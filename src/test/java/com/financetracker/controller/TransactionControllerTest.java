package com.financetracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.entity.Transaction;
import com.financetracker.security.jwt.JwtAuthenticationFilter;
import com.financetracker.security.jwt.JwtTokenProvider;
import com.financetracker.service.impl.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;


import static java.util.Map.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@DisplayName("TransactionController")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock every bean that Spring Security or the controller needs
    @MockBean private TransactionService transactionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    // ── Helper ────────────────────────────────────────────────────────────────

    private Map<String, Object> validTransactionRequest() {
        return of(
                "accountUuid",       "acc-uuid-123",
                "amount",            new BigDecimal("50.00"),
                "type",              "EXPENSE",
                "description",       "Lunch",
                "transactionDate",   LocalDate.now().toString()
        );
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /transactions - valid request - should return 201")
    @WithMockUser(username = "user@test.com", roles = "USER")
    void createTransaction_validRequest_returns201() throws Exception {
        // Return a Transaction entity to match service return type
        Transaction mockTransaction = Transaction.builder()
                .uuid("txn-uuid-1")
                .amount(new BigDecimal("50.00"))
                .type(Transaction.TransactionType.EXPENSE)
                .description("Lunch")
                .transactionDate(LocalDate.now())
                .build();

        when(transactionService.createTransaction(anyString(), any()))
                .thenReturn(mockTransaction);

        mockMvc.perform(post("/api/v1/transactions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTransactionRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /transactions - missing amount - should return 400")
    @WithMockUser(username = "user@test.com", roles = "USER")
    void createTransaction_missingAmount_returns400() throws Exception {
        Map<String, Object> badRequest = of(
                "accountUuid", "acc-uuid-123",
                "type",        "EXPENSE",
                "transactionDate", LocalDate.now().toString()
                // amount missing
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /transactions - unauthenticated - should return 401")
    void getTransactions_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /transactions - authenticated - should return 200")
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getTransactions_authenticated_returns200() throws Exception {
        when(transactionService.getTransactions(
                anyString(),                          // email
                any(LocalDate.class),                 // startDate
                any(LocalDate.class),                 // endDate
                any(Pageable.class)                   // pageable
        )).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/transactions")
                        .param("startDate", LocalDate.now().minusMonths(1).toString())
                        .param("endDate",   LocalDate.now().toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /transactions/{uuid} - not found - should return 404")
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getTransaction_notFound_returns404() throws Exception {
        when(transactionService.getTransaction(anyString(), anyString()))
                .thenThrow(new com.financetracker.exception.ResourceNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/transactions/nonexistent-uuid"))
                .andExpect(status().isNotFound());
    }
}