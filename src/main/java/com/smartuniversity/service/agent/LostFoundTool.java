package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartuniversity.model.LostFoundItem;
import com.smartuniversity.repository.LostFoundItemRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Active Lost &amp; Found posts. Results never say who posted an item or how to reach them: the
 * student contacts the poster through the item's page, as the Lost &amp; Found screen already works.
 */
@Component
public class LostFoundTool implements AssistantTool {

    static final int MAX_RESULTS = 8;

    private final LostFoundItemRepository lostFoundItemRepository;

    public LostFoundTool(LostFoundItemRepository lostFoundItemRepository) {
        this.lostFoundItemRepository = lostFoundItemRepository;
    }

    @Override
    public String name() {
        return "search_lost_and_found";
    }

    @Override
    public String description() {
        return "Searches active Lost & Found posts. FOUND posts are things someone has found, so check these when a "
                + "student has lost something. LOST posts are things someone is looking for. Results don't include "
                + "who posted an item; the student contacts them from the item's page in Athena's Lost & Found.";
    }

    @Override
    public Map<String, Object> parameters() {
        return ToolArgs.object(Map.of(
                "query", ToolArgs.text("What the item is, such as 'black wallet' or 'student ID'. Leave out to list the newest posts."),
                "type", ToolArgs.oneOf("FOUND or LOST. Leave out to search both.", "FOUND", "LOST")));
    }

    @Override
    public Object run(JsonNode args, long userId) {
        Optional<String> query = ToolArgs.text(args, "query").filter(ToolArgs::isSearchable);
        Optional<LostFoundItem.ItemType> type = ToolArgs.choice(args, "type", LostFoundItem.ItemType.class);

        List<LostFoundItem> active = lostFoundItemRepository.findActiveItemsOrderByDate(LostFoundItem.ItemStatus.ACTIVE).stream()
                .filter(item -> type.isEmpty() || item.getType() == type.get())
                .toList();
        List<LostFoundItem> matches = query
                .map(q -> ToolArgs.bestMatches(active, q, item -> new String[]{
                        item.getTitle(), item.getDescription(), item.getCategory(), item.getLocation(),
                        item.getTags() == null ? null : String.join(" ", item.getTags())}))
                .orElse(active);

        Map<String, Object> result = new LinkedHashMap<>();
        query.ifPresent(q -> result.put("query", q));
        type.ifPresent(t -> result.put("type", t.name()));
        result.put("total_found", matches.size());
        result.put("items", matches.stream().limit(MAX_RESULTS).map(LostFoundTool::describe).toList());
        if (matches.isEmpty()) {
            result.put("note", "No active posts match. The student can post the item on Athena's Lost & Found page.");
        }
        return result;
    }

    private static Map<String, Object> describe(LostFoundItem item) {
        Map<String, Object> details = new LinkedHashMap<>();
        ToolArgs.putIfPresent(details, "type", item.getType() == null ? null : item.getType().name());
        details.put("title", item.getTitle());
        ToolArgs.putIfPresent(details, "category", item.getCategory());
        ToolArgs.putIfPresent(details, "location", item.getLocation());
        ToolArgs.putIfPresent(details, "reported", ToolArgs.when(item.getDateReported()));
        if (item.getReward() != null && item.getReward() > 0) {
            details.put("reward", item.getReward());
        }
        ToolArgs.putIfPresent(details, "about", ToolArgs.clip(item.getDescription(), 200));
        return details;
    }
}
