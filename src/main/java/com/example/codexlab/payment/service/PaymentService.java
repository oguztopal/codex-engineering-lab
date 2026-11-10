package com.example.codexlab.payment.service;

import com.example.codexlab.common.api.PageResponse;
import com.example.codexlab.common.exception.InvalidPaymentTransitionException;
import com.example.codexlab.common.exception.ResourceNotFoundException;
import com.example.codexlab.merchant.entity.Merchant;
import com.example.codexlab.merchant.service.MerchantService;
import com.example.codexlab.payment.dto.CreatePaymentRequest;
import com.example.codexlab.payment.dto.PaymentResponse;
import com.example.codexlab.payment.dto.TransactionResponse;
import com.example.codexlab.payment.entity.Payment;
import com.example.codexlab.payment.entity.PaymentStatus;
import com.example.codexlab.payment.entity.PaymentTransaction;
import com.example.codexlab.payment.mapper.PaymentMapper;
import com.example.codexlab.payment.mapper.PaymentTransactionMapper;
import com.example.codexlab.payment.repository.PaymentRepository;
import com.example.codexlab.payment.repository.PaymentTransactionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final MerchantService merchantService;
    private final PaymentMapper paymentMapper;
    private final PaymentTransactionMapper transactionMapper;

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        Merchant merchant = merchantService.findEntity(request.merchantId());
        Payment payment = paymentRepository.save(new Payment(merchant, request.amount(), request.currency()));
        transactionRepository.save(new PaymentTransaction(payment, null, PaymentStatus.CREATED));
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(Long id) {
        return paymentMapper.toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentResponse> payments = paymentRepository.findAll(pageRequest).map(paymentMapper::toResponse);
        return PageResponse.from(payments);
    }

    @Transactional
    public PaymentResponse complete(Long id) {
        Payment payment = findEntity(id);
        PaymentStatus previousStatus = payment.getStatus();
        if (previousStatus != PaymentStatus.CREATED) {
            throw new InvalidPaymentTransitionException(
                    "Payment %d cannot transition from %s to COMPLETED".formatted(id, previousStatus));
        }

        payment.complete();
        transactionRepository.save(new PaymentTransaction(payment, previousStatus, PaymentStatus.COMPLETED));
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse cancel(Long id) {
        Payment payment = findEntity(id);
        PaymentStatus previousStatus = payment.getStatus();
        if (previousStatus != PaymentStatus.CREATED) {
            throw new InvalidPaymentTransitionException(
                    "Payment %d cannot transition from %s to CANCELLED".formatted(id, previousStatus));
        }

        payment.cancel();
        transactionRepository.save(new PaymentTransaction(payment, previousStatus, PaymentStatus.CANCELLED));
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getHistory(Long id) {
        findEntity(id);
        return transactionRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    private Payment findEntity(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment %d was not found".formatted(id)));
    }
}
