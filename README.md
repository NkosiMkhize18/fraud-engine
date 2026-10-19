# Fraud Rule Engine Service

Accepts categorized transaction events, evaluates each against a set of configurable fraud rules,
stores the outcome (risk score, flag, and which rules fired), and exposes the results over a REST API
behind an API gateway with OAuth2 authentication and per-client rate limiting.

Java 21 · Spring Boot 4 · Spring Cloud Gateway · Spring Security (OAuth2 resource server) · Keycloak ·
PostgreSQL · Redis · Flyway · Testcontainers · Docker Compose · Prometheus · Grafana.

Note that in a real world production I would have the gateway, monitoring and supporting services in their own repos
this is just to consolidate the review.

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

| Client (Keycloak) | Secret (from `.env`) | Scope | Allowed |
|---|---|---|---|
| `transaction-submitter` | `TRANSACTION_SUBMITTER_SECRET` | `fraud-api/submit` | `POST /api/v1/transactions` |
| `assessment-reader` | `ASSESSMENT_READER_SECRET` | `fraud-api/read` | `GET /api/v1/assessments/**` |

## Run it

No secrets are committed to this repo. Before the first run, create the git-ignored `.env` with random local values:

```bash
./scripts/init-env.sh          # macOS, Linux, Git Bash, WSL
```

```powershell
.\scripts\init-env.ps1         # Windows PowerShell
```

Both scripts leave an existing `.env` alone. To set your own values instead, edit `.env` directly. Use letters and
digits only, since characters like `|`, `&` or `$` break the bootstrap step.

Compose refuses to start until every value in `.env` is set. The `bootstrap` service renders the Keycloak realm and
the Prometheus secret from these values each time the stack starts.

### Prerequisites

- **Docker** (Docker Desktop, or Docker Engine with the Compose v2 plugin). This is all you need to run the stack.
- **JDK 21**, only to run the tests or dev mode. Maven doesn't need to be installed: use the Maven wrapper
  (`./mvnw` on macOS/Linux/Git Bash, `.\mvnw.cmd` in Windows PowerShell or cmd).
- These ports free on your machine: `8090` (gateway), `8180` (Keycloak), `3000` (Grafana), `9090` (Prometheus),
  `9093` (Alertmanager), `5432` (PostgreSQL), `6379` (Redis). Everything except the gateway is bound to `127.0.0.1`.

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

### Changing secrets in `.env`

Keycloak's realm and client secrets, and the Prometheus scraper secret, are read from `.env` each time the stack starts.
After changing one of them, restart the stack. Otherwise token requests fail with `unauthorized_client`. The Keycloak
admin password (`KEYCLOAK_ADMIN_PASSWORD`) is read the same way. Grafana's admin password (`GRAFANA_ADMIN_PASSWORD`)
applies only when its data volume is first created, so change it in Grafana's own settings after that, or delete the
volume (`down --volumes`).

```bash
docker compose --profile app down                     # removes the containers, keeps the data
docker compose --profile app up -d --wait
```

`DB_PASSWORD` is the exception. PostgreSQL applies its password only when its data volume is first created, so
changing `DB_PASSWORD` needs that volume deleted. This also deletes the database:

```bash
docker compose --profile app down --volumes
docker compose --profile app up -d --wait
```

### Stop it

```bash
docker compose --profile app down                     # stop, keep the data
docker compose --profile app down --volumes           # stop and delete the database, metrics and dashboard data
```

### Windows (PowerShell)

The Docker commands above work unchanged in PowerShell. The equivalents for the other steps:

```powershell
.\scripts\init-env.ps1                                     # create .env (once)
docker compose --profile app up --build -d --wait          # start the stack
curl.exe http://localhost:8090/actuator/health/readiness   # curl.exe, not curl: in Windows PowerShell "curl" is an alias for Invoke-WebRequest
docker compose --profile app down                          # stop, keep the data
.\mvnw.cmd verify                                          # tests
.\mvnw.cmd -f gateway\pom.xml verify                       # gateway tests
.\mvnw.cmd spring-boot:run                                 # dev mode
```

