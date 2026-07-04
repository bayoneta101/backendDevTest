package com.example.similarproducts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SimilarProductsServiceTest {

	private static WebClientResponseException status(int code) {
		return WebClientResponseException.create(code, "err", HttpHeaders.EMPTY, new byte[0], null);
	}

	private static SimilarProductsService service(ProductClient client, Duration timeout) {
		return new SimilarProductsService(client, timeout, Duration.ofMinutes(1), Duration.ofSeconds(10));
	}

	@Test
	void skipsFailingAndSlowDetails() {
		ProductClient client = mock(ProductClient.class);
		when(client.similarIds("1")).thenReturn(Mono.just(List.of("2", "3", "4")));
		when(client.detail("2")).thenReturn(Mono.just(new ProductDetail("2", "Dress", 1, true)));
		when(client.detail("3")).thenReturn(Mono.error(status(500)));                                  // errors -> skip
		when(client.detail("4")).thenReturn(Mono.just(new ProductDetail("4", "Boots", 2, true))
				.delayElement(Duration.ofMillis(300)));                                                // too slow -> skip

		StepVerifier.create(service(client, Duration.ofMillis(80)).similarProducts("1"))
				.assertNext(list -> assertEquals(List.of("2"), list.stream().map(ProductDetail::id).toList()))
				.verifyComplete();
	}

	@Test
	void mapsMainProductNotFoundTo404() {
		ProductClient client = mock(ProductClient.class);
		when(client.similarIds("404")).thenReturn(Mono.error(status(404)));

		StepVerifier.create(service(client, Duration.ofSeconds(2)).similarProducts("404"))
				.expectErrorSatisfies(e -> {
					var rse = assertInstanceOf(ResponseStatusException.class, e);
					assertEquals(HttpStatus.NOT_FOUND, rse.getStatusCode());
				})
				.verify();
	}

	@Test
	void cachesSimilarIdsAndDetails() {
		ProductClient client = mock(ProductClient.class);
		when(client.similarIds("1")).thenReturn(Mono.just(List.of("2")));
		when(client.detail("2")).thenReturn(Mono.just(new ProductDetail("2", "Dress", 1, true)));

		var service = service(client, Duration.ofSeconds(2));
		service.similarProducts("1").block();
		service.similarProducts("1").block();

		verify(client, times(1)).similarIds("1"); // second call served from cache
		verify(client, times(1)).detail("2");
	}
}
