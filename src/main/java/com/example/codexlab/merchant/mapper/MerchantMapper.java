package com.example.codexlab.merchant.mapper;

import com.example.codexlab.merchant.dto.MerchantResponse;
import com.example.codexlab.merchant.entity.Merchant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MerchantMapper {

    MerchantResponse toResponse(Merchant merchant);
}
