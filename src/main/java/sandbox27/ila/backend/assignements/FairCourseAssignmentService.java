package sandbox27.ila.backend.assignements;
// Modellklassen und Vergabeservice mit Statistik und fairem Zweitdurchlauf

import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FairCourseAssignmentService {

    public AssignmentResult assign(BlockAssignmentRequest request) {
        Map<String, Map<Long, String>> result = new HashMap<>();
        Map<String, List<Integer>> preferenceLevels = new HashMap<>();

        Map<Long, List<BlockCourseDefinition>> coursesByBlock = request.getBlockCourses().stream()
                .collect(Collectors.groupingBy(BlockCourseDefinition::getBlockId));

        Map<Long, List<BlockPreference>> preferencesByBlock = request.getBlockPreferences().stream()
                .collect(Collectors.groupingBy(BlockPreference::getBlockId));

        for (Long blockId : preferencesByBlock.keySet()) {
            Map<String, String> blockAssignment = assignBlock(
                    preferencesByBlock.get(blockId),
                    coursesByBlock.getOrDefault(blockId, List.of()),
                    preferenceLevels);

            for (Map.Entry<String, String> entry : blockAssignment.entrySet()) {
                result.computeIfAbsent(entry.getKey(), k -> new HashMap<>()).put(blockId, entry.getValue());
            }
        }

        Map<String, Double> avgLevels = preferenceLevels.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().mapToInt(i -> i).average().orElse(0.0)));

        List<String> sortierteSchueler = new ArrayList<>(avgLevels.keySet());
        sortierteSchueler.sort(Comparator.comparingDouble(avgLevels::get).reversed());

        for (Long blockId : preferencesByBlock.keySet()) {
            Map<String, String> blockResult = result.entrySet().stream()
                    .filter(e -> e.getValue().containsKey(blockId))
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(blockId)));

            Map<String, BlockPreference> prefMap = preferencesByBlock.get(blockId).stream()
                    .collect(Collectors.toMap(BlockPreference::getStudentId, p -> p));

            Map<String, BlockCourseDefinition> courseMap = coursesByBlock.getOrDefault(blockId, List.of()).stream()
                    .collect(Collectors.toMap(BlockCourseDefinition::getCourseId, c -> c));

            for (String sid : sortierteSchueler) {
                if (!prefMap.containsKey(sid)) continue;
                BlockPreference pref = prefMap.get(sid);
                String currentCourse = blockResult.get(sid);
                int currentLevel = pref.getPreferences().indexOf(currentCourse);

                for (int i = 0; i < currentLevel; i++) {
                    String bessererKurs = pref.getPreferences().get(i);
                    BlockCourseDefinition kurs = courseMap.get(bessererKurs);
                    if (kurs == null || kurs.getAssignedStudents().size() >= kurs.getMax()) continue;

                    Optional<String> verdrängbar = kurs.getAssignedStudents().stream()
                            .filter(otherId -> {
                                double otherAvg = avgLevels.getOrDefault(otherId, 0.0);
                                return otherAvg < avgLevels.get(sid);
                            })
                            .findFirst();

                    if (verdrängbar.isPresent()) {
                        String loser = verdrängbar.get();
                        kurs.getAssignedStudents().remove(loser);
                        kurs.getAssignedStudents().add(sid);
                        courseMap.get(currentCourse).getAssignedStudents().remove(sid);
                        courseMap.get(currentCourse).getAssignedStudents().add(loser);

                        result.get(sid).put(blockId, bessererKurs);
                        result.get(loser).put(blockId, currentCourse);

                        log.info("Tausch in Block {}: {} ↔ {} ({} wird bevorzugt)", blockId, sid, loser, sid);
                        break;
                    }
                }
            }
        }

        Map<Integer, Long> verteilung = preferenceLevels.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(l -> l, Collectors.counting()));

        log.info("--- Präferenzverteilung ---");
        verteilung.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> log.info("Prio {}: {} Zuordnungen", e.getKey(), e.getValue()));

        return new AssignmentResult(result, avgLevels, verteilung);
    }

    private Map<String, String> assignBlock(List<BlockPreference> preferences, List<BlockCourseDefinition> courses,
                                            Map<String, List<Integer>> preferenceLevels) {
        Map<String, String> assigned = new HashMap<>();
        Map<String, BlockCourseDefinition> courseMap = new HashMap<>();
        for (BlockCourseDefinition course : courses) {
            courseMap.put(course.getCourseId(), course);
        }

        Set<String> unassigned = preferences.stream().map(BlockPreference::getStudentId).collect(Collectors.toSet());
        int maxPrio = preferences.stream().mapToInt(p -> p.getPreferences().size()).max().orElse(0);

        for (int prio = 0; prio < maxPrio; prio++) {
            Map<String, List<String>> bewerberProKurs = new HashMap<>();

            for (BlockPreference pref : preferences) {
                if (!unassigned.contains(pref.getStudentId())) continue;
                if (prio >= pref.getPreferences().size()) continue;
                String kursId = pref.getPreferences().get(prio);
                bewerberProKurs.computeIfAbsent(kursId, k -> new ArrayList<>()).add(pref.getStudentId());
            }

            for (Map.Entry<String, List<String>> entry : bewerberProKurs.entrySet()) {
                String kursId = entry.getKey();
                BlockCourseDefinition kurs = courseMap.get(kursId);
                if (kurs == null) continue;

                int freiePlätze = kurs.getMax() - kurs.getAssignedStudents().size();
                if (freiePlätze <= 0) continue;

                List<String> bewerber = entry.getValue();
                Collections.shuffle(bewerber);
                List<String> zugewiesen = bewerber.subList(0, Math.min(freiePlätze, bewerber.size()));

                kurs.getAssignedStudents().addAll(zugewiesen);
                for (String sid : zugewiesen) {
                    assigned.put(sid, kursId);
                    unassigned.remove(sid);
                    preferenceLevels.computeIfAbsent(sid, k -> new ArrayList<>()).add(prio + 1);
                }
            }
        }

        for (String sid : unassigned) {
            assigned.put(sid, "PAUSE");
            preferenceLevels.computeIfAbsent(sid, k -> new ArrayList<>()).add(maxPrio + 1);
            log.warn("{} konnte keinem Kurs im Block zugewiesen werden", sid);
        }

        return assigned;
    }
}
