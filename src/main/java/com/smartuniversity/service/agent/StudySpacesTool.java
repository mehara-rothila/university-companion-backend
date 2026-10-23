package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartuniversity.model.StudySpace;
import com.smartuniversity.repository.StudySpaceRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Study spaces, least crowded first, using the crowd level students last reported. */
@Component
public class StudySpacesTool implements AssistantTool {

    static final int MAX_RESULTS = 8;

    private static final Set<String> QUIET_LEVELS = Set.of("silent", "quiet");
    private static final List<String> CROWD_ORDER = List.of("EMPTY", "MODERATE", "CROWDED");

    private final StudySpaceRepository studySpaceRepository;

    public StudySpacesTool(StudySpaceRepository studySpaceRepository) {
        this.studySpaceRepository = studySpaceRepository;
    }

    @Override
    public String name() {
        return "find_study_spaces";
    }

    @Override
    public String description() {
        return "Lists campus study spaces with their building, floor, room, capacity, usual noise level and how crowded "
                + "students last reported them, least crowded first. Use it when a student wants somewhere to study.";
    }

    @Override
    public Map<String, Object> parameters() {
        return ToolArgs.object(Map.of(
                "building", ToolArgs.text("Optional building name to search in."),
                "quiet_only", ToolArgs.flag("True to list only silent or quiet spaces.")));
    }

    @Override
    public Object run(JsonNode args, long userId) {
        Optional<String> building = ToolArgs.text(args, "building").filter(ToolArgs::isSearchable);
        boolean quietOnly = ToolArgs.flag(args, "quiet_only");

        List<StudySpace> spaces = studySpaceRepository.findAll().stream()
                .filter(space -> building.isEmpty() || ToolArgs.matchScore(building.get(), space.getBuilding(), space.getName()) > 0)
                .filter(space -> !quietOnly || QUIET_LEVELS.contains(lower(space.getDefaultNoiseLevel())))
                .sorted(Comparator.comparingInt(StudySpacesTool::crowdRank))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_found", spaces.size());
        result.put("spaces", spaces.stream().limit(MAX_RESULTS).map(StudySpacesTool::describe).toList());
        if (spaces.isEmpty()) {
            result.put("note", "No study spaces match.");
        }
        return result;
    }

    private static Map<String, Object> describe(StudySpace space) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", space.getName());
        ToolArgs.putIfPresent(details, "building", space.getBuilding());
        ToolArgs.putIfPresent(details, "floor", space.getFloor());
        ToolArgs.putIfPresent(details, "room", space.getRoom());
        ToolArgs.putIfPresent(details, "capacity", space.getCapacity());
        ToolArgs.putIfPresent(details, "noise_level", space.getDefaultNoiseLevel());
        ToolArgs.putIfPresent(details, "crowd", space.getStatus() == null ? null : lower(space.getStatus()));
        return details;
    }

    private static int crowdRank(StudySpace space) {
        int rank = CROWD_ORDER.indexOf(space.getStatus() == null ? "" : space.getStatus().toUpperCase(Locale.ROOT));
        return rank < 0 ? CROWD_ORDER.size() : rank;
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
