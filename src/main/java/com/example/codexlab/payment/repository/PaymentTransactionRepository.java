package com.example.codexlab.payment.repository;

import com.example.codexlab.payment.entity.PaymentTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByPaymentIdOrderByCreatedAtAscIdAsc(Long paymentId);
}
