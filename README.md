# Fraud Rule Engine Service

Accepts categorized transaction events, evaluates each against a set of configurable fraud rules,
stores the outcome (risk score, flag, and which rules fired), and exposes the results over a REST API
behind an API gateway with OAuth2 authentication and per-client rate limiting.

Java 21 · Spring Boot 4 · Spring Cloud Gateway · Spring Security (OAuth2 resource server) · Keycloak ·
PostgreSQL · Redis · Flyway · Docker Compose · Prometheus · Grafana.

## Architecture

```
Postman / client
   │ 1. POST /realms/fraud-engine/protocol/openid-connect/token  (client credentials)
   ▼
Keycloak :8180 ── issues JWT with scope fraud-api/submit or fraud-api/read
   │
   │ 2. Authorization: Bearer <token>
   ▼
API gateway :8090 (gateway/)          validates JWT + route scope, rate limits per client (Redis),
   │                                  access log with X-Request-Id; only /api/v1 is routed
   ▼
Fraud service :8080 (not published)   validates the JWT again, scores the transaction
   │
   ▼
PostgreSQL
```

Every container runs as a non-root user with no Linux capabilities and `no-new-privileges`; the services have
read-only filesystems, memory limits and health checks. Separate Docker networks mean each container can only
reach what it needs: the gateway can't reach the database, and the fraud service can't reach Redis.

| Client (Keycloak) | Secret (local dev only) | Scope | Allowed |
|---|---|---|---|
| `transaction-submitter` | `submitter-local-secret` | `fraud-api/submit` | `POST /api/v1/transactions` |
| `assessment-reader` | `reader-local-secret` | `fraud-api/read` | `GET /api/v1/assessments/**` |

## Run it

### Prerequisites

- **Docker** (Docker Desktop, or Docker Engine with the Compose v2 plugin). This is all you need to run the stack.
- **JDK 21**, only to run the tests or dev mode. Maven doesn't need to be installed: use the Maven wrapper
  (`./mvnw` on macOS/Linux/Git Bash, `.\mvnw.cmd` in Windows PowerShell or cmd).
- These ports free on your machine: `8090` (gateway), `8180` (Keycloak), `3000` (Grafana), `9090` (Prometheus),
  `9093` (Alertmanager), `5432` (PostgreSQL), `6379` (Redis).

### Start the stack

From the project root:

```bash
docker compose --profile app up --build -d --wait     # Postgres, Keycloak, Redis, service, gateway, monitoring
```

The first run builds both images and downloads the dependencies and base images, so it can take several minutes.
`--wait` returns once every container is healthy. To check:

```bash
docker compose --profile app ps                       # every container should show "healthy"
curl http://localhost:8090/actuator/health/readiness  # {"status":"UP"}
```

If `--wait` fails, look at the logs: `docker compose --profile app logs app gateway keycloak`. A "port is already
allocated" error means another program is using one of the ports above.

### Stop it

```bash
docker compose --profile app down                     # stop, keep the data
docker compose --profile app down --volumes           # stop and delete the database, metrics and dashboard data
```

### Call it from Postman

1. Import `postman/fraud-engine.postman_collection.json`.
2. Run **1. Get tokens → Get submitter token** and **Get reader token**. Each calls the Keycloak token URL
   (`http://localhost:8180/realms/fraud-engine/protocol/openid-connect/token`) and stores the access token.
3. Run the requests in **2. Fraud API**, which send the token as `Authorization: Bearer ...`.
4. **3. Security checks** shows the gateway rejecting a missing token (401) and the wrong scope (403).

Tokens last 15 minutes. The whole collection also runs from the command line:
`npx newman run postman/fraud-engine.postman_collection.json`.

### From the command line

