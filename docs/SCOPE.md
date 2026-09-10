# MVP scope

## Product
Turn webhook delivery failures into repeatable tests of application behavior.
The user specifies expectations; HTTP 200 alone is not proof of business success.

## Local deployment
One trusted team per installation. No public multi-tenant hosting or billing.
Allowed destination origins are configured by the operator. Redirects are not followed.
No production credentials or payloads in demo fixtures.

## Milestones
1. Repository conventions, scope and architecture.
2. PostgreSQL persistence, validated scenarios, execution and assertions.
3. React scenario editor, history and detailed reports.
4. Demo shop, CI runner, integration tests, Docker Compose and bilingual documentation.

## Semantics
Each run snapshots its scenario and replaces {{runId}} with a unique run identifier.
Steps execute sequentially; copies inside one step may execute concurrently.
The client has a bounded timeout. Runs and response sizes are bounded.
A restart marks unfinished runs interrupted; no automatic retry of uncertain deliveries.
A probe polls a business API until its assertion succeeds or its deadline expires.
Run cancellation and distributed execution are outside the first milestone.

## Future
HTTP service simulation, combined inbound/outbound workflows, team access control,
then additional protocols only when validated by users.
