package com.example.similarproducts;

import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

// Covers the wiring the service tests can't: route -> JSON body, and
// ResponseStatusException(404) actually surfacing as HTTP 404.
@WebFluxTest(SimilarProductsController.class)
class SimilarProductsControllerTest {

	@Autowired
	WebTestClient web;

	@MockitoBean
	SimilarProductsService service;

	@Test
	void returnsDetailsAsJsonArray() {
		when(service.similarProducts("1")).thenReturn(Mono.just(List.of(
				new ProductDetail("2", "Dress", 19.99, true),
				new ProductDetail("3", "Blazer", 29.99, false))));

		web.get().uri("/product/1/similar").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].id").isEqualTo("2")
				.jsonPath("$[1].name").isEqualTo("Blazer")
				.jsonPath("$[1].availability").isEqualTo(false);
	}

	@Test
	void propagatesNotFoundAsHttp404() {
		when(service.similarProducts("404"))
				.thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));

		web.get().uri("/product/404/similar").exchange()
				.expectStatus().isNotFound();
	}
}
