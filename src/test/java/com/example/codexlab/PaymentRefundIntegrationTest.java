package com.example.codexlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.codexlab.merchant.entity.Merchant;
import com.example.codexlab.merchant.repository.MerchantRepository;
import com.example.codexlab.payment.dto.CreatePaymentRequest;
import com.example.codexlab.payment.dto.RefundPaymentRequest;
import com.example.codexlab.payment.entity.PaymentStatus;
import com.example.codexlab.payment.entity.Payment;
import com.example.codexlab.payment.entity.PaymentTransaction;
import com.example.codexlab.payment.mapper.PaymentMapper;
import com.example.codexlab.payment.repository.PaymentRepository;
import com.example.codexlab.payment.repository.PaymentTransactionRepository;
import com.example.codexlab.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class PaymentRefundIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentTransactionRepository transactionRepository;
    @MockitoSpyBean private PaymentMapper paymentMapper;

    private MockMvc mockMvc;
    private long paymentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
        merchantRepository.deleteAll();
        Merchant merchant = merchantRepository.save(new Merchant("Refund Store", "refund@example.com"));
        paymentId = paymentService.create(new CreatePaymentRequest(merchant.getId(), new BigDecimal("42.50"), "USD")).id();
        paymentService.complete(paymentId);
    }

    @Test
    void refundsOnceAndPreservesPaymentAndHistoryContracts() throws Exception {
        refund("{\"amount\":42.50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.amount").value(42.50))
                .andExpect(jsonPath("$.data.currency").value("USD"));
        refund("{\"amount\":42.50}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_TRANSITION"));
        mockMvc.perform(post("/api/payments/{id}/complete", paymentId))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
        mockMvc.perform(get("/api/payments/{id}/transactions", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[2].previousStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data[2].newStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data[2].createdAt").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"amount\":null}", "{\"amount\":0}", "{\"amount\":-1}",
            "{\"amount\":42.501}", "{\"amount\":42.500}", "{\"amount\":100000000000000000}",
            "{\"amount\":42.49}", "{\"amount\":42.51}"})
    void rejectsInvalidAmountsWithoutChangingState(String body) throws Exception {
        refund(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.amount").exists());
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"amount\":\"invalid\"}"})
    void rejectsMalformedRequests(String body) throws Exception {
        refund(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        assertUnchanged();
    }

    @Test
    void rejectsCreatedPayment() throws Exception {
        long merchantId = merchantRepository.findAll().getFirst().getId();
        long createdId = paymentService.create(new CreatePaymentRequest(merchantId, BigDecimal.TEN, "USD")).id();
        mockMvc.perform(post("/api/payments/{id}/refund", createdId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_TRANSITION"));
        assertThat(paymentRepository.findById(createdId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(transactionRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(createdId)).hasSize(1);
    }

    @Test
    void returnsNotFoundForMissingPayment() throws Exception {
        mockMvc.perform(post("/api/payments/{id}/refund", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":42.50}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertUnchanged();
    }

    @Test
    void rollsBackBothWritesWhenRefundFailsAfterPersistence() {
        doAnswer(invocation -> {
            // Force both writes to the database before failing, so rollback is observable.
            transactionRepository.flush();
            throw new IllegalStateException("Simulated response mapping failure");
        }).when(paymentMapper).toResponse(any(Payment.class));

        assertThatThrownBy(() -> paymentService.refund(paymentId, new RefundPaymentRequest(new BigDecimal("42.50"))))
                .isInstanceOf(IllegalStateException.class).hasMessage("Simulated response mapping failure");
        assertUnchanged();
    }

    @Test
    void concurrentRefundsCommitOnlyOneHistoryEntry() throws Exception {
        CyclicBarrier bothRefundsReady = new CyclicBarrier(2);
        doAnswer(invocation -> {
            // Both requests have read COMPLETED and its version before either can commit.
            bothRefundsReady.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(paymentMapper).toResponse(any(Payment.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> refund("{\"amount\":42.50}").andReturn().getResponse().getStatus());
            var second = executor.submit(() -> refund("{\"amount\":42.50}").andReturn().getResponse().getStatus());
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(transactionRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId))
                .extracting(PaymentTransaction::getNewStatus)
                .containsExactly(PaymentStatus.CREATED, PaymentStatus.COMPLETED, PaymentStatus.REFUNDED);
    }

    private ResultActions refund(String body) throws Exception {
        return mockMvc.perform(post("/api/payments/{id}/refund", paymentId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertUnchanged() {
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(transactionRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId))
                .extracting(PaymentTransaction::getNewStatus)
                .containsExactly(PaymentStatus.CREATED, PaymentStatus.COMPLETED);
    }
}
