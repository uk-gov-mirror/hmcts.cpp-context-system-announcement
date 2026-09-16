# system-announcement — J17 → J25 behavioural parity findings

Audit against the CTP J17→J25 parity guide (Confluence 1990371020, 24-BC catalogue) and the
users-groups reference (PEG-3336, PRs #217/#219). Method: **Java 17 (`main`) is the source of
truth** — each parity test is authored green on J17 first, then the identical assertion is carried
to J25 (`team/25.104.x`).

## Context shape (important subtlety)

system-announcement *looks* like a JPA context — `SystemAnnouncementEntity` carries `@Entity`/`@Id`/
`@Column` and there is a `persistence.xml` declaring a Hibernate persistence-unit. **But the data
access is raw JDBC, not JPA/Hibernate.** `SystemAnnouncementRepository` is `@ApplicationScoped` and
uses `PreparedStatementWrapperFactory` + `ViewStoreJdbcDataSourceProvider` with hand-written native
SQL and manual `ResultSet`→entity mapping. There is **no `EntityManager` anywhere in the context**
(verified by grep), so no Hibernate finder / lazy-init / primitive-coercion code path executes.

Consequently the Hibernate breaking-change family is N/A *by construction*:
- **BC-01/02** (finder null↔throw): `findById` returns `results.isEmpty() ? null : results.get(0)` —
  the same JDBC code on both branches; there is no Hibernate finder whose no-match semantics change.
- **BC-04** (NULL→primitive): the only primitive column-field is `short orderIndex`, populated via
  JDBC `resultSet.getShort("order_index")` (returns 0 for SQL NULL — JDBC behaviour, unchanged
  J17→J25). No Hibernate `@Basic` primitive coercion is involved.
- **BC-05** (JPQL `!= null`): the queries are **native SQL** using `end_date IS NULL` — the JPQL
  `<> null` → UNKNOWN seam does not apply to native SQL.
- **BC-06** (lazy-init): no JPA associations, no lazy loading.

**No production Java source changed J17→J25** beyond `javax`→`jakarta` import churn (verified: the
non-import source diff between `main` and `team/25.104.x` is empty).

## BC catalogue disposition

| BC | Area | Present? | Disposition |
|----|------|----------|-------------|
| BC-01/02 | JPA finder null↔throw | No | N/A — raw JDBC `findById` (`isEmpty()?null`), same on both branches |
| BC-04 | NULL → primitive int | No | N/A — `getShort` (JDBC 0-on-null), not Hibernate coercion |
| BC-05 | JPQL `!= null` | No | N/A — native SQL `IS NULL`, not JPQL |
| BC-06 | Lazy-init | No | N/A — no JPA associations / no EntityManager |
| BC-07 | `liquibase.hub.mode` removed in Liquibase 5 | **Yes** | **Fixed** — removed from `systemannouncement-liquibase/…/liquibase.properties`. Valid on Liquibase 4 (J17), hard deploy failure on Liquibase 5 (J25). J25 change only. |
| BC-11 | `JsonObjectBuilder.add(k, null)` | Builders used, no null-add | N/A — `QueryAll/QueryBannerAnnouncementsApi` only `add(JsonValue)` (converted objects) and `add("key", builder)`; no `add(key, null)` site, and per guide v6 that pattern is parity anyway |
| BC-20 | Drools 0-rule vacuous deny | **Yes** (1 kbase, 5 rules files) | **Guarded** — added `AccessControlRuleCountTest` asserting the `SystemAnnouncement.API` kbase compiles ≥1 rule. Both branches. |
| BC-24 | pgjdbc / native SQL | Runtime | Covered by ITs — native SQL is unchanged J17→J25, so parity rides on the existing integration suite against real Postgres. |

## Golden-master baseline

The existing test golden JSON is **unchanged** J17→J25 (0 files differ). The existing repository unit
test (`SystemAnnouncementRepositoryTest`, Mockito over the JDBC mapping) and API tests already pin the
mapping/response shape.

## Changes in this branch

- **BC-07:** removed `liquibase.hub.mode: off` from the liquibase properties (J25 only).
- **BC-20:** `systemannouncement-api/.../accesscontrol/AccessControlRuleCountTest.java` — rule-count
  guard for kbase `SystemAnnouncement.API` (both branches).

## Two-PR structure

- **J17 (`main`)** — test-only: `AccessControlRuleCountTest`.
- **J25 (`team/25.104.x`)** — same test **plus** the BC-07 liquibase fix and these findings docs.
