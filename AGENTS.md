# AGENTS.md

## Stack — Verified 2026-08-30
- Backend: Spring Boot 4.1.1 (Java 17 `17.0.19`, Maven `3.9.16`, requires Java 17-26) — `spring.io/projects/spring-boot` latest stable. Fallback 3.5.16 if needed, but 4.1.1 validated (`mvn validate` succeeds).
- Frontend: Next.js 16.3.3 / React 19.1.1 / TypeScript 5.8 / Node 22 (Docker) / 24.18.1 (local) — Active LTS Aug 25 2026.
- DB: PostgreSQL 16-alpine (compose), 16.15/18.6 per `postgresql.org` — H2 in-memory for tests (`application-test.yml`).
- Infra: Docker Compose v5.3.0.

## Project Structure
- `backend/` — Spring Boot Maven. Entrypoint `com.recoverflow.RecoverFlowApplication:8`, health `com.recoverflow.health.HealthController:10`. Config `application.yml:3` (env `DATABASE_URL`, `DEMO_API_KEY`). Migration `db/migration/V1__init.sql:1`.
- `frontend/` — Next.js `src/app/page.tsx:1` (server fetch to backend), `next.config.mjs:1` (rewrites). Env `NEXT_PUBLIC_API_URL`.
- `docker-compose.yml:1` — services `db:2`, `backend:22`, `frontend:52` with healthchecks. `backend` `DATABASE_URL=jdbc:postgresql://db:5432/...` (compose) vs `localhost:5432` (local).
- `.env.example:1` — copy to `.env`, never commit `.env`.

## Verified Commands (PowerShell)
- Backend compile: `mvn -f backend/pom.xml clean verify` — 53s, BUILD SUCCESS (Boot 4.1.1, Flyway, Postgres driver).
- Backend run (H2, no PG needed): `java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=test` → `:8080` — tested `curl http://localhost:8080/api/v1/health` → `{"status":"UP"}` + `curl http://localhost:8080/actuator/health` → `db:H2`.
- Backend run (PG): `mvn -f backend/pom.xml spring-boot:run` — requires `DATABASE_URL` / compose db on `:5432`.
- Frontend install: `npm install --prefix frontend` → 345 packages.
- Frontend dev: `npm run dev --prefix frontend` → `:3000` (page fetches `NEXT_PUBLIC_API_URL/api/v1/health` server-side, shows UP/DOWN).
- Frontend build: `npm run build --prefix frontend` → Turbopack compile, 24s, route `ƒ /` dynamic.
- Compose validate: `docker compose config` — parsed OK (daemon not needed). Full `docker compose up --build` needs Docker Desktop (was stopped `com.docker.service` on this machine).
- CI: `.github/workflows/ci.yml:1` — `backend: mvn -B verify`, `frontend: npm ci && npm run build`.

## Conventions
- Source of truth is executable config (`backend/pom.xml`, `docker-compose.yml`, `frontend/package.json`, `application.yml`), not prose.
- Money: never float — future `NUMERIC(19,4)` + `BigDecimal`. Phase 1 has no money code yet.
- Demo auth (future): `X-Demo-Key → DemoPrincipal` server-derived `merchantId`, not client body. Do not add `merchantId` to request body as authority.
- Hidden truth / AI separation (Phase 0): estimator `base + aiContribution` must be deterministic + versioned; `P_true` never from AI. Keep `syntheticCustomerFrictionProxy` label.
- Keep this file compact: every line must answer "would agent miss without help?" Delete stale notes.

## What Is NOT Yet Built (Phase 1 only)
No AI, recovery workflow, state machine, policy/EV engines, gateway, evaluator, product pages — those start Phase 2 per `docs/` Phase 0.
