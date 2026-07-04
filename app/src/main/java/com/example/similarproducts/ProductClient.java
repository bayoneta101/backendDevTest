package com.example.similarproducts;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/** Wraps the existing mock APIs (similarids + product detail). */
@Component
public class ProductClient {

	private static final ParameterizedTypeReference<List<String>> ID_LIST = new ParameterizedTypeReference<>() {
	};

	private final WebClient client;

	public ProductClient(WebClient.Builder builder,
			@Value("${existing-api.base-url:http://localhost:3001}") String baseUrl) {
		this.client = builder.baseUrl(baseUrl).build();
	}

	public Mono<List<String>> similarIds(String productId) {
		return client.get().uri("/product/{id}/similarids", productId)
				.retrieve()
				.bodyToMono(ID_LIST);
	}

	public Mono<ProductDetail> detail(String productId) {
		return client.get().uri("/product/{id}", productId)
				.retrieve()
				.bodyToMono(ProductDetail.class);
	}
}
