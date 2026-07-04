#!/usr/bin/env bash
# Runs the full k6 evaluation suite (all 5 scenarios) and captures the summary.
#
# The app runs as a container on the compose network, aliased as the hostname
# the given k6 script targets (host.docker.internal), and reaches the mock by
# service name. Everything is container-to-container, which is the only path
# that works on Linux where the docker->host gateway is firewalled. On
# Mac/Windows the README's host-based flow works too; this just makes the test
# reproducible here without touching the given docker-compose.yaml.
set -euo pipefail
cd "$(dirname "$0")"

NET="$(basename "$PWD" | tr '[:upper:]' '[:lower:]')_default"
K6_LOG="k6-results.log"

# 1. Mocks + metrics infra (also creates the compose network).
docker-compose up -d simulado influxdb grafana

# 2. Build the app jar + image.
echo ">> Building app..."
(cd app && ./mvnw -q -DskipTests package)
docker build -q -t similarproducts:test app >/dev/null

# 3. Run the app on the compose network as host.docker.internal.
cleanup() { docker rm -f sp-app >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker rm -f sp-app >/dev/null 2>&1 || true
docker run -d --name sp-app --network "$NET" --network-alias host.docker.internal \
  similarproducts:test --existing-api.base-url=http://simulado >/dev/null

echo -n ">> Waiting for app "
for _ in $(seq 1 60); do
  docker logs sp-app 2>&1 | grep -q "Netty started on port" && break
  echo -n "."; sleep 1
done
if ! docker run --rm --network "$NET" curlimages/curl:latest \
     -sf -o /dev/null --max-time 5 http://host.docker.internal:5000/product/1/similar; then
  echo " UNREACHABLE. App logs:"; docker logs --tail 20 sp-app; exit 1
fi
echo " up"

# 4. Run k6 — no extra_hosts, so host.docker.internal resolves to the app alias.
echo ">> Running k6..."
docker run --rm --network "$NET" -v "$PWD/shared/k6:/scripts" \
  -e K6_OUT=influxdb=http://influxdb:8086/k6 \
  loadimpact/k6:0.28.0 run /scripts/test.js 2>&1 | tee "$K6_LOG"

echo ">> Done. Summary saved to $K6_LOG"
echo ">> Charts: http://localhost:3000/d/Le2Ku9NMk/k6-performance-test"
