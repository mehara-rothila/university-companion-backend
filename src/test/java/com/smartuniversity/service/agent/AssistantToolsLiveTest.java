package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.dto.ChatbotResponse;
import com.smartuniversity.model.Event;
import com.smartuniversity.model.StudySpace;
import com.smartuniversity.repository.EventRepository;
import com.smartuniversity.repository.StudySpaceRepository;
import com.smartuniversity.service.GeminiChatService;
import com.smartuniversity.service.S3Service;
import com.smartuniversity.service.rag.DocumentIndexService;
import com.smartuniversity.service.rag.DocumentRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks tool calling against the real Gemini API: questions that need a lookup should make the
 * model call the right tools, and the answer should use what they returned. The tools are real;
 * only their repositories are stubbed. It's opt-in because it calls the API and uses quota:
 *
 * <pre>
 * AGENT_EVAL=1 GEMINI_API_KEY=... [GEMINI_MODEL=gemini-3.5-flash] [GEMINI_THINKING_BUDGET=0] \
 *   mvn test -Dtest=AssistantToolsLiveTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "AGENT_EVAL", matches = ".+")
class AssistantToolsLiveTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiChatService chat;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Colombo"));

        Event workshop = new Event();
        workshop.setTitle("Robotics Society: Drone Building Workshop");
        workshop.setCategory("Club Activity");
        workshop.setLocation("IT Faculty, Lab 3");
        workshop.setEventDate(now.plusDays(3).withHour(14).withMinute(0));
        workshop.setEndTime(now.plusDays(3).withHour(17).withMinute(0));
        workshop.setDescription("Build and fly a small quadcopter. Bring a laptop.");
        EventRepository events = mock(EventRepository.class);
        when(events.findByStatusAndEventDateAfterOrderByEventDateAsc(eq(Event.ApprovalStatus.APPROVED), any()))
                .thenReturn(List.of(workshop));

        StudySpace silentHall = new StudySpace();
        silentHall.setName("Silent Reading Hall");
        silentHall.setBuilding("Main Library");
        silentHall.setFloor(2);
        silentHall.setDefaultNoiseLevel("silent");
        silentHall.setStatus("EMPTY");
        StudySpaceRepository spaces = mock(StudySpaceRepository.class);
        when(spaces.findAll()).thenReturn(List.of(silentHall));

        AssistantToolbox toolbox = new AssistantToolbox(
                List.of(new UpcomingEventsTool(events), new StudySpacesTool(spaces)), objectMapper);
        chat = new GeminiChatService(new RestTemplate(), objectMapper, mock(S3Service.class),
                mock(DocumentIndexService.class), mock(DocumentRetriever.class), toolbox);
        ReflectionTestUtils.setField(chat, "apiKey", System.getenv("GEMINI_API_KEY"));
        ReflectionTestUtils.setField(chat, "baseUrl", "https://generativelanguage.googleapis.com/v1beta");
        ReflectionTestUtils.setField(chat, "model", System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash"));
        ReflectionTestUtils.setField(chat, "thinkingBudget",
                Integer.parseInt(System.getenv().getOrDefault("GEMINI_THINKING_BUDGET", "0")));
        ReflectionTestUtils.setField(chat, "agentEnabled", true);
    }

    @Test
    void looksUpEventsBeforeAnswering() {
        ChatbotResponse response = ask("Are there any workshops on campus in the next week?");

        assertEquals(List.of("find_upcoming_events"), response.getToolsUsed());
        assertTrue(response.getResponse().toLowerCase(Locale.ROOT).contains("drone"), response.getResponse());
    }

    @Test
    void usesTwoToolsForAQuestionThatNeedsBoth() {
        ChatbotResponse response = ask("I need a silent place to study this afternoon, and is anything happening on campus this week?");

        assertTrue(response.getToolsUsed().containsAll(List.of("find_upcoming_events", "find_study_spaces")),
                "tools used: " + response.getToolsUsed());
        String answer = response.getResponse().toLowerCase(Locale.ROOT);
        assertTrue(answer.contains("silent reading hall") && answer.contains("drone"), response.getResponse());
    }

    @Test
    void answersSmallTalkWithoutLookingAnythingUp() {
        ChatbotResponse response = ask("Thanks Athena, that's all for now!");

        assertNull(response.getToolsUsed());
    }

    private ChatbotResponse ask(String question) {
        ChatbotResponse response = chat.chat(new ChatbotRequest(question, 1L));
        System.out.printf("%nQ: %s%nTools: %s | tokens in %s, out %s%nA: %s%n",
                question, response.getToolsUsed(), response.getInputTokens(), response.getOutputTokens(),
                response.isSuccess() ? response.getResponse() : "ERROR " + response.getError());
        assertTrue(response.isSuccess(), response.getError());
        return response;
    }
}
