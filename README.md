# RecoverFlow — AI Revenue Recovery Decision & Orchestration Engine

> Razorpay AI Buildathon — Track 03: AI Revenue Recovery

RecoverFlow is a decision and orchestration engine for failed payments. AI assesses recoverability, a deterministic pipeline ranks recovery actions by expected net value, a hard policy gate authorizes only permissible actions, and safe execution with reconciliation and audit completes the recovery lifecycle.

## Core Architecture

```
AI assessment
  → deterministic likelihood estimation (observable-only, versioned)
  → expected net recovery value (P*amount − cost − friction − risk)
  → hard policy gate (allow / block / escalate / stop)
  → safe execution (idempotent, optimistic concurrency)
  → reconciliation (UNKNOWN → RECOVERED / FAILED)
  → audit (persisted decision snapshot + events)
```

Policy can override AI. Execution requires policy approval. No HiddenTruth or true expected value is used at decision time; it is evaluator-only.

## Stack (verified 2026-08-30)

| Layer | Choice | Version |
|-------|--------|---------|
| Backend | Spring Boot | 4.1.1 (Java 17 `17.0.19`, Maven `3.9.16`, requires Java 17-26) |
| DB | PostgreSQL | 16-alpine (compose) · H2 in-memory for tests |
| Frontend | Next.js / React / TypeScript | 16.3.3 / 19.1.1 / 5.8 |
| Node | Node.js | 22 (Docker) / 24.18.1 (local) |
| Infra | Docker Compose | v5.3.0 |

## Main Product Features

- **Recovery Cases** — list and detail of payment failures, recovery state, gateway and customer context
- **Historical Decision View** — persisted snapshot replay (observable evidence, AI assessment, likelihood, EV ranking, policy gate, selected action, audit timeline, execution → gateway → reconciliation) — never recomputed live
- **Failure Lab** — 10 deterministic scenarios exercising the real execution path (see below)
- **Analytics** — operational evidence dashboard: Baseline B (Policy-Only) vs RecoverFlow recovered revenue, revenue delta/lift, true decision regret and regret reduction, AI behavior (action-change, help/hurt/neutral) — all synthetic evaluation
- **Evaluation** — experimental benchmark: development / validation / held-out partitions, three-way comparison (Baseline A, Baseline B, RecoverFlow, Oracle evaluator-only), regret cascade, AI-quality experiment, methodology and reproducibility

## Safety Guarantees

- AI does not authorize money movement — assessment is advisory only
- Policy can override AI; execution requires `ALLOWED` policy decision
- Idempotent execution (`idempotencyKey`) — duplicate and concurrent requests produce one gateway invocation
- `UNKNOWN` on gateway timeout — no blind retry; reconciliation queries gateway and finalizes state
- Optimistic concurrency protection on recovery cases
- Persisted decision snapshot and audit events for every decision — no chain-of-thought stored

## Evaluation

- **Partitions:** `DEVELOPMENT` 30 seeds `10000–10029` (6000 cases), `VALIDATION` 10 seeds `20000–20009` (2000), `HELD_OUT` 10 seeds `30000–30009` (2000) — `200` cases per seed — identical evaluator configuration; held-out not used for tuning
- **Synthetic evaluation** clearly labelled on every metric; not production performance; Oracle labelled `evaluator-only reference — not executable`
- **True decision regret:** gap between selected action's true expected value and best permissible action (evaluator-only); displayed as policy-only vs RecoverFlow regret and regret reduction (total and mean, not realized payment failure)
- **AI behavior:** `action-change rate`, `help / hurt / neutral` rates classified via true expected value, not Bernoulli outcome — separated from business outcome (revenue/regret)
- **AI quality sanity experiment:** observable-only synthetic proxy on same worlds; measured true-category accuracy `LOW ~44%`, `MEDIUM ~65%`, `HIGH ~77%` — shown as measured values, never `50/75/90%` or real LLM accuracy

## Failure Lab — 10 Deterministic Scenarios

`GATEWAY_TIMEOUT`, `GATEWAY_FAILURE_RETRYABLE`, `GATEWAY_FAILURE_TERMINAL`, `DUPLICATE_EXECUTION`, `CONCURRENT_EXECUTION`, `UNKNOWN_RECONCILIATION_SUCCESS`, `UNKNOWN_RECONCILIATION_FAILURE`, `STALE_POLICY_APPROVAL`, `CUSTOMER_OPT_OUT_BEFORE_EXECUTION`, `AI_RECOMMENDS_BLOCKED_ACTION`

