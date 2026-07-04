package com.example.similarproducts;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SimilarProductsService {

	private final ProductClient client;
	private final Duration detailTimeout;

	public SimilarProductsService(ProductClient client,
			@Value("${detail.timeout:2s}") Duration detailTimeout) {
		this.client = client;
		this.detailTimeout = detailTimeout;
	}

	public Mono<List<ProductDetail>> similarProducts(String productId) {
		return client.similarIds(productId)
				// main product unknown -> 404 (a missing *similar* product is handled below)
				.onErrorMap(NotFound.class, e -> new ResponseStatusException(HttpStatus.NOT_FOUND))
				.flatMapMany(Flux::fromIterable)
				// fetch details concurrently, keep similarity order
				.flatMapSequential(this::detailOrSkip)
				.collectList();
	}

	// Resilience: drop any similar product whose detail 404s, errors, or is too slow,
	// so one bad dependency call never sinks the whole response.
	private Mono<ProductDetail> detailOrSkip(String id) {
		return client.detail(id)
				.timeout(detailTimeout)
				.onErrorResume(e -> Mono.empty());
	}
}
