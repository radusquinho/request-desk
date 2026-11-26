package com.radusquinho.requestdesk.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.radusquinho.requestdesk.MutableClock;
import com.radusquinho.requestdesk.service.RequestService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end through HTTP, security, rules, database and audit, on H2 or PostgreSQL. */
@SpringBootTest(properties = {"desk.jobs.sla-check=PT1H", "desk.jobs.webhook-dispatch=PT1H"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RequestFlowTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-26T09:00:00Z"));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MutableClock clock;

    @Autowired
    RequestService service;

    @Test
    void healthIsPublicButApiNeedsLogin() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/requests/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(httpBasic("ana", "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void smallExpenseIsApprovedByRulesAndAudited() throws Exception {
        long id = submit("ana", "{\"type\":\"EXPENSE\",\"title\":\"Taxi to client\",\"amount\":45}", "AUTO_APPROVED");

        mvc.perform(get("/api/requests/" + id).with(httpBasic("ana", "demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audit", hasSize(2)))
                .andExpect(jsonPath("$.audit[0].action").value("SUBMITTED"))
                .andExpect(jsonPath("$.audit[1].action").value("RULE_AUTO_APPROVED"))
                .andExpect(jsonPath("$.audit[1].actor").value("rules"))
                .andExpect(jsonPath("$.webhooks", hasSize(2)))
                .andExpect(jsonPath("$.webhooks[0].state").value("PENDING"));
    }

    @Test
    void managerApprovesFromQueue() throws Exception {
        long id = submit("ana", "{\"type\":\"EXPENSE\",\"title\":\"Conference ticket\",\"amount\":450}", "PENDING_REVIEW");

        mvc.perform(get("/api/review/queue").with(httpBasic("marta", "demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));

        mvc.perform(post("/api/review/" + id + "/approve").with(httpBasic("marta", "demo"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Within budget\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("marta"))
                .andExpect(jsonPath("$.reason").value("Approved by marta: Within budget"));

        // Deciding twice is a conflict, not a silent overwrite.
        mvc.perform(post("/api/review/" + id + "/reject").with(httpBasic("marta", "demo")))
                .andExpect(status().isConflict());
    }

    @Test
    void employeesCannotReviewAndNobodyReviewsTheirOwnRequest() throws Exception {
        long id = submit("marta", "{\"type\":\"PURCHASE\",\"title\":\"Monitor\",\"amount\":220}", "PENDING_REVIEW");

        mvc.perform(post("/api/review/" + id + "/approve").with(httpBasic("ana", "demo")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/review/" + id + "/approve").with(httpBasic("marta", "demo")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You cannot review your own request"));
        mvc.perform(get("/api/requests/" + id).with(httpBasic("luis", "demo")))
                .andExpect(status().isForbidden());
    }

    @Test
    void escalatedRequestsAreForAdminsOnly() throws Exception {
        long id = submit("ana", "{\"type\":\"EXPENSE\",\"title\":\"Laptop repair\",\"amount\":1500}", "ESCALATED");

        mvc.perform(post("/api/review/" + id + "/approve").with(httpBasic("marta", "demo")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/review/" + id + "/approve").with(httpBasic("admin", "demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void overdueReviewIsEscalatedByTheSlaJob() throws Exception {
        long id = submit("ana", "{\"type\":\"PURCHASE\",\"title\":\"Chair\",\"amount\":180}", "PENDING_REVIEW");

        // Other tests may have left rows in the shared database, so check this request, not a count.
        clock.advance(Duration.ofHours(47));
        service.escalateOverdue();
        mvc.perform(get("/api/requests/" + id).with(httpBasic("ana", "demo")))
                .andExpect(jsonPath("$.request.status").value("PENDING_REVIEW"));

        clock.advance(Duration.ofHours(2));
        service.escalateOverdue();

        mvc.perform(get("/api/requests/" + id).with(httpBasic("ana", "demo")))
                .andExpect(jsonPath("$.request.status").value("ESCALATED"))
                .andExpect(jsonPath("$.audit[2].action").value("SLA_ESCALATED"));
    }

    @Test
    void overlappingVacationIsSentToReview() throws Exception {
        submit("luis", "{\"type\":\"VACATION\",\"title\":\"Long weekend\",\"startDate\":\"2026-12-21\",\"endDate\":\"2026-12-22\"}",
                "AUTO_APPROVED");
        submit("ana", "{\"type\":\"VACATION\",\"title\":\"Christmas\",\"startDate\":\"2026-12-22\",\"endDate\":\"2026-12-23\"}",
                "PENDING_REVIEW");
    }

    @Test
    void invalidInputGetsAClearMessage() throws Exception {
        mvc.perform(post("/api/requests").with(httpBasic("ana", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VACATION\",\"title\":\"No dates\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A vacation needs a start and an end date"));
        mvc.perform(post("/api/requests").with(httpBasic("ana", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"title\":\"\",\"amount\":10}"))
                .andExpect(status().isBadRequest());
    }

    private long submit(String user, String json, String expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/requests").with(httpBasic(user, "demo"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }
}
