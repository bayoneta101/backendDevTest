package com.example.similarproducts;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class SimilarProductsController {

	private final SimilarProductsService service;

	public SimilarProductsController(SimilarProductsService service) {
		this.service = service;
	}

	@GetMapping("/product/{productId}/similar")
	public Mono<List<ProductDetail>> similar(@PathVariable String productId) {
		return service.similarProducts(productId);
	}
}
