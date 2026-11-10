package com.example.codexlab.merchant.service;

import com.example.codexlab.common.exception.ResourceNotFoundException;
import com.example.codexlab.merchant.dto.CreateMerchantRequest;
import com.example.codexlab.merchant.dto.MerchantResponse;
import com.example.codexlab.merchant.entity.Merchant;
import com.example.codexlab.merchant.mapper.MerchantMapper;
import com.example.codexlab.merchant.repository.MerchantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantMapper merchantMapper;

    @Transactional
    public MerchantResponse create(CreateMerchantRequest request) {
        Merchant merchant = new Merchant(request.name(), request.email());
        return merchantMapper.toResponse(merchantRepository.save(merchant));
    }

    @Transactional(readOnly = true)
    public MerchantResponse getById(Long id) {
        return merchantMapper.toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<MerchantResponse> list() {
        return merchantRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(merchantMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Merchant findEntity(Long id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant %d was not found".formatted(id)));
    }
}
