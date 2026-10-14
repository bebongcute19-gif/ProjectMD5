package com.omnichannel.product.service;

import com.omnichannel.product.dto.ProductDTO;
import com.omnichannel.product.dto.ProductRequest;
import com.omnichannel.product.entity.Category;
import com.omnichannel.product.entity.Product;
import com.omnichannel.product.repository.CategoryRepository;
import com.omnichannel.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<ProductDTO> getAllProducts(String category, String keyword, Boolean flashSale) {
        List<Product> list;
        if (Boolean.TRUE.equals(flashSale)) {
            list = productRepository.findByFlashSaleTrue();
        } else if (category != null && !category.isBlank()) {
            list = productRepository.findByCategorySlug(category);
        } else if (keyword != null && !keyword.isBlank()) {
            list = productRepository.searchByKeyword(keyword);
        } else {
            list = productRepository.findAll();
        }
        return list.stream().map(ProductDTO::new).collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<ProductDTO> getProductsPaged(
            String category, String keyword, Boolean flashSale, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Product> page;
        if (Boolean.TRUE.equals(flashSale)) {
            page = productRepository.findByFlashSaleTrue(pageable);
        } else if (category != null && !category.isBlank()) {
            page = productRepository.findByCategorySlug(category, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            page = productRepository.searchByKeyword(keyword, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }
        return page.map(ProductDTO::new);
    }

    public ProductDTO getProductById(Long id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với id: " + id));
        return new ProductDTO(p);
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional
    public ProductDTO createProduct(ProductRequest req) {
        Product p = new Product();
        p.setName(req.getName());
        p.setSku(req.getSku());
        p.setSlug(req.getSlug() != null ? req.getSlug() : req.getSku().toLowerCase());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        p.setOriginalPrice(req.getOriginalPrice());
        p.setStock(req.getStock() != null ? req.getStock() : 0);
        p.setCategorySlug(req.getCategorySlug());
        p.setImageUrl(req.getImageUrl());
        p.setFlashSale(Boolean.TRUE.equals(req.getFlashSale()));
        p.setFlashSalePrice(req.getFlashSalePrice());

        return new ProductDTO(productRepository.save(p));
    }

    @Transactional
    public ProductDTO updateStock(Long productId, Integer quantityChange) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại: " + productId));
        int newStock = p.getStock() + quantityChange;
        if (newStock < 0) {
            throw new IllegalStateException("Số lượng tồn kho không đủ (Hiện còn: " + p.getStock() + ")");
        }
        p.setStock(newStock);
        return new ProductDTO(productRepository.save(p));
    }

    public Integer getStock(Long productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại"));
        return p.getStock();
    }
}
