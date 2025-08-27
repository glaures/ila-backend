package sandbox27.ila.backend.assignements.algorithm2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.assignements.algorithm2.dtos.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AssignmentAlgorithmServiceV15
 *
 * Änderungen ggü. V14:
 *  - Schritt 6 (Auffüllen auf genau 3 Kurse) berücksichtigt jetzt die Klassenstufe:
 *    Ein Kurs wird nur gewählt, wenn der/die Schüler:in in course.allowedGrades enthalten ist
 *    (falls Liste leer/null: keine Einschränkung).
 *
 * Schrittfolge:
 *  0) validateAndFixThreeBlocks(...) auf Input anwenden
 *  1) Vorbelegte Kurse → Block sperren
 *  2) Pausen (prefIndex < 0 oder courseId=="PAUSE") → Block sperren
 *  3) Placeholder-Kurse mit prefIndex==0 GARANTIERT zuteilen → Block sperren; übrige Placeholder-Prefs entfernen
 *  4) Rangweise Zuteilung r=0,1,2,... (BLOCKWEISE, zufällig):
 *     - Kandidaten je Block (currentRank==r) zufällig zuteilen (mit Kapazität)
 *     - je Zuteilung: hartes Block-Pruning
 *     - NACH der Rangrunde: Demote übrige Prefs (currentRank==r) der in r erfolgreichen Nutzer um +1
 *  5) Nicht zugeteilte Präferenzen loggen
 *  6) Auffüllen auf GENAU 3 Kurse/Schüler (pro Tag max. 1 Kurs; Pause zählt nicht; erster Block bevorzugt)
 *     - **NEU:** nur Kurse, die zur Klassenstufe passen (allowedGrades)
 *  7) Abschluss-Checks & Statistiken
 *
 * CourseUserAssignmentDTO.preferenceIndex:
 *  - predefined & pause: 0
 *  - placeholder Rang 0: 0
 *  - normale Vergabe: ORIGINAL-rank der Präferenz (nicht currentRank!)
 *  - Auffüllen (ohne Präferenz): 999
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentAlgorithmService {

    public static final String PAUSE_COURSE_NAME = "__PAUSE__";
    public static final String PAUSE_COURSE_ID_MARKER = "PAUSE"; // optionaler Marker

    /** String-Seed für deterministische Zufälligkeit. */
    public static final long RANDOM_SEED = 96533564564456123L;

    /** Zielanzahl an Kurs-Belegungen pro Schüler über die Woche. */
    private static final int TARGET_COURSE_ASSIGNMENTS_PER_STUDENT = 3;

    /** Marker-Index für Auto-Fill-Zuweisungen (Schritt 6). */
    private static final int PREF_INDEX_AUTO_FILL = 999;

    public List<CourseUserAssignmentDTO> assignCourses(final AssignmentInputDTO inputRaw) {
        // 0) Validieren + ggf. fixen (arbeitet in-place am DTO)
        final AssignmentInputDTO input = validateAndFixThreeBlocks(inputRaw);

        final List<CourseUserAssignmentDTO> result = new ArrayList<>();

        // --- Stammdaten & Indizes ---
        final Map<String, CourseDTO> courseById = safeList(input.getCourses()).stream()
                .collect(Collectors.toMap(CourseDTO::getId, c -> c, (a, b) -> a, LinkedHashMap::new));

        final Map<Long, BlockDTO> blockById = safeList(input.getBlocks()).stream()
                .collect(Collectors.toMap(BlockDTO::getId, b -> b, (a, b) -> a, LinkedHashMap::new));

        // Block-Reihenfolge über Woche (Tag, dann Startzeit)
        final List<BlockDTO> blockOrder = new ArrayList<>(blockById.values());
        blockOrder.sort(Comparator
                .comparing(BlockDTO::getDayOfWeek, Comparator.comparingInt(AssignmentAlgorithmService::dayIndex))
                .thenComparing(BlockDTO::getStartTime));
        final List<Long> blockIdsInOrder = blockOrder.stream().map(BlockDTO::getId).collect(Collectors.toList());

        // Kurse pro Block
        final Map<Long, List<CourseDTO>> coursesByBlock = new HashMap<>();
        for (CourseDTO c : safeList(input.getCourses())) {
            coursesByBlock.computeIfAbsent(c.getBlockId(), k -> new ArrayList<>()).add(c);
        }

        // Blöcke pro Tag (für Auffüllen)
        final Map<String, List<BlockDTO>> blocksByDay = blockOrder.stream()
                .collect(Collectors.groupingBy(BlockDTO::getDayOfWeek, LinkedHashMap::new, Collectors.toList()));

        // Mutable Arbeitskopien der Präferenzen
        final List<PrefWork> workPrefs = new ArrayList<>();
        for (PreferenceDTO p : safeList(input.getPreferences())) {
            workPrefs.add(PrefWork.from(p));
        }

        // Tracking
        final Map<String, Integer> occupancyByCourse = new HashMap<>(); // courseId -> count
        final Set<String> assignedUserBlock = new HashSet<>();          // "user#block"
        final Set<String> winningPrefKeys = new HashSet<>();            // zugeteilte Prefs (für Logging)
        final Map<String, Integer> firstChoiceHitsPerUser = new HashMap<>(); // Statistik

        // === (1) Vorbelegte Zuweisungen ===
        for (CourseUserAssignmentDTO a : safeList(input.getPredefinedAssignments())) {
            if (a == null) continue;
            final String ubKey = userBlockKey(a.getUserName(), a.getBlockId());
            if (assignedUserBlock.add(ubKey)) {
                // normiere preferenceIndex = 0
                CourseUserAssignmentDTO normalized = new CourseUserAssignmentDTO(
                        a.getUserName(), a.getCourseId(), a.getCourseName(), a.getBlockId(),
                        a.isPredefined(), a.isPause(), 0
                );
                result.add(normalized);
                if (!normalized.isPause() && normalized.getCourseId() != null) {
                    inc(occupancyByCourse, normalized.getCourseId(), 1);
                }
                pruneUserBlockPrefs(workPrefs, normalized.getUserName(), normalized.getBlockId());
            } else {
                log.warn("Vorbelegung doppelt ignoriert für {} (blockId={}): {}", a.getUserName(), a.getBlockId(), a);
            }
        }
        log.info("Vorbelegungen übernommen: {}", result.stream().filter(CourseUserAssignmentDTO::isPredefined).count());

        // === (2) Pausen (sofort) ===
        for (PrefWork pw : new ArrayList<>(workPrefs)) {
            if (!isPausePref(pw)) continue;
            final String ubKey = userBlockKey(pw.userName, pw.blockId);
            // if (assignedUserBlock.contains(ubKey)) continue;

            CourseUserAssignmentDTO pause = new CourseUserAssignmentDTO(
                    pw.userName, null, PAUSE_COURSE_NAME, pw.blockId, false, true, 0
            );
            result.add(pause);
            // assignedUserBlock.add(ubKey);
            winningPrefKeys.add(pw.key());
            pruneUserBlockPrefs(workPrefs, pw.userName, pw.blockId);
        }

        // === (3) Placeholder-0 GARANTIERT ===
        for (PrefWork pw : new ArrayList<>(workPrefs)) {
            if (pw.originalRank != 0) continue;
            CourseDTO course = courseById.get(pw.courseId);
            if (course == null || !belongsToBlock(course, pw.blockId) || !isPlaceholder(course)) continue;

            final String ubKey = userBlockKey(pw.userName, pw.blockId);
            if (assignedUserBlock.contains(ubKey)) continue;

            CourseUserAssignmentDTO assignment = new CourseUserAssignmentDTO(
                    pw.userName, pw.courseId, course.getName(), pw.blockId, false, false, 0
            );
            result.add(assignment);
            assignedUserBlock.add(ubKey);
            inc(occupancyByCourse, pw.courseId, 1); // Kapazität ignoriert, Zählung konsistent
            winningPrefKeys.add(pw.key());
            firstChoiceHitsPerUser.merge(pw.userName, 1, Integer::sum);

            pruneUserBlockPrefs(workPrefs, pw.userName, pw.blockId, pw.key());
        }
        // Übrige Placeholder-Prefs (≠ Rang 0) entfernen
        workPrefs.removeIf(pw -> {
            CourseDTO c = courseById.get(pw.courseId);
            return c != null && isPlaceholder(c) && pw.originalRank != 0;
        });

        // --- Ab hier nur noch „echte“ Kurs-Präferenzen ---
        final List<PrefWork> activePrefs = new ArrayList<>(workPrefs);

        // === (4) Rangweise, BLOCKWEISE Vergabe; Demotion NACH Rangrunde ===
        final Random rnd = rng();
        int currentRank = 0;

        while (true) {
            final int rankF = currentRank; // für Lambdas/Streams

            // Gibt es noch jemanden auf diesem Rang?
            boolean anyAtRank = activePrefs.stream()
                    .anyMatch(pw -> pw.currentRank == rankF
                            && !winningPrefKeys.contains(pw.key())
                            && !assignedUserBlock.contains(userBlockKey(pw.userName, pw.blockId)));
            if (!anyAtRank) {
                OptionalInt maxRemaining = activePrefs.stream()
                        .filter(pw -> !winningPrefKeys.contains(pw.key()))
                        .filter(pw -> !assignedUserBlock.contains(userBlockKey(pw.userName, pw.blockId)))
                        .mapToInt(pw -> pw.currentRank)
                        .max();
                if (maxRemaining.isEmpty() || currentRank > maxRemaining.getAsInt()) break;
                currentRank++;
                continue;
            }

            // Nutzer, die in DIESEM Rang (irgendeinem Block) erfolgreich waren
            final Set<String> usersSucceededThisRank = new HashSet<>();

            // Blockweise durchgehen (fixe Reihenfolge)
            for (Long blockId : blockIdsInOrder) {
                final List<PrefWork> candidates = activePrefs.stream()
                        .filter(pw -> pw.blockId.equals(blockId))
                        .filter(pw -> pw.currentRank == rankF)
                        .filter(pw -> !winningPrefKeys.contains(pw.key()))
                        .filter(pw -> !assignedUserBlock.contains(userBlockKey(pw.userName, pw.blockId)))
                        .collect(Collectors.toCollection(ArrayList::new));

                if (candidates.isEmpty()) continue;

                Collections.shuffle(candidates, rnd);

                for (PrefWork pw : candidates) {
                    final String ubKey = userBlockKey(pw.userName, pw.blockId);
                    if (assignedUserBlock.contains(ubKey)) continue;

                    final CourseDTO course = courseById.get(pw.courseId);
                    if (course == null || !belongsToBlock(course, pw.blockId)) continue;

                    final int occ = occupancyByCourse.getOrDefault(pw.courseId, 0);
                    final int cap = course.getMaxAttendees() > 0 ? course.getMaxAttendees() : Integer.MAX_VALUE;
                    if (occ >= cap) continue;

                    // Zuweisen (preferenceIndex = ORIGINAL-rank)
                    CourseUserAssignmentDTO assignment = new CourseUserAssignmentDTO(
                            pw.userName, pw.courseId, course.getName(), pw.blockId, false, false, pw.originalRank
                    );
                    result.add(assignment);
                    assignedUserBlock.add(ubKey);
                    occupancyByCourse.put(pw.courseId, occ + 1);
                    winningPrefKeys.add(pw.key());
                    if (pw.originalRank == 0) firstChoiceHitsPerUser.merge(pw.userName, 1, Integer::sum);

                    pruneUserBlockPrefs(activePrefs, pw.userName, pw.blockId, pw.key());
                    pruneUserBlockPrefs(workPrefs,  pw.userName, pw.blockId, pw.key());

                    usersSucceededThisRank.add(pw.userName);
                }
            }

            // Demotion NACH der Rangrunde (für alle anderen Blöcke dieser Nutzer)
            if (!usersSucceededThisRank.isEmpty()) {
                for (PrefWork other : activePrefs) {
                    if (!usersSucceededThisRank.contains(other.userName)) continue;
                    if (other.currentRank == rankF) {
                        other.currentRank = other.currentRank + 1;
                    }
                }
            }

            currentRank++;
        }

        // === (5) Nicht zugeteilte Präferenzen loggen ===
        final List<PrefWork> losers = workPrefs.stream()
                .filter(pw -> !winningPrefKeys.contains(pw.key()))
                .collect(Collectors.toList());
        if (!losers.isEmpty()) {
            Map<String, List<String>> byUser = new LinkedHashMap<>();
            for (PrefWork pw : losers) {
                final CourseDTO c = courseById.get(pw.courseId);
                final boolean placeholder = c != null && isPlaceholder(c);
                final String courseLabel = isPausePref(pw) ? "PAUSE" : (placeholder ? ("PLH:" + pw.courseId) : pw.courseId);
                byUser.computeIfAbsent(pw.userName, k -> new ArrayList<>())
                        .add(pw.blockId + ":" + courseLabel + "(rank=" + pw.originalRank + ")");
            }
            byUser.forEach((u, list) -> log.info("Nicht zugeteilt – {} -> {}", u, String.join(", ", list)));
            log.info("Anzahl unzugeteilter Präferenzen: {}", losers.size());
        } else {
            log.info("Alle relevanten Präferenzen erhielten eine Zuweisung (inkl. PAUSE/Vorbelegungen).");
        }

        // === (6) Auffüllen auf GENAU 3 Kurse/Schüler (tagesweise, erster Block bevorzugt) – MIT GRADE CHECK ===

        // Indexe: Tage je Block, Kursbelegungen je User&Tag
        final Map<String, Set<String>> courseDaysByUser = new HashMap<>();
        final Map<String, Integer> courseCountByUser = new HashMap<>();
        for (CourseUserAssignmentDTO a : result) {
            if (a.isPause()) continue;
            final BlockDTO b = blockById.get(a.getBlockId());
            if (b == null) continue;
            courseDaysByUser.computeIfAbsent(a.getUserName(), k -> new HashSet<>()).add(b.getDayOfWeek());
            courseCountByUser.merge(a.getUserName(), 1, Integer::sum);
        }

        // Nutzer → Klassenstufe
        final Map<String, String> gradeByUser = safeList(input.getUsers()).stream()
                .collect(Collectors.toMap(UserDTO::getUserName, UserDTO::getGrade, (a, b) -> a, LinkedHashMap::new));

        final Set<String> allUsers = safeList(input.getUsers()).stream()
                .map(UserDTO::getUserName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allUsers.isEmpty()) {
            for (PrefWork pw : workPrefs) allUsers.add(pw.userName);
        }

        final List<String> dayOrder = new ArrayList<>(blocksByDay.keySet());
        dayOrder.sort(Comparator.comparingInt(AssignmentAlgorithmService::dayIndex));

        for (String userName : allUsers) {
            int currentCourseCount = courseCountByUser.getOrDefault(userName, 0);
            final String userGrade = normalize(gradeByUser.get(userName));

            while (currentCourseCount < TARGET_COURSE_ASSIGNMENTS_PER_STUDENT) {
                boolean assignedSomething = false;

                for (String day : dayOrder) {
                    if (courseDaysByUser.getOrDefault(userName, Collections.emptySet()).contains(day)) continue;

                    final List<BlockDTO> blocksOfDay = blocksByDay.getOrDefault(day, Collections.emptyList());
                    if (blocksOfDay.isEmpty()) continue;

                    // Blöcke sind bereits in Tagesreihenfolge (erster Block bevorzugt)
                    for (BlockDTO targetBlock : blocksOfDay) {
                        final String ubKey = userBlockKey(userName, targetBlock.getId());
                        if (assignedUserBlock.contains(ubKey)) continue; // schon Pause/Kurs in diesem Block

                        // Greedy: Kurs mit größter Restkapazität, der zur Klassenstufe passt
                        final List<CourseDTO> blockCourses = coursesByBlock.getOrDefault(targetBlock.getId(), Collections.emptyList());
                        CourseDTO picked = null;
                        int bestRest = -1;
                        for (CourseDTO c : blockCourses) {
                            if (!isAllowedForGrade(c, userGrade) || isPlaceholder(c)) continue; // <<< NEU: Grade-Check
                            final int cap = c.getMaxAttendees() > 0 ? c.getMaxAttendees() : Integer.MAX_VALUE;
                            final int occ = occupancyByCourse.getOrDefault(c.getId(), 0);
                            final int rest = cap == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, cap - occ);
                            if (rest <= 0) continue;
                            if (rest > bestRest) { bestRest = rest; picked = c; }
                        }

                        if (picked != null) {
                            CourseUserAssignmentDTO a = new CourseUserAssignmentDTO(
                                    userName, picked.getId(), picked.getName(), targetBlock.getId(), false, false, PREF_INDEX_AUTO_FILL
                            );
                            result.add(a);
                            assignedUserBlock.add(ubKey);
                            inc(occupancyByCourse, picked.getId(), 1);

                            courseDaysByUser.computeIfAbsent(userName, k -> new HashSet<>()).add(day);
                            currentCourseCount++;
                            courseCountByUser.put(userName, currentCourseCount);

                            assignedSomething = true;
                            break;
                        }
                    }
                    if (assignedSomething) break;
                }

                if (!assignedSomething) {
                    log.warn("Auffüllen nicht vollständig möglich für {} – hat {} / {} Kurs-Belegungen (ggf. wegen allowedGrades).",
                            userName, currentCourseCount, TARGET_COURSE_ASSIGNMENTS_PER_STUDENT);
                    break;
                }
            }
        }

        // === (7) Abschluss-Checks ===
        Map<Integer, Long> dist = courseCountByUser.values().stream()
                .collect(Collectors.groupingBy(i -> i, TreeMap::new, Collectors.counting()));
        log.info("Verteilung Kurs-Belegungen pro Schüler (nur Kurse, ohne Pausen): {}", dist);

        for (String userName : allUsers) {
            int count = courseCountByUser.getOrDefault(userName, 0);
            if (count == TARGET_COURSE_ASSIGNMENTS_PER_STUDENT) {
                log.info("OK: {} hat genau {} Kurs-Belegungen.", userName, TARGET_COURSE_ASSIGNMENTS_PER_STUDENT);
            } else if (count < TARGET_COURSE_ASSIGNMENTS_PER_STUDENT) {
                log.warn("ZU WENIG: {} hat nur {} von {} Kurs-Belegungen.", userName, count, TARGET_COURSE_ASSIGNMENTS_PER_STUDENT);
            } else {
                log.warn("ZU VIELE: {} hat {} (Soll {}).", userName, count, TARGET_COURSE_ASSIGNMENTS_PER_STUDENT);
            }
        }

        Map<Integer, Long> histogram = firstChoiceHitsPerUser.values().stream()
                .collect(Collectors.groupingBy(i -> i, Collectors.counting()));
        log.info("Zuweisungen gemäß 1. Präferenz (über alle Blöcke): {}", histogram);

        return result;
    }

    // ------------------ Öffentliche Helper ------------------

    /** Public, damit auch außerhalb nutzbar (deterministische Zufälligkeit über String-Seed). */
    public static Random rng() {
        return new Random(RANDOM_SEED);
    }

    /** Public, generische Null-Absicherung: niemals null zurückgeben. */
    public static <T> List<T> safeList(List<T> in) {
        return in == null ? Collections.emptyList() : in;
    }

    // ------------------ Validator / Fixer ------------------

    public AssignmentInputDTO validateAndFixThreeBlocks(final AssignmentInputDTO input) {
        if (input == null) return null;

        final Random rnd = rng();
        final List<PreferenceDTO> prefs = new ArrayList<>(safeList(input.getPreferences()));

        // Vorbelegte KURS-Blöcke pro User (Pausen ignorieren)
        final Map<String, Set<Long>> predefBlocksByUser = new HashMap<>();
        for (CourseUserAssignmentDTO a : safeList(input.getPredefinedAssignments())) {
            if (a == null) continue;
            if (a.isPause()) continue;
            if (a.getCourseId() == null) continue;
            predefBlocksByUser.computeIfAbsent(a.getUserName(), k -> new LinkedHashSet<>()).add(a.getBlockId());
        }

        // Präferenz-Blöcke pro User (nur KURS-Präferenzen, Pausen ignorieren)
        final Map<String, Set<Long>> prefBlocksByUser = new HashMap<>();
        for (PreferenceDTO p : prefs) {
            if (p == null) continue;
            if (isPausePreference(p)) continue;
            prefBlocksByUser.computeIfAbsent(p.getUserName(), k -> new LinkedHashSet<>()).add(p.getBlockId());
        }

        // Alle relevanten User (aus Users, Prefs, Vorbelegungen)
        final Set<String> allUsers = new LinkedHashSet<>();
        for (UserDTO u : safeList(input.getUsers())) allUsers.add(u.getUserName());
        allUsers.addAll(predefBlocksByUser.keySet());
        allUsers.addAll(prefBlocksByUser.keySet());

        for (String user : allUsers) {
            final Set<Long> predef = new LinkedHashSet<>(predefBlocksByUser.getOrDefault(user, Collections.emptySet()));
            final Set<Long> pref   = new LinkedHashSet<>(prefBlocksByUser.getOrDefault(user, Collections.emptySet()));

            final Set<Long> combined = new LinkedHashSet<>(predef);
            combined.addAll(pref);

            final int size = combined.size();
            if (size == 3) continue;

            if (size < 3) {
                log.warn("validateAndFixThreeBlocks: {} hat nur {} Blöcke (vorbelegt={}, prefs={}) – keine automatische Ergänzung.",
                        user, size, predef.size(), pref.size());
                continue;
            }

            // size > 3: reduzieren
            final int toRemove = size - 3;

            // Nur Präferenz-Blöcke dürfen entfernt werden (Vorbelegungen sind fix)
            final List<Long> removablePrefBlocks = new ArrayList<>(pref);
            removablePrefBlocks.removeAll(predef); // schütze vorbelegte Blöcke

            if (predef.size() > 3) {
                // Vorbelegungen belegen schon >3 Blöcke – kann hier nicht fixen
                log.warn("validateAndFixThreeBlocks: {} hat bereits {} vorbelegte Kurs-Blöcke (>3). Präferenzen werden nicht verändert.",
                        user, predef.size());
                continue;
            }

            if (removablePrefBlocks.isEmpty()) {
                continue; // nichts zu entfernen
            }

            Collections.shuffle(removablePrefBlocks, rnd);
            final List<Long> blocksToDrop = removablePrefBlocks.subList(0, Math.min(toRemove, removablePrefBlocks.size()));

            // Entferne alle Präferenzen des Users in diesen Blöcken
            int before = prefs.size();
            prefs.removeIf(p -> user.equals(p.getUserName()) && blocksToDrop.contains(p.getBlockId()));
            int removed = before - prefs.size();

            log.info("validateAndFixThreeBlocks: {} hatte {} Blöcke, entferne {} Präferenz-Block/Blöcke {} ({} Präferenzen gelöscht), jetzt 3.",
                    user, size, blocksToDrop.size(), blocksToDrop, removed);
        }

        // Schreibe gefixte Präferenzen zurück
        try {
            input.setPreferences(prefs);
        } catch (Exception e) {
            log.warn("validateAndFixThreeBlocks: Konnte Preferences nicht zurückschreiben (kein Setter?). Änderungen nur lokal wirksam.");
        }

        return input;
    }

    // ------------------ Private Helpers ------------------

    private static boolean isPausePref(PrefWork pw) {
        return pw.originalRank < 0 || (pw.courseId != null && PAUSE_COURSE_ID_MARKER.equalsIgnoreCase(pw.courseId));
    }

    /** Kurs-Pause-Präferenz? (negativer Index ODER courseId == 'PAUSE') */
    private static boolean isPausePreference(PreferenceDTO p) {
        if (p == null) return false;
        if (p.getPreferenceIndex() < 0) return true;
        final String cid = p.getCourseId();
        return cid != null && PAUSE_COURSE_ID_MARKER.equalsIgnoreCase(cid);
    }

    private static boolean belongsToBlock(CourseDTO c, Long blockId) {
        return Objects.equals(c.getBlockId(), blockId);
    }

    private static boolean isPlaceholder(CourseDTO c) {
        try {
            return (boolean) CourseDTO.class.getMethod("isPlaceholder").invoke(c);
        } catch (Exception ignore) {
            try {
                return (boolean) CourseDTO.class.getMethod("getPlaceholder").invoke(c);
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** true, wenn der Kurs für die gegebene Klassenstufe erlaubt ist (allowedGrades leer/null ⇒ offen). */
    private static boolean isAllowedForGrade(CourseDTO c, String userGradeNorm) {
        Set<String> allowed = c.getAllowedGrades();
        if (allowed == null || allowed.isEmpty()) return true;    // keine Einschränkung
        if (userGradeNorm == null || userGradeNorm.isEmpty()) {
            // Keine bekannte Klassenstufe → nur Kurse ohne Einschränkung wären erlaubt; hier: restriktiv
            return false;
        }
        for (String g : allowed) {
            if (userGradeNorm.equals(normalize(g))) return true;
        }
        return false;
    }

    private static String normalize(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String userBlockKey(String userName, Long blockId) {
        return userName + "#" + blockId;
    }

    private static int dayIndex(String dayOfWeek) {
        if (dayOfWeek == null) return 999;
        switch (dayOfWeek.toUpperCase(Locale.ROOT)) {
            case "MONDAY":    return 1;
            case "TUESDAY":   return 2;
            case "WEDNESDAY": return 3;
            case "THURSDAY":  return 4;
            case "FRIDAY":    return 5;
            case "SATURDAY":  return 6;
            case "SUNDAY":    return 7;
            default:          return 999;
        }
    }

    /** Entfernt ALLE Prefs eines (user, block) aus der Liste (z. B. nach Vorbelegung oder Pause). */
    private static void pruneUserBlockPrefs(List<PrefWork> list, String userName, Long blockId) {
        list.removeIf(pw -> pw.userName.equals(userName) && Objects.equals(pw.blockId, blockId));
    }

    /**
     * Entfernt ALLE Prefs eines (user, block) außer der mit keepKey aus der Liste.
     * Nutze diese Variante, wenn gerade eine Präferenz aus diesem Block zugeteilt wurde.
     */
    private static void pruneUserBlockPrefs(List<PrefWork> list, String userName, Long blockId, String keepKey) {
        list.removeIf(pw -> pw.userName.equals(userName)
                && Objects.equals(pw.blockId, blockId)
                && !pw.key().equals(keepKey));
    }

    private static void inc(Map<String, Integer> map, String key, int delta) {
        map.put(key, map.getOrDefault(key, 0) + delta);
    }

    /** Mutable Arbeitsrepräsentation der Präferenzen */
    private static final class PrefWork {
        final String userName;
        final Long blockId;
        final String courseId;
        final int originalRank;
        int currentRank;

        private PrefWork(String userName, Long blockId, String courseId, int rank) {
            this.userName = userName;
            this.blockId = blockId;
            this.courseId = courseId;
            this.originalRank = rank;
            this.currentRank = rank;
        }
        static PrefWork from(PreferenceDTO p) {
            return new PrefWork(p.getUserName(), p.getBlockId(), p.getCourseId(), p.getPreferenceIndex());
        }
        String key() {
            return userName + "#" + blockId + "#" + courseId + "#" + originalRank;
        }
    }
}
