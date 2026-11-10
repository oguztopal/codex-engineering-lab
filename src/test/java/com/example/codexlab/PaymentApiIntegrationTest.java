package com.example.codexlab;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.codexlab.merchant.repository.MerchantRepository;
import com.example.codexlab.payment.repository.PaymentRepository;
import com.example.codexlab.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class PaymentApiIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentTransactionRepository transactionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    @Test
    void createsAndListsMerchant() throws Exception {
        mockMvc.perform(post("/api/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Store","email":"payments@acme.test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/merchants/\\d+")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Acme Store"));

        mockMvc.perform(get("/api/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void createsPaymentCompletesItAndReturnsHistory() throws Exception {
        long merchantId = createMerchant();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(merchantId, "42.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(42.50))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        long paymentId = paymentRepository.findAll().getFirst().getId();
        mockMvc.perform(post("/api/payments/{id}/complete", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/payments/{id}/transactions", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].previousStatus").doesNotExist())
                .andExpect(jsonPath("$.data[0].newStatus").value("CREATED"))
                .andExpect(jsonPath("$.data[1].previousStatus").value("CREATED"))
                .andExpect(jsonPath("$.data[1].newStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data[1].createdAt").isNotEmpty());
    }

    @Test
    void rejectsInvalidAmountWithStructuredError() throws Exception {
        long merchantId = createMerchant();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(merchantId, "0.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.amount").exists());
    }

    @Test
    void rejectsCompletingPaymentTwice() throws Exception {
        long merchantId = createMerchant();
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(merchantId, "10.00")))
                .andExpect(status().isCreated());
        long paymentId = paymentRepository.findAll().getFirst().getId();

        mockMvc.perform(post("/api/payments/{id}/complete", paymentId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/payments/{id}/complete", paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_PAYMENT_TRANSITION"));
    }

    @Test
    void returnsNotFoundForUnknownPayment() throws Exception {
        mockMvc.perform(get("/api/payments/{id}", 99999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void paginatesPayments() throws Exception {
        long merchantId = createMerchant();
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(merchantId, "10.00")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(merchantId, "20.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/payments?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    private long createMerchant() throws Exception {
        mockMvc.perform(post("/api/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Store","email":"payments@acme.test"}
                                """))
                .andExpect(status().isCreated());
        return merchantRepository.findAll().getFirst().getId();
    }

    private String paymentJson(long merchantId, String amount) {
        return """
                {"merchantId":%d,"amount":%s,"currency":"USD"}
                """.formatted(merchantId, amount);
    }
}
