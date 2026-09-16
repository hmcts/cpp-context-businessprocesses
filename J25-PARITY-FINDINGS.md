# businessprocesses — J17 → J25 behavioural parity findings

Part of the work-management-proxy-combo parity audit (PEG-3402), per the CTP parity guide
(Confluence 1990371020) and the users-groups reference (PEG-3336). J17 (`main`) is the source of truth.

## Context shape

Camunda business-processes context with a small JPA viewstore (2 entities: `TaskEntity`,
`TaskHistoryEntity`). The DeltaSpike→JPA migration was reviewed and is **parity-clean**:

- `TaskRepository.findBy(id)` → `entityManager.find(TaskEntity.class, id)` — null↔null (no BC-01).
- `TaskHistoryRepository` (was a DeltaSpike `@Repository` interface): `findById`/`findByTaskId` now use
  `createQuery(...).getResultList()` — **List returns, not single-result**, so no `getSingleResult`
  throw/null flip; `save`→`merge`. Parity.
- The `findFirst()` sites in the event processors are in-memory Java-stream operations on collections,
  **not JPA finders** — irrelevant to BC-01.
- No `getSingleResult`, no primitive `@Version`, no JPQL `!= null`, no lazy associations.

Golden test JSON unchanged J17→J25.

## BC catalogue disposition

| BC | Present? | Disposition |
|----|----------|-------------|
| BC-01/02 | No | N/A — `find` + list queries only, no single-result throw finder |
| BC-04 | No | N/A — no primitive `@Version` |
| BC-05 | No | N/A — no JPQL `!= null` |
| BC-06 | No | N/A — no lazy associations |
| BC-07 | No | N/A — no `liquibase.hub.mode` |
| BC-11 | No | N/A |
| BC-20 | **Yes** (1 kbase) | **Guarded** — `AccessControlRuleCountTest` for kbase `QUERY_API`. Both branches. |
| BC-24 | Runtime | Covered by ITs |

## Change

- **BC-20:** `businessprocesses-query-api/.../accesscontrol/AccessControlRuleCountTest.java` —
  rule-count guard for kbase `QUERY_API`. Both branches.
