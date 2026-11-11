package com.example.codexlab.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.codexlab.common.exception.InvalidPaymentTransitionException;
import com.example.codexlab.common.exception.ResourceNotFoundException;
import com.example.codexlab.merchant.entity.Merchant;
import com.example.codexlab.merchant.service.MerchantService;
import com.example.codexlab.payment.dto.PaymentResponse;
import com.example.codexlab.payment.dto.RefundPaymentRequest;
import com.example.codexlab.payment.entity.Payment;
import com.example.codexlab.payment.entity.PaymentStatus;
import com.example.codexlab.payment.entity.PaymentTransaction;
import com.example.codexlab.payment.mapper.PaymentMapper;
import com.example.codexlab.payment.mapper.PaymentTransactionMapper;
import com.example.codexlab.payment.repository.PaymentRepository;
import com.example.codexlab.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private MerchantService merchantService;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentTransactionMapper transactionMapper;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                transactionRepository,
                merchantService,
                paymentMapper,
                transactionMapper);
    }

    @Test
    void completesCreatedPaymentAndRecordsTransition() {
        Payment payment = new Payment(new Merchant("Test", "test@example.com"), new BigDecimal("19.99"), "USD");
        PaymentResponse response = new PaymentResponse(null, null, new BigDecimal("19.99"), "USD",
                PaymentStatus.COMPLETED, null, null);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        paymentService.complete(1L);

        verify(transactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void rejectsCompletingAnAlreadyCompletedPayment() {
        Payment payment = new Payment(new Merchant("Test", "test@example.com"), new BigDecimal("19.99"), "USD");
        payment.complete();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.complete(1L))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("COMPLETED to COMPLETED");
    }

    @Test
    void refundsCompletedPaymentAndRecordsExactTransition() {
        Payment payment = new Payment(new Merchant("Test", "test@example.com"), new BigDecimal("19.99"), "USD");
        payment.complete();
        PaymentResponse response = new PaymentResponse(null, null, payment.getAmount(), "USD",
                PaymentStatus.REFUNDED, null, null);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        assertThat(paymentService.refund(1L, new RefundPaymentRequest(new BigDecimal("19.99")))).isSameAs(response);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<PaymentTransaction> history = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(history.capture());
        assertThat(history.getValue().getPayment()).isSameAs(payment);
        assertThat(history.getValue().getPreviousStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(history.getValue().getNewStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsRefundFromEveryOtherStatus(PaymentStatus status) {
        Payment payment = new Payment(new Merchant("Test", "test@example.com"), new BigDecimal("19.99"), "USD");
        payment.complete();
        ReflectionTestUtils.setField(payment, "status", status);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(1L, new RefundPaymentRequest(new BigDecimal("19.99"))))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining(status + " to REFUNDED");
        assertThat(payment.getStatus()).isEqualTo(status);
        verifyNoInteractions(transactionRepository, paymentMapper);
    }

    @Test
    void rejectsRefundOfUnknownPayment() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.refund(1L, new RefundPaymentRequest(new BigDecimal("19.99")))).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(transactionRepository, paymentMapper);
    }

    @Test
    void propagatesHistoryFailure() {
        Payment payment = new Payment(new Merchant("Test", "test@example.com"), new BigDecimal("19.99"), "USD");
        payment.complete();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(transactionRepository.save(any())).thenThrow(new IllegalStateException("history failure"));
        assertThatThrownBy(() -> paymentService.refund(1L, new RefundPaymentRequest(new BigDecimal("19.99"))))
                .isInstanceOf(IllegalStateException.class).hasMessage("history failure");
        verifyNoInteractions(paymentMapper);
    }
}
