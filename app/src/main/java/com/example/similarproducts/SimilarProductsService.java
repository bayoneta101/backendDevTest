package com.example.similarproducts;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;
import org.springframework.web.server.ResponseStatusException;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SimilarProductsService {

	private static final int MAX_ENTRIES = 10_000;

	private final AsyncLoadingCache<String, List<String>> similarIdsCache;
	private final AsyncLoadingCache<String, Optional<ProductDetail>> detailCache;

	public SimilarProductsService(ProductClient client,
			@Value("${upstream.timeout:2s}") Duration timeout,
			@Value("${cache.ttl:60s}") Duration ttl,
			@Value("${cache.negative-ttl:10s}") Duration negativeTtl) {

		// A timeout here too: a hung similar-ids call must not hang the request forever.
		this.similarIdsCache = Caffeine.newBuilder()
				.maximumSize(MAX_ENTRIES)
				.expireAfterWrite(ttl)
				.buildAsync((id, ex) -> client.similarIds(id).timeout(timeout).toFuture());

		this.detailCache = Caffeine.newBuilder()
				.maximumSize(MAX_ENTRIES)
				// Present details live for "ttl". A miss (404 / 500 / timeout) is stored as
				// empty for a short "negativeTtl", so a slow or broken id fails fast on the
				// next request instead of hitting the timeout wall again, then recovers.
				.expireAfter(Expiry.creating(
						(String id, Optional<ProductDetail> v) -> v.isPresent() ? ttl : negativeTtl))
				.buildAsync((id, ex) -> client.detail(id)
						.timeout(timeout)
						.map(Optional::of)
						.onErrorReturn(Optional.empty())
						.toFuture());
	}

	public Mono<List<ProductDetail>> similarProducts(String productId) {
		// suppressCancel=true: a client disconnect must not cancel the shared cache load.
		return Mono.fromFuture(() -> similarIdsCache.get(productId), true)
				.onErrorMap(SimilarProductsService::isNotFound,
						e -> new ResponseStatusException(HttpStatus.NOT_FOUND))
				.flatMapMany(Flux::fromIterable)
				.flatMapSequential(this::detail) // concurrent, similarity order kept
				.collectList();
	}

	private Mono<ProductDetail> detail(String id) {
		return Mono.fromFuture(() -> detailCache.get(id), true)
				.flatMap(Mono::justOrEmpty); // empty Optional -> product skipped
	}

	// The main product's similar-ids call 404'd -> product unknown. Walk the cause chain
	// because the error crosses a CompletableFuture and may be wrapped.
	private static boolean isNotFound(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof NotFound) {
				return true;
			}
		}
		return false;
	}
}
