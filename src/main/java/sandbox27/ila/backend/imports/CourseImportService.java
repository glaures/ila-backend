package sandbox27.ila.backend.imports;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.*;
import sandbox27.ila.backend.imports.CourseSheetParser.ParsedSheet;
import sandbox27.ila.backend.imports.CourseSheetParser.RawRow;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

/**
 * Validates and imports course planning sheets uploaded via the frontend. The same validation runs
 * for both the dry-run ({@link #validate}) and the actual import ({@link #commit}); a commit only
 * writes when the sheet is free of blocking errors.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseImportService {

    private final CourseSheetParser parser;
    private final PeriodRepository periodRepository;
    private final BlockRepository blockRepository;
    private final CourseRepository courseRepository;
    private final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    private final UserRepository userRepository;

    private static final Set<String> PAUSE_MARKERS = Set.of("hofpause", "mittagessen", "pause", "mittag");

    /** "VK" (Vorbereitungsklasse) has no numeric grade and is stored under this pseudo grade. */
    public static final int VK_GRADE = 99;

    /** Normalized category text (and short codes) -> enum. */
    private static final Map<String, CourseCategory> CATEGORY_BY_TEXT = Map.ofEntries(
            Map.entry("kreativität und praxis", CourseCategory.KuP),
            Map.entry("bewegung und entspannung", CourseCategory.BuE),
            Map.entry("fordern und fördern", CourseCategory.FuF),
            Map.entry("selbstorganisiertes lernen", CourseCategory.SOL),
            Map.entry("ila", CourseCategory.iLa),
            Map.entry("kup", CourseCategory.KuP),
            Map.entry("bue", CourseCategory.BuE),
            Map.entry("fuf", CourseCategory.FuF),
            Map.entry("sol", CourseCategory.SOL));

    private static final String CATEGORY_OPTIONS =
            "Kreativität und Praxis, Bewegung und Entspannung, Fordern und Fördern, Selbstorganisiertes Lernen, iLa";

    private static final Map<String, DayOfWeek> DAY_BY_TEXT = Map.ofEntries(
            Map.entry("montag", DayOfWeek.MONDAY), Map.entry("mo", DayOfWeek.MONDAY),
            Map.entry("dienstag", DayOfWeek.TUESDAY), Map.entry("di", DayOfWeek.TUESDAY),
            Map.entry("mittwoch", DayOfWeek.WEDNESDAY), Map.entry("mi", DayOfWeek.WEDNESDAY),
            Map.entry("donnerstag", DayOfWeek.THURSDAY), Map.entry("do", DayOfWeek.THURSDAY),
            Map.entry("freitag", DayOfWeek.FRIDAY), Map.entry("fr", DayOfWeek.FRIDAY),
            Map.entry("samstag", DayOfWeek.SATURDAY), Map.entry("sa", DayOfWeek.SATURDAY),
            Map.entry("sonntag", DayOfWeek.SUNDAY), Map.entry("so", DayOfWeek.SUNDAY));

    public CourseImportReport validate(MultipartFile file, Long periodId) {
        return process(file, periodId, false);
    }

    @Transactional
    public CourseImportReport commit(MultipartFile file, Long periodId) {
        return process(file, periodId, true);
    }

    /** Bundles a valid row with the entities the commit needs, so we don't resolve them twice. */
    private record RowContext(CourseImportRowDto dto, Block block, User instructor,
                              Set<CourseCategory> categories) {
    }

    private record InstructorName(String firstName, String lastName) {
    }

    private CourseImportReport process(MultipartFile file, Long periodId, boolean commit) {
        Period period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));

        CourseImportReport report = CourseImportReport.builder()
                .fileName(file.getOriginalFilename())
                .periodId(period.getId())
                .periodName(period.getName())
                .build();

        ParsedSheet sheet;
        try {
            sheet = parser.parse(file.getInputStream());
        } catch (CourseImportException e) {
            report.getGlobalIssues().add(ImportIssue.error("file", e.getMessage()));
            finalizeCounts(report);
            return report;
        } catch (IOException e) {
            report.getGlobalIssues().add(ImportIssue.error("file", "Die hochgeladene Datei konnte nicht gelesen werden."));
            finalizeCounts(report);
            return report;
        }
        report.setSheetName(sheet.sheetName());

        Map<BlockKey, Block> blocksByDayAndStart = loadBlocks(period);
        Set<String> seenCourseIds = new HashSet<>();
        LinkedHashMap<InstructorName, List<String>> missingInstructors = new LinkedHashMap<>();
        List<RowContext> committable = new ArrayList<>();

        for (RawRow raw : sheet.rows()) {
            if (isEmptyRow(raw) || isPauseRow(raw)) {
                // trailing/leftover blank rows and break rows are silently ignored, not errors
                report.setSkippedRows(report.getSkippedRows() + 1);
                continue;
            }
            RowContext ctx = validateRow(raw, period, blocksByDayAndStart, seenCourseIds, missingInstructors);
            report.getRows().add(ctx.dto());
            if (!ctx.dto().hasErrors()) {
                committable.add(ctx);
            }
        }

        report.getMissingInstructors().addAll(missingInstructors.entrySet().stream()
                .map(e -> new MissingInstructorDto(e.getKey().firstName(), e.getKey().lastName(), e.getValue()))
                .toList());

        if (report.getRows().isEmpty()) {
            report.getGlobalIssues().add(ImportIssue.warning("file", "Es wurden keine Kurszeilen gefunden."));
        }

        finalizeCounts(report);

        if (commit && report.isImportable()) {
            int created = 0;
            int updated = 0;
            for (RowContext ctx : committable) {
                boolean isNew = writeCourse(ctx, period);
                if (isNew) created++;
                else updated++;
            }
            report.setCommitted(true);
            report.setCreatedCount(created);
            report.setUpdatedCount(updated);
            log.info("Course import into period '{}': {} created, {} updated", period.getName(), created, updated);
        }
        return report;
    }

    // ---- validation ---------------------------------------------------------------------------

    private RowContext validateRow(RawRow raw, Period period, Map<BlockKey, Block> blocks,
                                   Set<String> seenCourseIds,
                                   LinkedHashMap<InstructorName, List<String>> missingInstructors) {
        CourseImportRowDto dto = CourseImportRowDto.builder().rowNumber(raw.rowNumber()).build();

        // course id
        String courseId = raw.get(CourseColumn.KURS_ID).trim();
        dto.setCourseId(courseId);
        if (courseId.isBlank()) {
            dto.addIssue(ImportIssue.error("courseId", "Kurs-ID fehlt."));
        } else if (!seenCourseIds.add(courseId)) {
            dto.addIssue(ImportIssue.error("courseId", "Kurs-ID \"" + courseId + "\" kommt mehrfach in der Datei vor."));
        }

        // name
        String name = raw.get(CourseColumn.TITEL).trim();
        dto.setName(name);
        if (name.isBlank()) {
            dto.addIssue(ImportIssue.error("name", "Titel fehlt."));
        }

        // description (+ optional Zielstellung appended)
        dto.setDescription(buildDescription(raw));

        // room
        dto.setRoom(raw.get(CourseColumn.RAUM).trim());

        // max attendees
        dto.setMaxAttendees(parseMaxAttendees(raw.get(CourseColumn.MAX_ATTENDEES), dto));

        // grades
        dto.setGrades(parseGrades(raw.get(CourseColumn.KLASSEN), dto));

        // categories
        Set<CourseCategory> categories = parseCategories(raw.get(CourseColumn.KATEGORIE), dto);
        dto.setCategories(categories.stream().map(Enum::name).toList());

        // block (weekday + time slot)
        Block block = resolveBlock(raw, period, blocks, dto);

        // instructor
        User instructor = resolveInstructor(raw, courseId, dto, missingInstructors);

        dto.setAction(dto.hasErrors() ? CourseImportRowDto.Action.SKIP
                : courseRepository.findByCourseIdAndPeriod(courseId, period).isPresent()
                ? CourseImportRowDto.Action.UPDATE : CourseImportRowDto.Action.CREATE);

        return new RowContext(dto, block, instructor, categories);
    }

    private String buildDescription(RawRow raw) {
        String beschreibung = raw.get(CourseColumn.BESCHREIBUNG).trim();
        String ziel = raw.get(CourseColumn.ZIELSTELLUNG).trim();
        if (ziel.isBlank()) return beschreibung;
        if (beschreibung.isBlank()) return ziel;
        return beschreibung + "\n\n" + ziel;
    }

    private Integer parseMaxAttendees(String raw, CourseImportRowDto dto) {
        String v = raw.trim();
        if (v.isBlank()) {
            dto.addIssue(ImportIssue.error("maxAttendees", "Maximale Teilnehmerzahl fehlt."));
            return null;
        }
        try {
            int n = Integer.parseInt(v.replaceAll("[^0-9-]", ""));
            if (n <= 0) {
                dto.addIssue(ImportIssue.error("maxAttendees", "Maximale Teilnehmerzahl muss größer als 0 sein (\"" + v + "\")."));
                return null;
            }
            return n;
        } catch (NumberFormatException e) {
            dto.addIssue(ImportIssue.error("maxAttendees", "Maximale Teilnehmerzahl ist keine Zahl (\"" + v + "\")."));
            return null;
        }
    }

    private List<Integer> parseGrades(String raw, CourseImportRowDto dto) {
        List<Integer> grades = new ArrayList<>();
        String v = raw.trim();
        if (v.isBlank()) {
            dto.addIssue(ImportIssue.warning("grades", "Keine Klassenstufen angegeben."));
            return grades;
        }
        for (String token : v.split("[,;/]")) {
            String t = token.trim();
            if (t.isBlank()) continue;
            if (t.equalsIgnoreCase("VK")) {
                grades.add(VK_GRADE);
            } else {
                try {
                    grades.add(Integer.parseInt(t));
                } catch (NumberFormatException e) {
                    dto.addIssue(ImportIssue.error("grades", "Ungültige Klassenstufe \"" + t + "\" (erwartet: Zahl oder \"VK\")."));
                }
            }
        }
        return grades;
    }

    private Set<CourseCategory> parseCategories(String raw, CourseImportRowDto dto) {
        Set<CourseCategory> categories = new LinkedHashSet<>();
        String v = raw.trim();
        if (v.isBlank()) {
            dto.addIssue(ImportIssue.warning("categories", "Keine Kategorie angegeben."));
            return categories;
        }
        for (String token : v.split("[,;]")) {
            String key = token.trim().toLowerCase().replaceAll("\\s+", " ");
            if (key.isBlank()) continue;
            CourseCategory cat = CATEGORY_BY_TEXT.get(key);
            if (cat == null) {
                dto.addIssue(ImportIssue.error("categories",
                        "Unbekannte Kategorie \"" + token.trim() + "\". Erlaubt: " + CATEGORY_OPTIONS + "."));
            } else {
                categories.add(cat);
            }
        }
        return categories;
    }

    private Block resolveBlock(RawRow raw, Period period, Map<BlockKey, Block> blocks, CourseImportRowDto dto) {
        String weekdayRaw = raw.get(CourseColumn.WOCHENTAG).trim();
        String timeRaw = raw.get(CourseColumn.ZEITSCHIENE).trim();
        dto.setWeekday(weekdayRaw);
        dto.setTimeSlot(timeRaw);

        DayOfWeek day = weekdayRaw.isBlank() ? null : DAY_BY_TEXT.get(weekdayRaw.toLowerCase());
        if (weekdayRaw.isBlank()) {
            dto.addIssue(ImportIssue.error("weekday", "Wochentag fehlt."));
        } else if (day == null) {
            dto.addIssue(ImportIssue.error("weekday", "Unbekannter Wochentag \"" + weekdayRaw + "\"."));
        }

        LocalTime start = parseTime(timeRaw);
        if (timeRaw.isBlank()) {
            dto.addIssue(ImportIssue.error("timeSlot", "Zeitschiene fehlt."));
        } else if (start == null) {
            dto.addIssue(ImportIssue.error("timeSlot", "Zeitschiene \"" + timeRaw + "\" ist keine gültige Uhrzeit."));
        }

        if (day == null || start == null) return null;

        Block block = blocks.get(new BlockKey(day, start));
        if (block == null) {
            dto.addIssue(ImportIssue.error("block",
                    "Kein Block für " + weekdayRaw + " " + start + " in Phase \"" + period.getName()
                            + "\". Bitte den passenden Block anlegen."));
        }
        return block;
    }

    private User resolveInstructor(RawRow raw, String courseId, CourseImportRowDto dto,
                                   LinkedHashMap<InstructorName, List<String>> missingInstructors) {
        String firstName = raw.get(CourseColumn.VORNAME).trim();
        String lastName = raw.get(CourseColumn.NACHNAME).trim();
        if (firstName.isBlank() && lastName.isBlank()) {
            // clean template only has a combined "Vorname Nachname" column
            String combined = raw.get(CourseColumn.KURSLEITER).trim();
            int lastSpace = combined.lastIndexOf(' ');
            if (lastSpace > 0) {
                firstName = combined.substring(0, lastSpace).trim();
                lastName = combined.substring(lastSpace + 1).trim();
            } else {
                lastName = combined;
            }
        }
        dto.setInstructorFirstName(firstName);
        dto.setInstructorLastName(lastName);

        if (firstName.isBlank() && lastName.isBlank()) {
            dto.addIssue(ImportIssue.error("instructor", "Kursleiter fehlt."));
            return null;
        }

        Optional<User> found = userRepository.findByFirstNameAndLastName(firstName, lastName);
        if (found.isPresent()) {
            dto.setInstructorUserName(found.get().getUserName());
            return found.get();
        }

        dto.addIssue(ImportIssue.error("instructor",
                "Kursleiter \"" + (firstName + " " + lastName).trim() + "\" ist nicht als Nutzer angelegt."));
        missingInstructors
                .computeIfAbsent(new InstructorName(firstName, lastName), k -> new ArrayList<>())
                .add(courseId.isBlank() ? "(ohne Kurs-ID)" : courseId);
        return null;
    }

    // ---- commit -------------------------------------------------------------------------------

    /** Writes one validated row; returns true if a new course was created, false if updated. */
    private boolean writeCourse(RowContext ctx, Period period) {
        CourseImportRowDto dto = ctx.dto();
        Optional<Course> existing = courseRepository.findByCourseIdAndPeriod(dto.getCourseId(), period);
        Course course = existing.orElseGet(Course::new);
        boolean isNew = existing.isEmpty();

        course.setCourseId(dto.getCourseId());
        course.setName(dto.getName());
        course.setDescription(dto.getDescription());
        course.setPeriod(period);
        course.setRoom(dto.getRoom());
        course.setMaxAttendees(dto.getMaxAttendees() == null ? 0 : dto.getMaxAttendees());
        if (isNew) {
            course.setMinAttendees(0);
        }
        if (course.getCourseCategories() == null) course.setCourseCategories(new HashSet<>());
        course.getCourseCategories().clear();
        course.getCourseCategories().addAll(ctx.categories());
        if (course.getGrades() == null) course.setGrades(new HashSet<>());
        course.getGrades().clear();
        course.getGrades().addAll(dto.getGrades());
        course.setInstructor(ctx.instructor());
        course = courseRepository.save(course);

        // upsert the block assignment: replace whatever the course had so re-planning is consistent
        List<CourseBlockAssignment> current = courseBlockAssignmentRepository.findAllByCourse(course);
        boolean alreadyCorrect = current.size() == 1
                && current.get(0).getBlock().getId() == ctx.block().getId();
        if (!alreadyCorrect) {
            courseBlockAssignmentRepository.deleteAll(current);
            courseBlockAssignmentRepository.save(
                    CourseBlockAssignment.builder().course(course).block(ctx.block()).build());
        }
        return isNew;
    }

    // ---- helpers ------------------------------------------------------------------------------

    private record BlockKey(DayOfWeek day, LocalTime start) {
    }

    private Map<BlockKey, Block> loadBlocks(Period period) {
        Map<BlockKey, Block> map = new HashMap<>();
        for (Block b : blockRepository.findByPeriod(period)) {
            // first block per (day, start) wins; duplicates are unexpected
            map.putIfAbsent(new BlockKey(b.getDayOfWeek(), b.getStartTime()), b);
        }
        return map;
    }

    private boolean isPauseRow(RawRow raw) {
        String name = raw.get(CourseColumn.TITEL).toLowerCase();
        return PAUSE_MARKERS.stream().anyMatch(name::contains);
    }

    /**
     * A leftover/blank row (e.g. trailing rows in the sheet that still carry a stray value in some
     * column) has neither a course id nor a title and is ignored rather than flagged as an error.
     */
    private boolean isEmptyRow(RawRow raw) {
        return raw.get(CourseColumn.KURS_ID).isBlank() && raw.get(CourseColumn.TITEL).isBlank();
    }

    private LocalTime parseTime(String raw) {
        String v = raw.trim();
        if (v.isBlank()) return null;
        try {
            return LocalTime.parse(v.length() == 5 ? v + ":00" : v);
        } catch (Exception e) {
            return null;
        }
    }

    private void finalizeCounts(CourseImportReport report) {
        int errors = report.getGlobalIssues().stream()
                .mapToInt(i -> i.severity() == ImportIssue.Severity.ERROR ? 1 : 0).sum();
        int warnings = report.getGlobalIssues().size() - errors;
        int importable = 0;
        for (CourseImportRowDto row : report.getRows()) {
            for (ImportIssue i : row.getIssues()) {
                if (i.severity() == ImportIssue.Severity.ERROR) errors++;
                else warnings++;
            }
            if (!row.hasErrors()) importable++;
        }
        report.setTotalRows(report.getRows().size());
        report.setImportableRows(importable);
        report.setErrorCount(errors);
        report.setWarningCount(warnings);
        boolean hasGlobalError = report.getGlobalIssues().stream()
                .anyMatch(i -> i.severity() == ImportIssue.Severity.ERROR);
        report.setImportable(!hasGlobalError && !report.getRows().isEmpty()
                && report.getRows().stream().noneMatch(CourseImportRowDto::hasErrors));
    }
}
