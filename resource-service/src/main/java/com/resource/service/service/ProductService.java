package com.resource.service.service;

import com.resource.service.dto.ProductResponse;
import com.resource.service.exception.ResourceNotFoundException;
import com.resource.service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(p -> new ProductResponse(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        p.getPrice(),
                        p.getStock(),
                        p.getCreatedAt()))
                .toList();
    }

    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .map(p -> new ProductResponse(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        p.getPrice(),
                        p.getStock(),
                        p.getCreatedAt()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }
}
