package com.example.codexlab.payment.mapper;

import com.example.codexlab.payment.dto.PaymentResponse;
import com.example.codexlab.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "merchantId", source = "merchant.id")
    PaymentResponse toResponse(Payment payment);
}
