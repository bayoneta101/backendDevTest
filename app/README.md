# Similar Products API

Spring Boot **WebFlux** service exposing `GET /product/{id}/similar` on port `5000`.
It fetches the similar-product ids for a product, then their details, and returns
the aggregated list ordered by similarity.

## Run the tests

From the repo root:

```bash
./run-tests.sh
```

It builds the app, runs it as a container on the mock's network, executes the
full k6 suite (all 5 scenarios), and prints the summary. Charts:
<http://localhost:3000/d/Le2Ku9NMk/k6-performance-test>

## Run the app on its own

```bash
cd app
./mvnw spring-boot:run          # needs the mocks up: docker-compose up -d simulado
curl http://localhost:5000/product/1/similar
```

Upstream base URL is configurable via `existing-api.base-url` (default
`http://localhost:3001`).

## Features

- **Reactive fan-out** — details are fetched concurrently with `flatMapSequential`,
  which keeps them in similarity order.
- **Non-blocking I/O** — WebClient + Netty, sized for the 200-VU load test.
- **Configurable upstream** — base URL via property/env, so it runs the same on
  host or in a container.
- **Resilient detail fetch** — each detail call has a timeout (`detail.timeout`,
  default `2s`) and is dropped on 404 / 500 / timeout, so one bad or slow
  dependency never sinks the whole response.
- **404 passthrough** — a missing *main* product returns 404; a missing
  *similar* product is just left out of the list.

### Next (in progress)
- Fast-fail for repeatedly-slow ids (circuit breaker / negative cache) to remove
  the timeout wall on the slow scenarios.
- Caffeine cache on the upstream calls (the load test hammers a handful of ids).
