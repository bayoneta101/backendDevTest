package com.example.similarproducts;

import java.util.List;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SimilarProductsService {

	private final ProductClient client;

	public SimilarProductsService(ProductClient client) {
		this.client = client;
	}

	public Mono<List<ProductDetail>> similarProducts(String productId) {
		return client.similarIds(productId)
				.flatMapMany(Flux::fromIterable)
				// flatMapSequential: fetch details concurrently, keep similarity order
				.flatMapSequential(client::detail)
				.collectList();
	}
}
