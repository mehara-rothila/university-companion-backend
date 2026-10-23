package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartuniversity.model.Event;
import com.smartuniversity.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Approved, visible campus events coming up soon, optionally narrowed by a keyword. */
@Component
public class UpcomingEventsTool implements AssistantTool {

    static final int MAX_RESULTS = 8;

    private final EventRepository eventRepository;
    private final Clock clock;

    @Autowired
    public UpcomingEventsTool(EventRepository eventRepository) {
        this(eventRepository, Clock.system(ZoneId.of("Asia/Colombo")));
    }

    UpcomingEventsTool(EventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "find_upcoming_events";
    }

    @Override
    public String description() {
        return "Finds approved campus events coming up soon: club meetups, workshops, talks, sports and cultural events. "
                + "Use it for questions about what's on, when or where an event is, or registration deadlines.";
    }

    @Override
    public Map<String, Object> parameters() {
        return ToolArgs.object(Map.of(
                "query", ToolArgs.text("Optional keyword to narrow the search, such as a topic, club, category or venue."),
                "days_ahead", ToolArgs.integer("How many days ahead to look, from 1 to 90. Defaults to 30.")));
    }

    @Override
    public Object run(JsonNode args, long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        int daysAhead = ToolArgs.number(args, "days_ahead", 30, 1, 90);
        LocalDateTime until = now.plusDays(daysAhead);
        Optional<String> query = ToolArgs.text(args, "query").filter(ToolArgs::isSearchable);

        List<Event> upcoming = eventRepository
                .findByStatusAndEventDateAfterOrderByEventDateAsc(Event.ApprovalStatus.APPROVED, now).stream()
                .filter(event -> !event.isHidden() && event.getEventDate().isBefore(until))
                .toList();
        List<Event> matches = query
                .map(q -> ToolArgs.bestMatches(upcoming, q, event -> new String[]{
                        event.getTitle(), event.getDescription(), event.getCategory(), event.getLocation(), event.getOrganizerName()}))
                .orElse(upcoming);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", ToolArgs.when(now));
        result.put("days_ahead", daysAhead);
        query.ifPresent(q -> result.put("query", q));
        result.put("total_found", matches.size());
        result.put("events", matches.stream().limit(MAX_RESULTS).map(UpcomingEventsTool::describe).toList());
        if (matches.isEmpty()) {
            result.put("note", "No approved events match in this period.");
        }
        return result;
    }

    private static Map<String, Object> describe(Event event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("title", event.getTitle());
        ToolArgs.putIfPresent(details, "category", event.getCategory());
        details.put("starts", ToolArgs.when(event.getEventDate()));
        ToolArgs.putIfPresent(details, "ends", ToolArgs.when(event.getEndTime()));
        ToolArgs.putIfPresent(details, "location", event.getLocation());
        ToolArgs.putIfPresent(details, "organizer", event.getOrganizerName());
        ToolArgs.putIfPresent(details, "registration_deadline", ToolArgs.when(event.getRegistrationDeadline()));
        ToolArgs.putIfPresent(details, "about", ToolArgs.clip(event.getDescription(), 200));
        return details;
    }
}