The `bash` examples in this README (the command-line token request and the Newman run) need Git Bash or WSL on
Windows. Postman works without either.

### Call it from Postman

1. Import `postman/fraud-engine.postman_collection.json`.
2. In the collection's **Variables** tab, set `submitterClientSecret` to the value of `TRANSACTION_SUBMITTER_SECRET`
   and `readerClientSecret` to the value of `ASSESSMENT_READER_SECRET` from your `.env`. They are empty in the file on purpose.
3. Run **1. Get tokens → Get submitter token** and **Get reader token**. Each calls the Keycloak token URL
   (`http://localhost:8180/realms/fraud-engine/protocol/openid-connect/token`) and stores the access token.
4. Run the requests in **2. Fraud API**, which send the token as `Authorization: Bearer ...`.
5. **3. Security checks** shows the gateway rejecting a missing token (401) and the wrong scope (403).

**Submit transaction** generates a new `transactionId` and timestamp once an hour, because the service rejects
timestamps older than 24 hours. Resending the request within that hour replays it and gets `200`; after that hour, a
new transaction is created.

Tokens last 15 minutes. The whole collection also runs from the command line:
`set -a && . ./.env && set +a && npx newman run postman/fraud-engine.postman_collection.json --env-var "submitterClientSecret=$TRANSACTION_SUBMITTER_SECRET" --env-var "readerClientSecret=$ASSESSMENT_READER_SECRET"`.

### From the command line

