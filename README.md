# Integration Lab

A self-hosted test bench for webhook reliability. Reproduce duplicate, concurrent,
delayed and out-of-order delivery, then verify the business outcome through HTTP.

**Java 21 · Spring Boot · React / JavaScript · PostgreSQL · Docker Compose**

[Русская документация](docs/README.ru.md) · [Scope and roadmap](docs/SCOPE.md)

## Start locally

Requires Docker Desktop with Linux containers. From PowerShell 7:

```powershell
./scripts/init-env.ps1
docker compose up -d --build
```

Open **http://localhost:8088** and enter `LAB_API_KEY` from your local `.env`.
The setup script generates random credentials and refuses to overwrite an existing file.
Alternatively copy `.env.example` to `.env` and fill its three secret values.
On Linux, copy the example and generate those values using your usual secret generator.

Only the web port is published, on loopback. PostgreSQL and the demo shop stay inside
the Compose network. `docker compose down` stops services and preserves database data.

## First two tests

1. Select **Try fixed handler**, then **Save & run**. Five parallel copies create one
   demo order. The business probe expects count = 1: **PASSED**.
2. Select **Try broken handler**, then **Save & run**. All five requests return HTTP 200,
   but five demo orders exist: **FAILED**.

The demo is intentionally minimal and stores orders in memory. It is not a real payment
service. Each scenario uses `{{runId}}` to isolate its data.

## Features

- Create, edit, import and export JSON scenarios.
- Reuse manually entered or captured event bodies.
- Ordered POST steps, duplicate copies, parallel groups and delays.
- Assert HTTP status and JSON Pointer values.
- Poll a GET business API until a value matches or a deadline expires.
- Persist scenario snapshots, delivery evidence and run status in PostgreSQL.
- Export JSON and JUnit XML from a command-line runner.
- After a backend restart, unfinished runs become INTERRUPTED, not silently retried.

## Connect your application

Set `LAB_ALLOWED_ORIGINS` to a comma-separated list of exact origins and recreate backend.
Use `http://host.docker.internal:PORT` for a service running on the Windows host:
inside Docker, localhost means the container itself.

Optional `LAB_TARGET_HEADERS` is a JSON object mapping exact origins to header objects.
Keep it only in `.env`. Headers apply to POST deliveries and GET probes for that origin,
and are never stored in scenario exports. Do not use real production credentials.

A test sender can POST a body to `/api/inbox/YOUR_LAB_INBOX_TOKEN`.
The inbox stores bodies only. Provider-specific signature regeneration is not implemented.

## Scenario format

See [fixed example](examples/duplicate-fixed.json) and
[broken example](examples/duplicate-broken.json).

Steps run in array order. `delayMs` delays the start of a step.
`parallel` sends copies concurrently without promising target-side execution order.
`pointer` uses JSON Pointer syntax, e.g. `/count`.
`expected` is a JSON value, e.g. `1`, `true`, or `"paid"`.
Missing/null step expectations disable JSON assertion; asserting JSON null is not supported yet.
The probe expects HTTP 200 and its JSON assertion, with bounded polling.

## CI runner

Provide `LAB_API_KEY` through the process environment:

```sh
node scripts/run-scenario.mjs examples/duplicate-fixed.json
```

Exit codes: 0 = passed, 1 = failed/error/interrupted test, 2 = client/infrastructure error.
Reports go to `artifacts/`. `LAB_URL` overrides the default http://127.0.0.1:8088,
and `LAB_REPORT_DIR` changes the output directory.

The runner creates a saved scenario per invocation. Reuse/update existing scenarios
through the API for long-lived installations.

## Development and verification

```sh
mvn -B -ntp -f backend/pom.xml test
cd frontend
npm ci
npm run build
npx playwright install chromium
npm test
```

Backend tests require Java 21, Maven and Docker (Testcontainers PostgreSQL).
Browser tests require the running Compose stack and `LAB_API_KEY`.
GitHub Actions runs backend tests, frontend build, both demo scenarios and browser tests.

For frontend development, `npm run dev` proxies /api to localhost:8080.
Run the backend locally with DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD,
LAB_API_KEY, LAB_INBOX_TOKEN and LAB_ALLOWED_ORIGINS configured.

## Architecture

A single Spring Boot process owns validation, execution, assertions and persistence.
PostgreSQL uses Flyway migrations. React uses the HTTP API. No Redis, Kafka or AI API is required.
The demo target is a separate container because it represents the system under test.

## Limits and deployment boundary

This is an early **local, single-team MVP**, not a public SaaS.
Use one backend process. There is no tenant isolation, user RBAC, cancellation,
automatic retention policy, arbitrary scripting or outbound API simulation yet.
Only POST deliveries and GET business probes are supported.

Limits: 20 steps, 30 deliveries, 10 copies per step, 2 active runs,
3-second request timeout, 64 KiB response bodies, 32,000-character step bodies.
Lists show the latest 100 runs/events; stored history is not automatically pruned.
Allowed origins must be operator-controlled. Do not expose the stack as a public proxy.
Scenarios may change data in the target: always use an isolated test environment.

## Git workflow

`dev` contains development. `prod` advances only to validated milestones.
Small commits describe one change without signature or co-author trailers.
A public license has not yet been selected.
