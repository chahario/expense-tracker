package com.fenmo.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenmo.expense.dto.CreateExpenseRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests: Spring context + H2 in-memory DB + full HTTP stack.
 *
 * @DirtiesContext ensures a fresh DB per test class.
 * Profile "test" loads application-test.properties: H2, Flyway disabled, ddl-auto=create-drop.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExpenseControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private CreateExpenseRequest validRequest() {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setAmount(new BigDecimal("500.00"));
        req.setCategory("Food");
        req.setDescription("Dinner at Punjab Grill");
        req.setDate(LocalDate.of(2024, 4, 20));
        return req;
    }

    // ─── POST /expenses ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /expenses returns 201 and expense body")
    void createExpense_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /expenses with idempotency key: second call returns same expense")
    void createExpense_idempotencyKey_deduplicatesRequest() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(validRequest());

        // First call
        MvcResult first = mockMvc.perform(post("/expenses")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        // Second call — same key, same body
        MvcResult second = mockMvc.perform(post("/expenses")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        // Must be the SAME expense — no duplicate insert
        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    @DisplayName("POST /expenses: negative amount returns 400")
    void createExpense_negativeAmount_returns400() throws Exception {
        CreateExpenseRequest req = validRequest();
        req.setAmount(new BigDecimal("-100.00"));

        mockMvc.perform(post("/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /expenses: missing required fields returns 400 with field errors")
    void createExpense_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4)); // all 4 fields missing
    }

    // ─── GET /expenses ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /expenses returns 200 with list (may be empty)")
    void listExpenses_returns200() throws Exception {
        mockMvc.perform(get("/expenses"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /expenses?category=Food filters by category")
    void listExpenses_withCategoryFilter_filtersCorrectly() throws Exception {
        // Create one Food, one Transport
        CreateExpenseRequest food = validRequest();
        CreateExpenseRequest transport = validRequest();
        transport.setCategory("Transport");
        transport.setDescription("Auto rickshaw");

        mockMvc.perform(post("/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(food))).andReturn();
        mockMvc.perform(post("/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transport))).andReturn();

        mockMvc.perform(get("/expenses").param("category", "Food"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].category").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("Food"))));
    }

    // ─── GET /expenses/summary ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /expenses/summary returns breakdown and grandTotal")
    void getSummary_returns200() throws Exception {
        mockMvc.perform(post("/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest()))).andReturn();

        mockMvc.perform(get("/expenses/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal").isNumber())
                .andExpect(jsonPath("$.totalCount").isNumber())
                .andExpect(jsonPath("$.categoryBreakdown").isArray());
    }

    // ─── X-Request-Id header ─────────────────────────────────────────────────

    @Test
    @DisplayName("Every response contains X-Request-Id correlation header")
    void everyResponse_hasRequestIdHeader() throws Exception {
        mockMvc.perform(get("/expenses"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }
}
