package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.model.Competition;
import com.smartuniversity.model.Event;
import com.smartuniversity.model.LostFoundItem;
import com.smartuniversity.model.StudySpace;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.CompetitionRepository;
import com.smartuniversity.repository.EventRepository;
import com.smartuniversity.repository.LostFoundItemRepository;
import com.smartuniversity.repository.StudySpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** The tools that read campus-wide data: events, competitions, study spaces and Lost &amp; Found. */
@ExtendWith(MockitoExtension.class)
class CampusToolsTest {

    /** Friday 23 October 2026, 10:00 in Colombo. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T04:30:00Z"), ZoneId.of("Asia/Colombo"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private StudySpaceRepository studySpaceRepository;

    @Mock
    private LostFoundItemRepository lostFoundItemRepository;

    // ==================== Events ====================

    @Test
    void listsVisibleEventsInsideTheWindow() throws Exception {
        Event hackathon = event("Hackathon kickoff", NOW.plusDays(2), "Tech");
        Event hidden = event("Hidden meetup", NOW.plusDays(3), "Social");
        hidden.setHidden(true);
        Event later = event("Graduation", NOW.plusDays(60), "Academic");
        when(eventRepository.findByStatusAndEventDateAfterOrderByEventDateAsc(Event.ApprovalStatus.APPROVED, NOW))
                .thenReturn(List.of(hackathon, hidden, later));

        JsonNode result = run(new UpcomingEventsTool(eventRepository, CLOCK), "{\"days_ahead\": 7}");

        assertEquals("Fri 23 Oct 2026, 10:00", result.get("today").asText());
        assertEquals(1, result.get("total_found").asInt());
        JsonNode first = result.get("events").get(0);
        assertEquals("Hackathon kickoff", first.get("title").asText());
        assertEquals("Sun 25 Oct 2026, 10:00", first.get("starts").asText());
        assertEquals("Tech", first.get("category").asText());
    }

    @Test
    void narrowsEventsByKeywordAndExplainsWhenNothingMatches() throws Exception {
        when(eventRepository.findByStatusAndEventDateAfterOrderByEventDateAsc(Event.ApprovalStatus.APPROVED, NOW))
                .thenReturn(List.of(event("Chess club", NOW.plusDays(1), "Club Activity"), event("Cricket finals", NOW.plusDays(2), "Sports")));
        UpcomingEventsTool tool = new UpcomingEventsTool(eventRepository, CLOCK);

        JsonNode sports = run(tool, "{\"query\": \"sports\"}");
        JsonNode none = run(tool, "{\"query\": \"robotics\"}");

        assertEquals("Cricket finals", sports.get("events").get(0).get("title").asText());
        assertEquals(1, sports.get("total_found").asInt());
        assertEquals(0, none.get("total_found").asInt());
        assertTrue(none.has("note"));
    }

    @Test
    void keepsTheEventWindowBetweenOneAndNinetyDays() throws Exception {
        when(eventRepository.findByStatusAndEventDateAfterOrderByEventDateAsc(Event.ApprovalStatus.APPROVED, NOW)).thenReturn(List.of());
        UpcomingEventsTool tool = new UpcomingEventsTool(eventRepository, CLOCK);

        assertEquals(90, run(tool, "{\"days_ahead\": 5000}").get("days_ahead").asInt());
        assertEquals(30, run(tool, "{}").get("days_ahead").asInt());
    }

    @Test
    void showsAtMostEightEvents() throws Exception {
        List<Event> many = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> event("Talk " + i, NOW.plusHours(i + 1), "Academic")).toList();
        when(eventRepository.findByStatusAndEventDateAfterOrderByEventDateAsc(Event.ApprovalStatus.APPROVED, NOW)).thenReturn(many);

        JsonNode result = run(new UpcomingEventsTool(eventRepository, CLOCK), "{}");

        assertEquals(12, result.get("total_found").asInt());
        assertEquals(UpcomingEventsTool.MAX_RESULTS, result.get("events").size());
    }

    // ==================== Competitions ====================

    @Test
    void describesHowToEnterEachCompetition() throws Exception {
        Competition internal = competition("Code sprint");
        internal.setInternalEnrollmentEnabled(true);
        Competition external = competition("Design jam");
        external.setExternalEnrollmentUrl("https://example.org/jam");
        Competition hidden = competition("Old contest");
        hidden.setHidden(true);
        when(competitionRepository.findByStatusAndEndDateAfterOrderByStartDateAsc(Competition.ApprovalStatus.APPROVED, NOW))
                .thenReturn(List.of(internal, external, hidden));

        JsonNode result = run(new CompetitionsTool(competitionRepository, CLOCK), "{}");

        assertEquals(2, result.get("total_found").asInt());
        assertEquals("Enrol on the competition's page in Athena.", result.at("/competitions/0/how_to_enter").asText());
        assertEquals("Register at https://example.org/jam", result.at("/competitions/1/how_to_enter").asText());
    }

    // ==================== Study spaces ====================

    @Test
    void listsQuietSpacesLeastCrowdedFirst() throws Exception {
        when(studySpaceRepository.findAll()).thenReturn(List.of(
                space("Main reading room", "Library", "quiet", "CROWDED"),
                space("Group pods", "Library", "lively", "EMPTY"),
                space("Silent hall", "IT Faculty", "silent", "EMPTY"),
                space("Level 2 desks", "Library", "quiet", "MODERATE")));
        StudySpacesTool tool = new StudySpacesTool(studySpaceRepository);

        JsonNode quiet = run(tool, "{\"quiet_only\": true}");
        JsonNode library = run(tool, "{\"building\": \"library\", \"quiet_only\": true}");

        assertEquals(List.of("Silent hall", "Level 2 desks", "Main reading room"), names(quiet.get("spaces")));
        assertEquals("empty", quiet.at("/spaces/0/crowd").asText());
        assertEquals(List.of("Level 2 desks", "Main reading room"), names(library.get("spaces")));
    }

    // ==================== Lost & Found ====================

    @Test
    void neverRevealsWhoPostedAnItem() throws Exception {
        LostFoundItem wallet = item(LostFoundItem.ItemType.FOUND, "Black wallet", "Found near the canteen");
        User poster = new User();
        poster.setFirstName("Nimal");
        poster.setEmail("nimal@example.com");
        wallet.setPostedBy(poster);
        when(lostFoundItemRepository.findActiveItemsOrderByDate(LostFoundItem.ItemStatus.ACTIVE)).thenReturn(List.of(wallet));

        String result = objectMapper.writeValueAsString(
                new LostFoundTool(lostFoundItemRepository).run(objectMapper.readTree("{\"query\": \"wallet\"}"), 1L));

        assertTrue(result.contains("Black wallet"));
        assertFalse(result.contains("Nimal"));
        assertFalse(result.contains("nimal@example.com"));
    }

    @Test
    void searchesOnlyTheRequestedKindOfPost() throws Exception {
        LostFoundItem foundId = item(LostFoundItem.ItemType.FOUND, "Student ID card", "Blue lanyard");
        foundId.setTags(Set.of("id", "card"));
        LostFoundItem lostId = item(LostFoundItem.ItemType.LOST, "Lost my ID", "Please help");
        LostFoundItem umbrella = item(LostFoundItem.ItemType.FOUND, "Umbrella", "Black umbrella");
        when(lostFoundItemRepository.findActiveItemsOrderByDate(LostFoundItem.ItemStatus.ACTIVE)).thenReturn(List.of(foundId, lostId, umbrella));

        JsonNode result = run(new LostFoundTool(lostFoundItemRepository), "{\"query\": \"ID card\", \"type\": \"found\"}");

        assertEquals("FOUND", result.get("type").asText());
        assertEquals(1, result.get("total_found").asInt());
        assertEquals("Student ID card", result.at("/items/0/title").asText());
    }

    @Test
    void rejectsAnUnknownKindOfPost() {
        LostFoundTool tool = new LostFoundTool(lostFoundItemRepository);

        assertThrows(IllegalArgumentException.class, () -> tool.run(objectMapper.readTree("{\"type\": \"stolen\"}"), 1L));
    }

    // ==================== Helpers ====================

    private JsonNode run(AssistantTool tool, String args) throws Exception {
        return objectMapper.valueToTree(tool.run(objectMapper.readTree(args), 1L));
    }

    private static List<String> names(JsonNode spaces) {
        List<String> names = new java.util.ArrayList<>();
        spaces.forEach(space -> names.add(space.get("name").asText()));
        return names;
    }

    private static Event event(String title, LocalDateTime start, String category) {
        Event event = new Event();
        event.setTitle(title);
        event.setEventDate(start);
        event.setEndTime(start.plusHours(2));
        event.setCategory(category);
        event.setStatus(Event.ApprovalStatus.APPROVED);
        return event;
    }

    private static Competition competition(String title) {
        Competition competition = new Competition();
        competition.setTitle(title);
        competition.setStartDate(NOW.plusDays(5));
        competition.setEndDate(NOW.plusDays(6));
        return competition;
    }

    private static StudySpace space(String name, String building, String noise, String crowd) {
        StudySpace space = new StudySpace();
        space.setName(name);
        space.setBuilding(building);
        space.setDefaultNoiseLevel(noise);
        space.setStatus(crowd);
        return space;
    }

    private static LostFoundItem item(LostFoundItem.ItemType type, String title, String description) {
        LostFoundItem item = new LostFoundItem();
        item.setType(type);
        item.setTitle(title);
        item.setDescription(description);
        item.setDateReported(NOW.minusDays(1));
        return item;
    }
}
