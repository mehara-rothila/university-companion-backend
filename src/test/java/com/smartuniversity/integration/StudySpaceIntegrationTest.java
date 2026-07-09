package com.smartuniversity.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.model.StudySpace;
import com.smartuniversity.model.StudySpaceReport;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.StudySpaceReportRepository;
import com.smartuniversity.repository.StudySpaceRepository;
import com.smartuniversity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
@AutoConfigureMockMvc
@Transactional
public class StudySpaceIntegrationTest {

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudySpaceRepository studySpaceRepository;

    @Autowired
    private StudySpaceReportRepository studySpaceReportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User studentUser;
    private User adminUser;
    private StudySpace testSpace;

    @BeforeEach
    void setUp() {
        studySpaceReportRepository.deleteAll();
        studySpaceRepository.deleteAll();
        userRepository.deleteAll();

        // Create student user
        studentUser = new User("Alice", "Perera", "alice@uni.edu", "alice", "password");
        studentUser.setRole(User.UserRole.STUDENT);
        studentUser = userRepository.save(studentUser);

        // Create admin user
        adminUser = new User("Bob", "Admin", "bob@uni.edu", "bob", "password");
        adminUser.setRole(User.UserRole.ADMIN);
        adminUser = userRepository.save(adminUser);

        // Create a default study space
        testSpace = new StudySpace();
        testSpace.setName("Main Library Floor 1");
        testSpace.setBuilding("Library");
        testSpace.setFloor(1);
        testSpace.setRoom("101");
        testSpace.setCapacity(150);
        testSpace.setDefaultNoiseLevel("QUIET");
        testSpace.setStatus("EMPTY");
        testSpace = studySpaceRepository.save(testSpace);
    }

    @Test
    @WithMockUser(username = "alice", roles = "STUDENT")
    void getAllSpaces_returnsList() throws Exception {
        mockMvc.perform(get("/api/study-spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Main Library Floor 1"))
                .andExpect(jsonPath("$[0].status").value("EMPTY"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "STUDENT")
    void submitReport_updatesOccupancyConsensus() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("status", "CROWDED");

        // Submit vote as Alice
        mockMvc.perform(post("/api/study-spaces/" + testSpace.getId() + "/vote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CROWDED"));

        // Fetch spaces again to check consensus (majority of last 2 hours votes should make it CROWDED)
        mockMvc.perform(get("/api/study-spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CROWDED"));
    }

    @Test
    @WithMockUser(username = "bob", roles = "ADMIN")
    void admin_canCreateAndDeleteSpace() throws Exception {
        StudySpace newSpace = new StudySpace();
        newSpace.setName("CSE Discussion Room");
        newSpace.setBuilding("CSE Building");
        newSpace.setFloor(2);
        newSpace.setRoom("202");
        newSpace.setCapacity(30);
        newSpace.setDefaultNoiseLevel("MODERATE");
        newSpace.setStatus("EMPTY");

        // Create new space as Admin
        mockMvc.perform(post("/api/admin/study-spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newSpace)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CSE Discussion Room"));

        // Verify it was saved
        List<StudySpace> spaces = studySpaceRepository.findAll();
        assertEquals(2, spaces.size());

        // Find the ID of the new space
        Long newSpaceId = spaces.stream()
                .filter(s -> s.getName().equals("CSE Discussion Room"))
                .findFirst()
                .orElseThrow()
                .getId();

        // Delete the new space as Admin
        mockMvc.perform(delete("/api/admin/study-spaces/" + newSpaceId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("deleted successfully")));

        // Verify it was deleted
        assertEquals(1, studySpaceRepository.count());
    }

    @Test
    @WithMockUser(username = "alice", roles = "STUDENT")
    void student_cannotAccessAdminEndpoints() throws Exception {
        StudySpace newSpace = new StudySpace();
        newSpace.setName("Hackerspace");
        newSpace.setBuilding("IT Center");
        newSpace.setFloor(1);
        newSpace.setRoom("102");
        newSpace.setCapacity(50);
        newSpace.setDefaultNoiseLevel("LOUD");

        mockMvc.perform(post("/api/admin/study-spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newSpace)))
                .andExpect(status().isForbidden());
    }
}
