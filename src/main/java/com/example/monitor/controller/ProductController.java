package com.example.monitor.controller;

import com.example.monitor.exception.ResourceNotFoundException;
import com.example.monitor.model.Product;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ConcurrentHashMap<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @PostConstruct
    public void init() {
        // Pre-populate with some sample products
        saveProduct(new Product(null, "Developer Mechanical Keyboard", "An RGB mechanical keyboard with blue switches.", 99.99, "KEY-RGB-001"));
        saveProduct(new Product(null, "Ergonomic Office Chair", "A highly adjustable mesh chair for long coding sessions.", 249.99, "CHR-ERG-002"));
        saveProduct(new Product(null, "UltraWide Monitor 34-inch", "34-inch curved ultra-wide monitor with 144Hz refresh rate.", 399.99, "MON-UW-003"));
    }

    private Product saveProduct(Product product) {
        if (product.getId() == null) {
            product.setId(idGenerator.getAndIncrement());
        }
        products.put(product.getId(), product);
        return product;
    }

    @GetMapping
    public Collection<Product> getAllProducts() {
        return products.values();
    }

    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        Product product = products.get(id);
        if (product == null) {
            throw new ResourceNotFoundException("Product with ID " + id + " not found");
        }
        return product;
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be empty");
        }
        if (product.getPrice() < 0) {
            throw new IllegalArgumentException("Product price cannot be negative");
        }
        Product saved = saveProduct(product);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Long id, @RequestBody Product productUpdates) {
        Product existing = products.get(id);
        if (existing == null) {
            throw new ResourceNotFoundException("Product with ID " + id + " not found");
        }
        if (productUpdates.getName() == null || productUpdates.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be empty");
        }
        if (productUpdates.getPrice() < 0) {
            throw new IllegalArgumentException("Product price cannot be negative");
        }

        existing.setName(productUpdates.getName());
        existing.setDescription(productUpdates.getDescription());
        existing.setPrice(productUpdates.getPrice());
        existing.setSku(productUpdates.getSku());

        products.put(id, existing);
        return existing;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Long id) {
        Product removed = products.remove(id);
        if (removed == null) {
            throw new ResourceNotFoundException("Product with ID " + id + " not found");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Product deleted successfully");
        response.put("id", id);
        response.put("status", "success");
        return response;
    }
}
