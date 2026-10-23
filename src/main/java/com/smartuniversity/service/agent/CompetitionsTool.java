package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartuniversity.model.Competition;
import com.smartuniversity.repository.CompetitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Approved, visible competitions that haven't ended, optionally narrowed by a keyword. */
@Component
public class CompetitionsTool implements AssistantTool {

    static final int MAX_RESULTS = 8;

    private final CompetitionRepository competitionRepository;
    private final Clock clock;

    @Autowired
    public CompetitionsTool(CompetitionRepository competitionRepository) {
        this(competitionRepository, Clock.system(ZoneId.of("Asia/Colombo")));
    }

    CompetitionsTool(CompetitionRepository competitionRepository, Clock clock) {
        this.competitionRepository = competitionRepository;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "find_competitions";
    }

    @Override
    public String description() {
        return "Finds approved competitions, hackathons and contests that haven't ended yet, with their dates, "
                + "registration deadlines, prizes and how to enter.";
    }

    @Override
    public Map<String, Object> parameters() {
        return ToolArgs.object(Map.of(
                "query", ToolArgs.text("Optional keyword to narrow the search, such as a topic, category or venue.")));
    }

    @Override
    public Object run(JsonNode args, long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<String> query = ToolArgs.text(args, "query").filter(ToolArgs::isSearchable);

        List<Competition> open = competitionRepository
                .findByStatusAndEndDateAfterOrderByStartDateAsc(Competition.ApprovalStatus.APPROVED, now).stream()
                .filter(competition -> !competition.isHidden())
                .toList();
        List<Competition> matches = query
                .map(q -> ToolArgs.bestMatches(open, q, competition -> new String[]{
                        competition.getTitle(), competition.getDescription(), competition.getCategory(), competition.getLocation()}))
                .orElse(open);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", ToolArgs.when(now));
        query.ifPresent(q -> result.put("query", q));
        result.put("total_found", matches.size());
        result.put("competitions", matches.stream().limit(MAX_RESULTS).map(CompetitionsTool::describe).toList());
        if (matches.isEmpty()) {
            result.put("note", "No open competitions match.");
        }
        return result;
    }

    private static Map<String, Object> describe(Competition competition) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("title", competition.getTitle());
        ToolArgs.putIfPresent(details, "category", competition.getCategory());
        ToolArgs.putIfPresent(details, "starts", ToolArgs.when(competition.getStartDate()));
        ToolArgs.putIfPresent(details, "ends", ToolArgs.when(competition.getEndDate()));
        ToolArgs.putIfPresent(details, "registration_deadline", ToolArgs.when(competition.getRegistrationDeadline()));
        ToolArgs.putIfPresent(details, "location", competition.getLocation());
        ToolArgs.putIfPresent(details, "prizes", ToolArgs.clip(competition.getPrizes(), 150));
        if (competition.isInternalEnrollmentEnabled()) {
            details.put("how_to_enter", "Enrol on the competition's page in Athena.");
        } else if (competition.getExternalEnrollmentUrl() != null && !competition.getExternalEnrollmentUrl().isBlank()) {
            details.put("how_to_enter", "Register at " + competition.getExternalEnrollmentUrl());
        }
        ToolArgs.putIfPresent(details, "about", ToolArgs.clip(competition.getDescription(), 200));
        return details;
    }
}
