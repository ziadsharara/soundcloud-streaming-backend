package com.soundstream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SoundStreamApplicationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void healthEndpointIsAvailableForContainerProbes() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void roomApiSupportsTheCompleteLifecycleWithoutLeakingTheHostToken() throws Exception {
        String body = mvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Integration room","hostName":"Test host"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room.name").value("Integration room"))
                .andExpect(jsonPath("$.room.hostToken").doesNotExist())
                .andExpect(jsonPath("$.hostToken").isString())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = json.readTree(body);
        String roomId = created.path("room").path("id").asText();
        String hostToken = created.path("hostToken").asText();

        mvc.perform(get("/api/rooms/{id}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId))
                .andExpect(jsonPath("$.hostToken").doesNotExist());

        mvc.perform(delete("/api/rooms/{id}", roomId).header("X-Host-Token", "wrong-token"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/rooms/{id}", roomId).header("X-Host-Token", hostToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/rooms/{id}", roomId))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomCreationRejectsBlankInput() throws Exception {
        mvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   ","hostName":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corsAllowsTheConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/api/rooms")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
