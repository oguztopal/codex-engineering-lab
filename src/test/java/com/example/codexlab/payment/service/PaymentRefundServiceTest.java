package com.example.codexlab.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.codexlab.common.exception.InvalidPaymentTransitionException;
import com.example.codexlab.common.exception.InvalidRefundAmountException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private MerchantService merchantService;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentTransactionMapper transactionMapper;

    private PaymentService service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, transactionRepository, merchantService,
                paymentMapper, transactionMapper);
        payment = new Payment(new Merchant("Test", "test@example.com"), new BigDecimal("10.00"), "USD");
        payment.complete();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
    }

    @Test
    void refundsNumericallyEqualAmountAndRecordsTransition() {
        PaymentResponse response = new PaymentResponse(1L, 1L, payment.getAmount(), "USD",
                PaymentStatus.REFUNDED, null, null);
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        assertThat(service.refund(1L, new RefundPaymentRequest(new BigDecimal("10")))).isSameAs(response);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getAmount()).isEqualByComparingTo("10.00");
        ArgumentCaptor<PaymentTransaction> history = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(history.capture());
        assertThat(history.getValue().getPayment()).isSameAs(payment);
        assertThat(history.getValue().getPreviousStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(history.getValue().getNewStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"CREATED", "FAILED", "REFUNDED"})
    void rejectsNonCompletedPayments(PaymentStatus status) {
        ReflectionTestUtils.setField(payment, "status", status);

        assertThatThrownBy(() -> service.refund(1L, new RefundPaymentRequest(new BigDecimal("10.00"))))
                .isInstanceOf(InvalidPaymentTransitionException.class);

        assertThat(payment.getStatus()).isEqualTo(status);
        verifyNoInteractions(transactionRepository, paymentMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "9.99", "10.01", "10.001", "10.000", "100000000000000000"})
    void rejectsInvalidAmountsWithoutMutation(String amount) {
        RefundPaymentRequest request = new RefundPaymentRequest(amount == null ? null : new BigDecimal(amount));

        assertThatThrownBy(() -> service.refund(1L, request)).isInstanceOf(InvalidRefundAmountException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verifyNoInteractions(transactionRepository, paymentMapper);
    }

    @Test
    void rejectsMissingRequest() {
        assertThatThrownBy(() -> service.refund(1L, null)).isInstanceOf(InvalidRefundAmountException.class);
        verifyNoInteractions(transactionRepository, paymentMapper);
    }

    @Test
    void rejectsMissingPayment() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refund(1L, new RefundPaymentRequest(BigDecimal.TEN)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(transactionRepository, paymentMapper);
    }
}
