# RecoverFlow — AI Revenue Recovery Decision & Orchestration Engine

> Razorpay AI Buildathon — Track 03: AI Revenue Recovery  
> Phase 1 Bootstrap — no business logic yet. See AGENTS.md for working conventions.

## Stack (verified 2026-08-30)

| Layer | Choice | Version | Source |
|-------|--------|---------|--------|
| Backend | Spring Boot | 4.1.1 | https://spring.io/projects/spring-boot (latest stable, requires Java 17-26) |
| Java | Eclipse Temurin | 17.0.19 | `java -version` |
| DB | PostgreSQL | 16-alpine (supports 16.15 / 17.11 / 18.6) | https://www.postgresql.org/about/news/ |
| Frontend | Next.js | 16.3.3 | https://github.com/vercel/next.js/releases/tag/v16.3.3 |
| React | React | 19.1.1 | npm |
| Node | Node.js | 22 (Docker) / 24.18.1 (local) | `node -v` |
| Build | Maven | 3.9.16 | `mvn -v` |
| Infra | Docker Compose | v5.3.0 | `docker compose version` |

> Spring Boot 4.1.1 validated via Maven Central (`mvn validate` with parent 4.1.1 succeeds). Next.js 16.3.3 is Active LTS (Aug 25 2026 security release). Postgres 16-alpine chosen for Phase 1; 18.6 is latest but 16 is LTS until 2028 and matches Phase 0.

## Quick Start (Phase 1)

### 1. Env

```powershell
Copy-Item .env.example .env
# Edit .env if needed — defaults work for local
```

### 2. Docker Compose (recommended)

```powershell
docker compose up --build
# Frontend http://localhost:3000
# Backend  http://localhost:8080/api/v1/health
#           http://localhost:8080/actuator/health
# DB       localhost:5432 (recoverflow/recoverflow)
```

### 3. Local without Docker

Backend (requires local Postgres or H2 profile):

```powershell
# H2 in-memory (no Postgres needed) — for quick health check:
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=test

# With Postgres (running separately):
mvn -f backend/pom.xml spring-boot:run
```

Frontend:

```powershell
cd frontend
npm install
npm run dev    # http://localhost:3000
npm run build  # production build check
```

## Health Endpoints

- `GET /api/v1/health` → `{"status":"UP","service":"recoverflow-backend",...}`
- `GET /actuator/health` → Spring Boot Actuator (includes DB + Flyway)

Flyway migration `V1__init.sql` creates `schema_version_check` to prove DB connectivity.

## Project Structure

```
backend/                 # Spring Boot (Maven, Java 17)
  src/main/java/com/recoverflow
  src/main/resources (application.yml, db/migration)
  Dockerfile
frontend/                # Next.js 16.3.3 (TypeScript)
  src/app
  Dockerfile
docker-compose.yml
.env.example
.github/workflows/ci.yml
```

## CI

GitHub Actions — `ci.yml` runs:

- Backend: `mvn -B verify`
- Frontend: `npm ci && npm run build`

## What Is NOT in Phase 1

No AI, recovery workflow, policy engine, EV engine, gateway, evaluator, or product pages. Those land in Phase 2+.

## AGENTS.md

Compact instruction file for future OpenCode sessions. Update it when you add toolchain — delete the greenfield snapshot note once this README covers setup.
