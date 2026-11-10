package com.example.codexlab.payment.mapper;

import com.example.codexlab.payment.dto.TransactionResponse;
import com.example.codexlab.payment.entity.PaymentTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentTransactionMapper {

    @Mapping(target = "paymentId", source = "payment.id")
    TransactionResponse toResponse(PaymentTransaction transaction);
}