Key demonstrations (real `ExecutionService → MockPaymentGateway → ReconciliationService → AuditService`):
- `timeout → UNKNOWN → reconciliation`
- `duplicate → one gateway invocation`
- `concurrency → one execution, second rejected`
- `policy block → zero gateway calls` (visibly 0)

## Routes

| Route | Description |
|-------|-------------|
| `/` | Overview (revenue at risk / recovered, recovery rate) |
| `/overview` | Overview (alias) |
| `/recovery-cases` | Recovery cases list |
| `/recovery-cases/[id]` | Case detail (payment, customer, state, actions, audit) |
| `/recovery-cases/[id]/decision` | Historical decision snapshot |
| `/failure-lab` | Failure Lab (10 scenarios) |
| `/analytics` | Analytics operational dashboard |
| `/evaluation` | Evaluation benchmark |

Navigation in `frontend/src/components/Nav.tsx` resolves all routes.

## Local Run

### 1. Env

```powershell
Copy-Item .env.example .env
# Edit .env if needed — defaults work for local
```

### 2. Docker Compose (recommended)

```powershell
docker compose up --build
# Frontend  http://localhost:3000
# Backend   http://localhost:8080/api/v1/health
#           http://localhost:8080/actuator/health
# DB        localhost:5432 (recoverflow/recoverflow)
```

### 3. Local without Docker

```powershell
# Backend with H2 (no Postgres needed)
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=test

# Backend with Postgres (requires DB on :5432)
mvn -f backend/pom.xml spring-boot:run

# Frontend
npm install --prefix frontend
npm run dev --prefix frontend    # http://localhost:3000
npm run build --prefix frontend  # production build check
```

## Health Endpoints

- `GET /api/v1/health` → `{"status":"UP", ...}`
- `GET /actuator/health` → Actuator (DB + Flyway)
- `GET /api/v1/analytics` → synthetic evaluation aggregates (development/validation/held-out + AI quality)
- `GET /api/v1/evaluation/*` → evaluation runs/ablation (synthetic)

## Environment Variables

See `.env.example` — never commit `.env` or real credentials. Required keys: `DATABASE_URL`, `DEMO_API_KEY` (`application.yml:3`), `NEXT_PUBLIC_API_URL` (frontend). AI (`AI_PROVIDER`, `AI_API_KEY`) and Razorpay (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_MODE`) remain empty for synthetic/mock mode.

## Project Structure

```
backend/               # Spring Boot (Maven, Java 17)
  src/main/java/com/recoverflow  (ai, decision, evaluation, execution, recovery, policy, synthetic, ...)
  src/main/resources (application.yml, db/migration)
  Dockerfile
frontend/              # Next.js 16.3.3 (TypeScript)
  src/app (page, overview, recovery-cases, failure-lab, analytics, evaluation)
  src/components/Nav.tsx
  Dockerfile
docker-compose.yml     # db, backend, frontend with healthchecks
.env.example
.github/workflows/ci.yml
AGENTS.md
```

## Validation Status (honest)

- **Backend tests:** `372 passing, 1 skipped` (`mvn -f backend/pom.xml clean verify`) — 1 skipped is PostgreSQL/Testcontainers when Docker unavailable; H2 in-memory suite passes
- **Frontend tests:** `65 passing` (`npm run test --prefix frontend` — 6 files: recovery-cases, case detail, decision, failure-lab, analytics, evaluation)
- **Frontend build:** passes (`npm run build --prefix frontend` — 8 routes: `○ /`, `○ /_not-found`, `○ /analytics`, `○ /evaluation`, `○ /failure-lab`, `○ /overview`, `○ /recovery-cases`, `ƒ /recovery-cases/[id]`, `ƒ /recovery-cases/[id]/decision`)
- **PostgreSQL live validation:** pending — requires Docker Desktop / Testcontainers (daemon not needed for H2)
- **Razorpay live TEST validation:** pending — requires `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` test credentials and `RAZORPAY_MODE=test`
- **Real LLM live smoke test:** pending — requires live `AI_API_KEY` / `AI_PROVIDER` credentials; current mode is `synthetic-ai-v1` observable-only proxy

No pending integration is claimed as validated. All `synthetic evaluation` numbers are benchmark-only, not production.

## CI

`ci.yml` runs `backend: mvn -B verify` and `frontend: npm ci && npm run build`.

## AGENTS.md

Compact instruction file for OpenCode sessions. Source of truth is executable config (`backend/pom.xml`, `docker-compose.yml`, `frontend/package.json`, `application.yml`).
