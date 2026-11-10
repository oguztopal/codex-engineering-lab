package com.example.codexlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.codexlab.merchant.entity.Merchant;
import com.example.codexlab.merchant.repository.MerchantRepository;
import com.example.codexlab.payment.dto.CreatePaymentRequest;
import com.example.codexlab.payment.entity.Payment;
import com.example.codexlab.payment.entity.PaymentStatus;
import com.example.codexlab.payment.entity.PaymentTransaction;
import com.example.codexlab.payment.mapper.PaymentMapper;
import com.example.codexlab.payment.repository.PaymentRepository;
import com.example.codexlab.payment.repository.PaymentTransactionRepository;
import com.example.codexlab.payment.service.PaymentService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class PaymentCancellationIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private PaymentTransactionRepository transactionRepository;
    @MockitoSpyBean private PaymentMapper paymentMapper;

    private MockMvc mockMvc;
    private long paymentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
        merchantRepository.deleteAll();
        Merchant merchant = merchantRepository.save(new Merchant("Store", "store@example.com"));
        paymentId = paymentService.create(new CreatePaymentRequest(
                merchant.getId(), new BigDecimal("42.50"), "USD")).id();
    }

    @Test
    void cancelsPaymentAndPreservesDetailsAndHistory() throws Exception {
        mockMvc.perform(post("/api/payments/{id}/cancel", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(paymentId))
                .andExpect(jsonPath("$.data.amount").value(42.50))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mockMvc.perform(get("/api/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mockMvc.perform(get("/api/payments/{id}/transactions", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].newStatus").value("CREATED"))
                .andExpect(jsonPath("$.data[1].previousStatus").value("CREATED"))
                .andExpect(jsonPath("$.data[1].newStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data[1].createdAt").isNotEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "CREATED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsEveryInvalidTransitionWithoutChangingStoredData(PaymentStatus initialStatus) throws Exception {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        ReflectionTestUtils.setField(payment, "status", initialStatus);
        paymentRepository.saveAndFlush(payment);

        mockMvc.perform(post("/api/payments/{id}/cancel", paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_TRANSITION"));
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(initialStatus);
        assertThat(transactionRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId)).hasSize(1);
    }

    @Test
    void rejectsDuplicateCancellationAndCompletionAfterCancellation() throws Exception {
        mockMvc.perform(post("/api/payments/{id}/cancel", paymentId)).andExpect(status().isOk());
        for (String action : new String[]{"cancel", "complete"}) {
            mockMvc.perform(post("/api/payments/{id}/" + action, paymentId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_TRANSITION"));
        }
        assertThat(transactionRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId)).hasSize(2);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void returnsStructuredNotFound() throws Exception {
        mockMvc.perform(post("/api/payments/{id}/cancel", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "complete"})
    void concurrentTransitionsCommitExactlyOneUpdateAndHistoryEntry(String competingAction) throws Exception {
        // Both requests reach mapping with the same version before either is allowed to commit.
        CyclicBarrier beforeCommit = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object response = invocation.callRealMethod();
            beforeCommit.await(10, TimeUnit.SECONDS);
            return response;
        }).when(paymentMapper).toResponse(any(Payment.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var cancellation = executor.submit(() -> mockMvc.perform(
                    post("/api/payments/{id}/cancel", paymentId)).andReturn());
            var competitor = executor.submit(() -> mockMvc.perform(
                    post("/api/payments/{id}/" + competingAction, paymentId)).andReturn());
            MvcResult first = cancellation.get(20, TimeUnit.SECONDS);
            MvcResult second = competitor.get(20, TimeUnit.SECONDS);
            assertThat(new int[]{first.getResponse().getStatus(), second.getResponse().getStatus()})
                    .containsExactlyInAnyOrder(200, 409);
            MvcResult conflict = first.getResponse().getStatus() == 409 ? first : second;
            jsonPath("$.success").value(false).match(conflict);
            jsonPath("$.error.code").value("PAYMENT_CONFLICT").match(conflict);
            PaymentStatus winner = first.getResponse().getStatus() == 200 || competingAction.equals("cancel")
                    ? PaymentStatus.CANCELLED : PaymentStatus.COMPLETED;
            assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(winner);
            var history = transactionRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId);
            assertThat(history).hasSize(2);
            assertThat(history.getLast().getPreviousStatus()).isEqualTo(PaymentStatus.CREATED);
            assertThat(history.getLast().getNewStatus()).isEqualTo(winner);
        }
    }

    @Test
    void historyInsertFailureRollsBackCancellation() {
        doThrow(new IllegalStateException("history unavailable"))
                .when(transactionRepository).save(any(PaymentTransaction.class));
        assertThatThrownBy(() -> paymentService.cancel(paymentId))
                .isInstanceOf(IllegalStateException.class).hasMessage("history unavailable");
        assertUnchanged();
    }

    @Test
    void failureAfterFlushingBothWritesRollsBackPaymentAndHistory() {
        doAnswer(invocation -> {
            entityManager.flush();
            throw new IllegalStateException("failure after flush");
        }).when(paymentMapper).toResponse(any(Payment.class));
        assertThatThrownBy(() -> paymentService.cancel(paymentId))
                .isInstanceOf(IllegalStateException.class).hasMessage("failure after flush");
        assertUnchanged();
    }

    private void assertUnchanged() {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.getVersion()).isZero();
        var history = transactionRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getNewStatus()).isEqualTo(PaymentStatus.CREATED);
    }
}