Needs a bash shell (on Windows, Git Bash or WSL) and [`jq`](https://jqlang.org/).

```bash
set -a && . ./.env && set +a
TOKEN=$(curl -s -u "transaction-submitter:$TRANSACTION_SUBMITTER_SECRET" -d grant_type=client_credentials \
  http://localhost:8180/realms/fraud-engine/protocol/openid-connect/token | jq -r .access_token)
curl -X POST localhost:8090/api/v1/transactions -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{ ...see the example below... }'
```

### Develop and test

Run these from the project root (`gateway/` uses the root's Maven wrapper). On Windows, replace `./mvnw` with
`.\mvnw.cmd`.

```bash
./mvnw verify                               # build + fraud service unit tests
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
| `fraud_submissions_total{outcome}` | `assessed` (new), `duplicate` (identical replay, `200`), `conflict` (reused id with different details, `409`) or `account_busy` (`429`) |
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
| `POST` | `/api/v1/transactions` | `fraud-api/submit` | Submit a transaction. Returns the assessment: `201` if new; `200` if the identical submission was already assessed (idempotent replay); `400` for a timestamp outside the accepted window or a currency with no exchange rate; `409` if that `transactionId` was already submitted with different details; `429` with `Retry-After` if too many requests for the same account are already queued (safe to resubmit); `503` with `Retry-After` if the database connection pool is busy (nothing was stored, safe to resubmit). |
| `GET` | `/api/v1/assessments/{transactionId}` | `fraud-api/read` | Fetch one assessment (`404` if unknown). |
| `GET` | `/api/v1/assessments` | `fraud-api/read` | Search, newest first. Optional params: `flagged`, `accountId`, `from` (inclusive), `to` (exclusive) as ISO-8601 offset date-times (e.g. `2026-03-01T00:00:00Z`), `page` (default 0, max 10,000), `size` (default 20, max 100). |
| `GET` | `/actuator/health/liveness`, `/readiness` | none | Gateway health. The service's own actuator endpoints are not routed. |

Errors use RFC 9457 problem responses (`application/problem+json`). The gateway adds `401` (missing/invalid
token), `403` (wrong scope or unrouted path) and `429` (rate limited, with `X-RateLimit-*` headers). The service adds
`400` (invalid request, timestamp outside the window, or a currency with no exchange rate), `404` (unknown assessment),
`409` (transaction id reused with different details), `429` (account busy, with `Retry-After`) and `503` (database
busy, with `Retry-After`). Every response carries an `X-Request-Id`.

### API docs (Swagger UI)

Open <http://localhost:8090/swagger-ui.html>. You're sent to the Keycloak login page first: sign in as
`docs-viewer` with the `DOCS_VIEWER_PASSWORD` from `.env` (local realm only), and you're returned to the Swagger UI.

To call the API from **Try it out**, click **Authorize** and enter a client from the realm, e.g.
`assessment-reader` with `ASSESSMENT_READER_SECRET` and the `fraud-api/read` scope (or `transaction-submitter` with
`TRANSACTION_SUBMITTER_SECRET` and `fraud-api/submit`). Swagger UI fetches the token from Keycloak itself.

- The Swagger UI is served by the gateway; the spec is generated by the service at `/v3/api-docs` and reaches the
  UI through the gateway route `/v3/api-docs/fraud-engine`, which forwards the logged-in user's token. The
  service also requires a valid token for the spec.
- The login uses a session cookie, but only on the docs paths. The API itself stays token-only: a logged-in
  browser session can't call it without a Bearer token.

### Example

```bash
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)   # must be within 24 hours in the past and 5 minutes in the future
curl -X POST localhost:8090/api/v1/transactions -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{
  \"transactionId\": \"txn-1001\",
  \"accountId\": \"acc-42\",
  \"amount\": 15000.00,
  \"currency\": \"ZAR\",
  \"category\": \"GAMBLING\",
  \"merchant\": \"Casino Royale\",
  \"country\": \"ZA\",
  \"timestamp\": \"$NOW\"
}"

curl 'localhost:8090/api/v1/assessments?flagged=true&accountId=acc-42' -H "Authorization: Bearer $READER_TOKEN"
```

Request fields:

- `category` is one of `GROCERIES, RESTAURANTS, TRAVEL, ENTERTAINMENT, UTILITIES, HEALTH, RETAIL, GAMBLING, CRYPTO,
  TRANSFER, ATM_WITHDRAWAL, OTHER`.
- `currency` is any ISO 4217 code in upper case (`ZAR`, `USD`, `EUR`, `GBP`, ...). It is accepted only while the
  `exchange_rate` table has a rate for it (see [Currencies](#currencies)); anything else is rejected with `400`.
  Amounts can't have more decimal places than the currency has minor units (`JPY` has none, `KWD` has three).
- `country` is any ISO 3166 alpha-2 code, so transactions from any country are accepted. Only `KP`, `IR` and `SY`
  fire the high-risk-country rule.
- `country` is ISO 3166 alpha-2 (`ZA`).
- `amount` must be > 0 with at most 4 decimal places, in rand.
- `timestamp` is an ISO-8601 `OffsetDateTime` with an offset (for example `2026-03-01T12:00:00Z`). It is converted to UTC on receipt, and all stored and returned timestamps are UTC. It must be within 24 hours in the past and 5 minutes in the future (`fraud.timestamp`).

## Fraud rules

Each rule that fires contributes a score. Thresholds are in the base currency (`fraud.base-currency`, ZAR by default),
and every rule compares the amount converted to that currency, so a USD transaction is tested against the same limit
as a ZAR one. A transaction is **flagged** when
the total reaches `fraud.flag-threshold` (default 50), so no single low-weight rule flags on its own. Every triggered
rule is stored with a human-readable reason.

| Rule | Fires when | Default score |
|---|---|---|
The rules are defined in `src/main/resources/fraud-rules.yml`, not in code. The current rules:

| Rule | Fires when | Score |
|---|---|---|
| `HIGH_AMOUNT` | amount ≥ 10,000 | 40 |
| `RISKY_CATEGORY` | category is `GAMBLING`/`CRYPTO` and amount ≥ 1,000 | 30 |
| `VELOCITY` | more than 5 transactions on the account within the last 10 minutes of arrival (when the service received them, not the client's timestamp) | 35 |
| `HIGH_RISK_COUNTRY` | country is `KP`, `IR` or `SY` | 30 |
| `ODD_HOURS` | amount ≥ 2,000 between 00:00 and 05:00 in `Africa/Johannesburg` | 20 |

### The rule file

Each rule has a `code`, a `type`, a `score` and the parameters its type needs:

```yaml
- code: HIGH_AMOUNT
  type: AMOUNT_THRESHOLD
  score: 40
  threshold: 10000.00
```

The types the service knows are `AMOUNT_THRESHOLD`, `CATEGORY_AMOUNT`, `COUNTRY_LIST`, `VELOCITY` and `ODD_HOURS`.
The file's header lists the parameters for each. Each type takes exactly its own parameters, and a missing or unexpected
one is an error. The service refuses to start if the file is invalid, so it never runs with a broken rule set or an
empty one.

- **Changing rules:** edit the file in a pull request. `.github/CODEOWNERS` requires fraud-team review, and the change
  takes effect on the next deploy. There is no runtime override.
- **Shadow mode:** `shadow: true` scores the rule and records its hits, but they don't count toward the score or the
  flag. Use it to test a rule on live traffic. Remove the flag in a later pull request to enforce it.
- **Rule set version:** every assessment records a hash of the rule file it was scored with (`ruleSetVersion`), so a
  decision can be traced to the exact rules that made it.
- **Disabling a rule** means removing it from the file. Git keeps the history, so it can be restored.

Thresholds are in the base currency (`fraud.base-currency`, ZAR by default), and every rule compares the amount
converted to that currency, so a USD transaction is tested against the same limit as a ZAR one. A transaction is
**flagged** when the enforced total reaches `fraud.flag-threshold` (default 50), so no single low-weight rule flags on
its own. Every triggered rule is stored with a human-readable reason, and shadow hits are marked as such.

**Adding a rule:** if an existing type fits, add an entry to `fraud-rules.yml`. A new kind of logic needs a new
`RuleType`, a class implementing `FraudRule` that takes an `EvaluationContext`, and a case in `RuleSetConfig.create`.
Compare `context.baseAmount()`, not `event().amount()`, so the rule works for every currency. Add tests in `RulesTest`
and an example to `RuleDefinitionTest`.

## Currencies

Rules are written in one base currency (`fraud.base-currency`, ZAR by default). Each other currency is converted with a
rate in the `exchange_rate` table, and a currency is accepted only while it has a row there. Rates are maintained in SQL,
so adding or retiring a currency needs no redeploy:

```sql
INSERT INTO exchange_rate (currency, rate_to_base) VALUES ('USD', 18.50)
ON CONFLICT (currency) DO UPDATE SET rate_to_base = EXCLUDED.rate_to_base, updated_at = now();

DELETE FROM exchange_rate WHERE currency = 'USD';   -- stops accepting USD
```

- The rate is the number of base-currency units per one unit of the submitted currency (`1 USD = 18.50 ZAR`).
- Each assessment stores the amount in the base currency (`baseAmount`) and the rate used (`rateToBase`), so a stored
  result can be audited after rates move, and a replay returns the rate it was scored with.
- The service doesn't fetch rates from a market feed. Keeping rates current is an operational job; a stale rate
  scores transactions against a stale conversion.
- `ZAR` is seeded with rate `1`. A base currency is always accepted without a row, but the row keeps it visible here.

## CI

GitHub Actions (`.github/workflows/ci.yml`), free for public repositories:

- tests for both services (unit tests only; the fraud service has no database integration test), and unit tests
  for the Prometheus alert rules
- image build + Trivy vulnerability scan for both images (fails on fixable HIGH/CRITICAL)
- Trivy misconfiguration scan of the Dockerfiles
- **end-to-end**: starts the whole Compose stack and runs the Postman collection with Newman, using real
  Keycloak tokens through the gateway

Dependabot keeps Maven, Docker, Compose and Actions dependencies up to date. Actions are pinned to commit SHAs.

## Design notes

- **Layers:** `api` (HTTP/DTOs/validation) → `service` (orchestration) → `rules` (pure per-rule logic) → `repository`.
- **Idempotency:** `transactionId` is the primary key. Replays return the stored result without re-scoring, and
  concurrent duplicates are resolved via the unique key. A replay with a *different* payload for the same id
  returns `409` and stores nothing, so a conflicting resubmission is visible instead of silently ignored.
- **Currency:** any currency with an exchange rate is accepted. Amounts are converted to the base currency before the rules
  run, so thresholds mean the same thing for every currency (see [Currencies](#currencies)).
- **Velocity** is computed from stored assessments, so it relies on every transaction being submitted through this
  service. It counts by arrival time, so a client can't spread its timestamps to stay under the limit. Client
  timestamps are checked only against the accepted window (`fraud.timestamp`).
- **Concurrency:** requests for the same account are evaluated one at a time (a PostgreSQL advisory lock per account, held until
  the transaction commits), so a concurrent burst can't slip past the limit. Different accounts don't block each
  other. A request that waits longer than `fraud.account-lock-timeout` (default 1s) fails fast with `429`. While it
  waits it holds a connection, so the pool is capped (`DB_POOL_SIZE`, default 20) and a request that can't get a
  connection within 2s fails with `503`. This limits how far one hot account can slow the others, but it doesn't
  remove the effect: a burst on one account still uses connections that other accounts could have used.
- **Replays** are answered before any intake check, so a retry of a stored transaction returns its result even after
  the timestamp window has passed. Only new transactions are checked against the window.
- **Tracing:** the gateway's `X-Request-Id` is reused by the service and put in every log line for the request, so
  one id finds the request in both services' logs.
- **Migrations:** `V2` is a plain `CREATE INDEX`, which blocks writes to `fraud_assessment` while it builds. That's
  fine for an empty or small table. On a large production table, build `idx_assessment_account_evaluated` by hand
  with `CREATE INDEX CONCURRENTLY` before deploying: `V2` uses `IF NOT EXISTS`, so it then skips. Flyway can't run
  `CONCURRENTLY` safely, because the build waits on Flyway's own open transaction and startup hangs.
- **Storage:** PostgreSQL. The schema is managed by Flyway (`db/migration`), and Hibernate only validates it.
- **Security:** OAuth2 client credentials (Keycloak). The gateway enforces token + scope at the edge and the service
  enforces them again, so it stays protected even if something bypasses the gateway. The API docs use a separate
  Keycloak browser login (authorization code flow, client `fraud-docs`), since a browser can't send a Bearer token
  when opening a page.
- **Audit trail:** every submit, read and search is recorded in `audit_event` with the calling client (the token's
  `azp`), the transaction id, the outcome (`CREATED`, `REPLAYED`, `CONFLICT`, `REJECTED`, `BUSY`, `UNAVAILABLE`,
  `NOT_FOUND`, `OK`, `ERROR`), the time and the request id, so it can be joined to the gateway's access log. Each entry
  commits in its own transaction, so rejected requests are recorded too. Rows can't be updated, deleted or truncated:
  a database trigger enforces that (`V6`). If an entry can't be written, the request fails rather than going
  unrecorded. Rule changes are audited by git history, and each assessment stores the rule-file version that scored it.
  Limits: requests rejected by bean validation (`400` for a malformed body) are not audited, and the application's
  database user should not own the table, so it can't drop the trigger.
- **Testing:**
  - Unit tests (JUnit, Mockito, AssertJ) cover each rule's boundaries, scoring and flagging, idempotency and replay
    conflicts, the account-lock timeout, timestamp and currency checks, request validation, the API mapping and error
    responses, metrics, and the gateway's access log, request ids and client ids.
  - There is no automated test against a real PostgreSQL. Flyway migrations, schema validation, the account lock under
    concurrent load and the search queries are not exercised by any test in this repository.
  - The HTTP layer, security configuration, routing and the Swagger login are checked only by the end-to-end CI job
    (the whole Compose stack plus the Postman collection).

## Known limitations

- Keycloak runs in development mode (embedded database, realm re-imported on start). Client secrets come from a
  git-ignored `.env`, so they aren't in the repo, but they are still plain environment values; a real deployment
  needs production-mode Keycloak and secrets from a vault.
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
- Transactions are scored once, when they arrive. An already-assessed transaction is never re-scored.
- The `fraud-api/read` scope can read every account's assessments. There is no per-account authorization yet. Limiting a
  client to specific accounts needs an access model, such as a token claim listing its accounts, mapped in Keycloak.