Needs a bash shell (on Windows, Git Bash or WSL) and [`jq`](https://jqlang.org/).

```bash
TOKEN=$(curl -s -u transaction-submitter:submitter-local-secret -d grant_type=client_credentials \
  http://localhost:8180/realms/fraud-engine/protocol/openid-connect/token | jq -r .access_token)
curl -X POST localhost:8090/api/v1/transactions -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{ ...see the example below... }'
```

### Develop and test

Run these from the project root (`gateway/` uses the root's Maven wrapper). On Windows, replace `./mvnw` with
`.\mvnw.cmd`.

```bash
./mvnw verify                               # build + fraud service unit tests (no Docker needed)
./mvnw -f gateway/pom.xml verify            # build + gateway unit tests
./mvnw spring-boot:run                      # dev mode: starts Postgres/Keycloak/Redis from compose.yaml, service on :8080
./mvnw -f gateway/pom.xml spring-boot:run   # optional, in a second terminal: gateway on :8090, routing to :8080
```

Dev mode needs Docker running (the service starts the infrastructure containers itself). If macOS/Linux says
`permission denied` for `./mvnw` (the executable bit can get lost when a project is copied or unzipped), run
`chmod +x mvnw` once.

Configuration is via environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_ISSUER_URI`,
`RATE_LIMIT_PER_SECOND`, `RATE_LIMIT_BURST`, ...); see `compose.yaml` and each `application.yml`.

### Logs

Both apps log to stdout only (no log files), one JSON object per line in
[Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/index.html) format. Code logs with key-values
(`log.atInfo().addKeyValue("transactionId", id).log("Transaction assessed")`), and each key becomes a JSON field.

Logs never contain tokens, secrets or request bodies. Account ids are masked to their last 4 characters
(`****7890`), the gateway logs the path without the query string, and PostgreSQL error details (which can include
a whole failing row) are turned off with `logServerErrorDetail=false`.

```bash
docker compose logs -f app                  # fraud service (one "Transaction assessed" line per new assessment)
docker compose logs -f gateway              # gateway access log (one "Request completed" line per request)
docker compose logs -f app gateway          # both, interleaved
docker compose logs --tail 200 app          # last 200 lines only
docker compose logs -f keycloak             # also postgres, redis
```

In dev mode (`./mvnw spring-boot:run`) the logs print in that terminal. To find a request in the gateway log,
search for the `X-Request-Id` response header value (the `requestId` field). To read just the messages, pipe
through `jq`, for example `docker compose logs --no-log-prefix app | jq -r .message`.

### Metrics and dashboard

`docker compose --profile app up` also starts Prometheus and Grafana.

- **Grafana:** <http://localhost:3000>, `admin` / `admin`. The **Fraud Rule Engine** dashboard is the home page.
- **Prometheus:** <http://localhost:9090> (bound to localhost only; it has no login).

Both apps expose `/actuator/prometheus`, which requires a token with the `fraud-api/metrics` scope (the gateway
port is public). Prometheus gets one from Keycloak as the `metrics-scraper` client.

| Metric | What it tells you |
|---|---|
| `fraud_submissions_total{outcome}` | `assessed` (new), `duplicate` (already assessed) or `account_busy` (429) |
| `fraud_assessments_total{flagged}` | New assessments by verdict, for the flag rate |
| `fraud_rule_hits_total{rule}` | How often each rule triggers |
| `fraud_risk_score` | Histogram of risk scores |
| `fraud_evaluation_seconds` | Histogram of evaluation time, including waiting for the account lock |
| `http_server_requests_seconds`, `spring_cloud_gateway_requests_seconds` | Request rate, status and latency (histograms, so p50/p95/p99 work) |
| `hikaricp_connections_*`, `jvm_*`, `process_cpu_usage` | Database pool, memory, GC and CPU |

Tags never include account or transaction ids (customer data, and one time series each). Every metric has an
`application` label (`fraud-rule-engine` or `fraud-api-gateway`).

### Alerts

Prometheus evaluates the rules in `infra/prometheus/alerts.yml` and sends firing alerts to Alertmanager
(<http://localhost:9093>, localhost only). They also appear in Grafana under **Alerting**.

| Alert | Severity | Fires when |
|---|---|---|
| `FraudServiceDown` | critical | an app can't be scraped for 1 minute |
| `GatewayHighErrorRate` | critical | over 5% of gateway responses are 5xx for 5 minutes |
| `GatewayHighLatency` | warning | gateway p95 for successful requests is over 1s for 10 minutes |
| `AccountBusySpike` | warning | more than 10 submissions rejected as account busy in 5 minutes |
| `FlagRateUnusual` | warning | over 30% of new assessments are flagged for 15 minutes (with at least 50 assessments) |
| `EdgeAuthRejectionsHigh` | warning | more than 1 request/s rejected with 401/403 for 10 minutes |
| `DatabaseConnectionsWaiting` | warning | requests keep waiting for a database connection for 5 minutes |
| `JvmHeapHigh` | warning | old-generation heap is over 90% full for 10 minutes |

While `FraudServiceDown` is firing, warnings are suppressed (they're usually its symptoms). Alertmanager has no
notification channel yet: add `slack_configs`, `email_configs` or similar to the receiver in
`infra/alertmanager/alertmanager.yml`. The rules have unit tests (`infra/prometheus/alerts.test.yml`), run in CI:

```bash
docker run --rm -v "$PWD/infra/prometheus:/p:ro" --entrypoint promtool prom/prometheus:v3.15.0 test rules /p/alerts.test.yml
```

## API

Through the gateway at `http://localhost:8090`; every `/api/v1` call needs a Bearer token with the scope shown.

| Method | Path | Scope | Description |
|---|---|---|---|
| `POST` | `/api/v1/transactions` | `fraud-api/submit` | Submit a transaction. Returns the assessment: `201` if new, `200` if that `transactionId` was already assessed (idempotent), `429` with `Retry-After` if too many requests for the same account are already queued (safe to resubmit). |
| `GET` | `/api/v1/assessments/{transactionId}` | `fraud-api/read` | Fetch one assessment (`404` if unknown). |
| `GET` | `/api/v1/assessments` | `fraud-api/read` | Search, newest first. Optional params: `flagged`, `accountId`, `from` (inclusive), `to` (exclusive) as ISO-8601 instants, `page` (default 0), `size` (default 20, max 100). |
| `GET` | `/actuator/health/liveness`, `/readiness` | none | Gateway health. The service's own actuator endpoints are not routed. |

Errors use RFC 9457 problem responses (`application/problem+json`). The gateway adds `401` (missing/invalid
token), `403` (wrong scope or unrouted path) and `429` (rate limited, with `X-RateLimit-*` headers). Every response
carries an `X-Request-Id`.

### API docs (Swagger UI)

Open <http://localhost:8090/swagger-ui.html>. You're sent to the Keycloak login page first: sign in as
`docs-viewer` / `docs-viewer-local` (local realm only), and you're returned to the Swagger UI.

To call the API from **Try it out**, click **Authorize** and enter a client from the realm, e.g.
`assessment-reader` / `reader-local-secret` with the `fraud-api/read` scope (or `transaction-submitter` /
`submitter-local-secret` with `fraud-api/submit`). Swagger UI fetches the token from Keycloak itself.

- The Swagger UI is served by the gateway; the spec is generated by the service at `/v3/api-docs` and reaches the
  UI through the gateway route `/v3/api-docs/fraud-engine`, which forwards the logged-in user's token. The
  service also requires a valid token for the spec.
- The login uses a session cookie, but only on the docs paths. The API itself stays token-only: a logged-in
  browser session can't call it without a Bearer token.

### Example

```bash
curl -X POST localhost:8090/api/v1/transactions -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{
  "transactionId": "txn-1001",
  "accountId": "acc-42",
  "amount": 15000.00,
  "currency": "ZAR",
  "category": "GAMBLING",
  "merchant": "Casino Royale",
  "country": "ZA",
  "timestamp": "2026-03-01T02:00:00Z"
}'

curl 'localhost:8090/api/v1/assessments?flagged=true&accountId=acc-42' -H "Authorization: Bearer $READER_TOKEN"
```

Request fields: `category` is one of `GROCERIES, RESTAURANTS, TRAVEL, ENTERTAINMENT, UTILITIES, HEALTH, RETAIL,
GAMBLING, CRYPTO, TRANSFER, ATM_WITHDRAWAL, OTHER`; `currency` is ISO 4217 (`ZAR`); `country` is ISO 3166 alpha-2
(`ZA`); `amount` must be > 0 with at most 4 decimal places.

## Fraud rules

Each rule that fires contributes a score. A transaction is **flagged** when the total reaches
`fraud.flag-threshold` (default 50), so no single low-weight rule flags on its own. Every triggered
rule is stored with a human-readable reason.

| Rule | Fires when | Default score |
|---|---|---|
| `HIGH_AMOUNT` | amount ≥ 10,000 | 40 |
| `RISKY_CATEGORY` | category is `GAMBLING`/`CRYPTO` and amount ≥ 1,000 | 30 |
| `VELOCITY` | more than 5 transactions on the account within 10 minutes (ending at this transaction's timestamp) | 35 |
| `HIGH_RISK_COUNTRY` | country is `KP`, `IR` or `SY` | 30 |
| `ODD_HOURS` | amount ≥ 2,000 between 00:00 and 05:00 UTC | 20 |

All thresholds, scores, category/country lists and per-rule `enabled` switches live under `fraud.*` in
`src/main/resources/application.yml`, and can be overridden with environment variables using Spring's relaxed
binding, e.g. `-e FRAUD_FLAG_THRESHOLD=70 -e FRAUD_RULES_HIGH_AMOUNT_THRESHOLD=5000`. Invalid configuration
fails fast at startup.

**Adding a rule:** implement `FraudRule` (`rules/`) and annotate it `@Component`. The evaluation service picks up
all `FraudRule` beans automatically.

## CI

GitHub Actions (`.github/workflows/ci.yml`), free for public repositories:

- unit tests for both services, and unit tests for the Prometheus alert rules
- image build + Trivy vulnerability scan for both images (fails on fixable HIGH/CRITICAL)
- Trivy misconfiguration scan of the Dockerfiles
- **end-to-end**: starts the whole Compose stack and runs the Postman collection with Newman, using real
  Keycloak tokens through the gateway

Dependabot keeps Maven, Docker, Compose and Actions dependencies up to date. Actions are pinned to commit SHAs.

## Design notes

- **Layers:** `api` (HTTP/DTOs/validation) → `service` (orchestration) → `rules` (pure per-rule logic) → `repository`.
- **Idempotency:** `transactionId` is the primary key. Replays return the stored result without re-scoring, and
  concurrent duplicates are resolved via the unique key. A replay with a *different* payload for the same id
  returns the original assessment; the new payload is ignored.
- **Velocity** is computed from stored assessments, so it relies on every transaction being submitted through this
  service. It uses event time (not arrival time), so replaying historical events behaves consistently.
  Requests for the same account are evaluated one at a time (a PostgreSQL advisory lock per account, held until
  the transaction commits), so a concurrent burst can't slip past the limit. Different accounts don't block each
  other. A request that waits longer than `fraud.account-lock-timeout` (default 1s) fails fast with `429` rather
  than holding a database connection while it queues, so a flood on one account can't starve the others.
- **Storage:** PostgreSQL. The schema is managed by Flyway (`db/migration`), and Hibernate only validates it.
- **Security:** OAuth2 client credentials (Keycloak). The gateway enforces token + scope at the edge and the service
  enforces them again, so it stays protected even if something bypasses the gateway. The API docs use a separate
  Keycloak browser login (authorization code flow, client `fraud-docs`), since a browser can't send a Bearer token
  when opening a page.
- **Testing:** unit tests only (JUnit, Mockito, AssertJ; no Spring context or Docker). They cover each rule's
  boundaries, scoring and flagging, idempotency, the account-lock timeout, request validation, the API mapping and
  error responses, metrics, and the gateway's access log, request ids and client ids. What unit tests can't check
  (SQL and the account lock against a real database, security configuration, routing, the Swagger login) is only
  covered by the end-to-end CI job (the whole Compose stack plus the Postman collection).

## Known limitations

- Keycloak runs in development mode (embedded database, realm re-imported on start) and the client secrets are
  committed for local use; a real deployment needs production-mode Keycloak and secrets from a vault.
- Traffic between containers is plain HTTP on private Docker networks; there is no TLS inside the stack.
- One instance of each service: Compose restarts failed containers but doesn't do rolling, zero-downtime
  deploys. If Redis is unavailable, the gateway's rate limiter fails open (requests are allowed).
- Swagger UI login sessions are kept in the gateway's memory, so they don't survive a restart or work across
  several gateway replicas (that needs a shared session store such as Spring Session with Redis). Any realm user
  can read the docs; a real deployment would restrict it to a role.
- Alertmanager doesn't notify anyone until a receiver (Slack, email, PagerDuty, ...) is configured. Grafana uses a
  local `admin` account; Prometheus and Alertmanager have no login (they're only bound to localhost).
- "Try it out" asks for a client id and secret in the browser. That's fine for local clients, but production
  docs should get tokens for a user (authorization code with PKCE) rather than handing out client secrets.
- Velocity is only checked when a transaction arrives. A late transaction with an earlier timestamp counts toward
  transactions after it, but transactions that were already assessed are not re-scored.
