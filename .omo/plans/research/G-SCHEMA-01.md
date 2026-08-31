# G-SCHEMA-01 — Schema Evolution Semantics & `validate` Correctness

**Gap ID:** G-SCHEMA-01 · **Component:** Schema evolution semantics & `validate` correctness · **Priority: P0**
**Date:** 2026-08-30 · **Author:** gap-research agent (Sisyphus-Junior)
**Plan refs:** `oss-mapping-wedge.md §3 (YAML→SQL validate)` · `oss-mapping-wedge-GAPS.md G-SCHEMA-01` · `oss-mapping-wedge-CRITIQUE.md §10b + §1`
**Acceptance:** CI gate rejects a column-rename without a version bump; `validate` covers m:n + FK≡PK drift case; warehouse-variance matrix checked into repo.

---

## 0. Executive Summary

The wedge's `validate` command must not be YAML-syntax sugar. At P0 it is the CI gate that turns a silent breaking schema change (rename `rev` → `revenue_net`, FK target retarget, `Revenue ($)` unquoted) into a **forced conversation before merge** — the same posture Confluent Schema Registry and the datadef/Data Contracts pattern enforce: *a PR that would break a contract fails the build before it can merge* (stevenzg.com, L3). This doc defines (a) the breaking vs non-breaking taxonomy, (b) the registry-pattern version-bump gate, (c) the YAML compatibility matrix, (d) the FK≡PK / m:n propagation case, (e) the per-warehouse null/case/quoting variance matrix, and (f) the ephemeral-warehouse execution harness that proves `validate ≠ syntax-only`.

Evidence levels: L1 systematic review, L2 peer-reviewed, L3 official/docs, L4 provider guidance/blog/benchmark, L5 opinion — confidence notes inline.

---

## 1. Breaking vs Non-Breaking Taxonomy — What Actually Breaks the Canonical Load

### 1.1 Formal frame — Confluent / Apicurio compatibility modes (L3)

Schema Registry compatibility (Confluent, L3 — docs.confluent.io) defines the frame the wedge reuses at the mapping layer:

| Mode | Guarantee | Intuition |
|---|---|---|
| `BACKWARD` (default) | new schema can read data written with old schema | add optional fields, remove fields (old writer, new reader) |
| `FORWARD` | old schema can read data written with new schema | remove optional fields, add fields (new writer, old reader) |
| `FULL` | both directions | add/remove optional fields only |
| `BACKWARD_TRANSITIVE` / `FULL_TRANSITIVE` | new schema compatible with **all** previous versions, not just latest | required once history > 2 versions; otherwise drift accumulates silently across hops |

> "Each schema version gets a unique ID and incremented version number. When schemas are updated, Schema Registry checks compatibility before accepting the new version." — Confluent Schema Evolution & Compatibility Types (L3) https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html
>
> "Avoid `NONE` compatibility in production because `NONE` disables all compatibility checks and allows breaking changes to reach consumers." — Apicurio Registry 3.3 lifecycle best practices (L3) https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-schema-lifecycle-best-practices.html

The wedge is **consumer = canonical load + downstream marts; producer = source SFTP/Kafka drop**. The mapping YAML is the *data contract* between them (see datadef.io — contract = schema + SLAs + ownership + rules, L3). Compatibility must be checked on the **contract surface** (canonical-visible fields + FK targets), not just raw column existence.

### 1.2 Mapping-layer taxonomy (wedge-specific)

The critique's killer case — *source renames `rev` → `revenue_net`; `validate` passes because the new column is nullable, but `canonical.revenue` now has zero source rows because the old name vanished* (CRITIQUE §10b) — shows why raw-column "nullable = safe" is insufficient. The taxonomy below is defined at two layers: **source column change** and **canonical surface effect**.

| # | Change | Raw-layer intuition | Canonical surface | Breaking? | Requires canonical DDL migration? | SemVer delta |
|---|---|---|---|---|---|---|
| 1 | **Add nullable column** (`discount_code` nullable) | backward compatible at source | no canonical binding yet | **Non-breaking** | No — ignored until mapped | PATCH or MINOR (no migration) |
| 2 | **Add non-nullable column without default** | backward incompatible — old readers assume absence | if auto-mapped → forces `NOT NULL` population | **Breaking** | Yes if bound to canonical `NOT NULL` | MAJOR |
| 3 | **Remove / delete column** that is **unmapped** | no consumer | none | Non-breaking | No | MINOR (contract narrows producer, not consumer) |
| 4 | **Remove / delete column** that **is mapped** to canonical | canonical field loses source | `canonical.revenue` null-rate jump → load lineage break | **Breaking** | Yes — canonical field must become nullable, defaulted, or remapped | MAJOR |
| 5 | **Rename** `rev` → `revenue_net` (same semantics, new identifier) | looks like "remove + add" atomically | old mapping `rev → canonical.revenue` dangles; new column unmapped → silent zero-row canonical field | **Breaking** (the slip-through case) | Depends: if rename is alias-only and canonical DDL unchanged → YAML-only MAJOR; if canonical field also renames → DDL migration MAJOR | MAJOR — **must bump version even though new column is nullable** |
| 6 | **Type change — widening** `INTEGER → DECIMAL` / `VARCHAR(50) → VARCHAR(200)` | backward compatible if coercion succeeds | may tighten validation if canonical is `INTEGER` | Non-breaking (with coercion contract) | No (or widen canonical type defensively) | MINOR |
| 7 | **Type change — narrowing** `DECIMAL → INTEGER` / relax → `NOT NULL` / `VARCHAR → DATE` | may truncate / throw at runtime | canonical load may truncate, reject, or mis-parse (`"$1,234"` as 1) | **Breaking** | Yes if canonical type must narrow | MAJOR |
| 8 | **FK target change** `orders.customer_id` FK `customers.id → clients.id` (or retarget after entity split) | constraint semantics change; old FK values may dangle | lineage + ER + downstream join correctness changes | **Breaking** (always) | **Yes — canonical DDL migration** (`ALTER TABLE ... DROP CONSTRAINT ... ADD CONSTRAINT ... REFERENCES new_target`, index changes, possible data backfill) | MAJOR |
| 9 | **PK change** (surrogate → composite `@@id([orderId, productId])` for junction) | identity changes; audit / lineage keys shift | `__dpone__parent_id` lineage hash (SHA-256) changes; downstream mart grain changes | **Breaking** | Yes | MAJOR |
| 10 | **Nullability change** on mapped field: `NULLABLE → NOT NULL` (tighten) | new writes may violate | canonical load rejects more rows | **Breaking** | Yes if canonical mirrors constraint | MAJOR |
| 11 | **Nullability change** `NOT NULL → NULLABLE` (relax) | more nulls flow downstream | canonical consumers that assumed non-null may NPE | Non-breaking at contract layer, **breaking at consumer SLO** — treat as MAJOR if freshness/SLA promises non-null | MAJOR (conservative) |
| 12 | **Add column that is reserved word / special-char identifier** `order`, `Revenue ($)`, `__EMPTY_3` | parseable with quoting, not without | if mapping aliases unquoted → SQL fails per warehouse | **Blocking** — `validate` must fail until quoted/aliased through IR | Depends on quoting fix — usually MINOR once IR handles it | MINOR (fix), else gate fails |
| 13 | **m:n junction creation / removal** — explicit `order_items(order_id, product_id)` with `PK(order_id, product_id)` vs implicit list fields | changes canonical grain; CRITIQUE §10b `m:n + FK≡PK` | downstream metric grain, dedup, lineage grain all change | **Breaking** | Yes — new junction DDL + FK pair | MAJOR |
| 14 | **Drop FK constraint (soft reference)** — keep column, drop `FOREIGN KEY` | column stays, referential enforcement lifted | canonical load no longer rejects orphans; quality gate may silently pass bad joins | **Breaking** (semantic) — even though DDL is "remove constraint" | Yes — constraint removal is canonical DDL | MAJOR |

**Decision rule distilled:**

> A change is **breaking** iff it can cause any of: (i) canonical load produces wrong or missing values for an existing canonical field, (ii) canonical DDL must change (column/constraint/PK), (iii) downstream consumer query that worked on version N produces different result set / error on version N+1. "Nullable new column" is non-breaking only when it is **unmapped**; any rename, removal of a mapped column, type narrowing, FK retarget, PK change, or m:n grain change is breaking and requires a version bump — even when the new column itself is nullable.

This matches Avro/Protobuf contract guidance: `BACKWARD` / `FORWARD` / `FULL` enforced automatically by a registry (datadef.io, L3), and Datadef's explicit rule: *"Schema Evolution (Avro/Protobuf) — Built-in compatibility modes: backward, forward, full. Schema registry enforces rules automatically."* — https://datadef.io/guides/en/data-contracts

---

## 2. Version-Bump Gate in CI — The Registry Pattern

### 2.1 What the pattern is (why not just "check YAML syntax")

The Data Contracts "registry pattern" (datadef.io, L3; Confluent `major_version` metadata, L3) makes three invariants enforceable in CI:

1. **Single authoritative contract per dataset, pinned and versioned** — one YAML per canonical entity, semver-tagged, stored in `contracts/` (Git is the registry for the wedge; `contracts/<entity>/v{N}.yaml`).
2. **Every change is a compatibility check against the pin** — CI diffs the PR's contract against the previously published / `main` contract.
3. **Breaking change without a version bump = build failure (exit 1)** — the gate turns a silent break into a forced conversation with the named consumers.

Concrete precedents every line below is traceable to:

- **Confluent:** `major_version` metadata property partitions version history into compatibility groups; migration rules (`$sift`, JSONata, CEL) transform across major versions transitively (1→2→3) (L3) https://docs.confluent.io/platform/current/schema-registry/fundamentals/data-contracts.html
- **Datadef:** "Validate contract changes in PR. Reject breaking changes without version bump. Check schema compatibility before merge." (L3) https://datadef.io/guides/en/data-contracts
- **Confluent blog:** "A contract that lives in a wiki is a suggestion. A contract enforced in CI is a guarantee. The producer cannot merge a breaking change without either (a) making it non-breaking (add a default, deprecate before delete), or (b) explicitly bumping the major version and coordinating a migration with the named consumers." (L3) https://stevenzg.com/software-development/data-engineering/data-contracts
- **data-contract-registry (OSS, L3):** `POST /contracts` — register new version, get deterministic compatibility report or `422` with every breaking change `kind` + field name (pypi.org/project/data-contract-registry; GitHub mizcausevic-dev/data-contract-registry). Selective CI gates per `kind` are first-class.
- **dbt-data-contracts (L3):** `dbt-contracts check` — `0` valid, `1` breaking gate failure, `2` error; breaking change removing `amount` while `finance` is a consumer → impact report + exit 1.
- **datalasi (L3):** versioned YAML contracts in Git, `datalasi check contracts/ transactions` → exit 1 if latest version breaks predecessor.
- **DEV Community contract framework (L3):** `Breaking schema change: Major 1.2.0 → 2.0.0; New optional field, relaxed constraint: Minor 1.2.0 → 1.3.0; Documentation: Patch 1.2.0 → 1.2.1` https://dev.to/datanestdigital/data-contract-framework-data-contract-framework-implementation-guide-hhj

### 2.2 SemVer for the mapping contract

The wedge contract's `version` field is **SemVer** on the canonical surface, not on raw source bytes:

```
MAJOR — any breaking change per §1.2 (rename of mapped col, removal, type narrowing,
        FK retarget, PK change, m:n grain change, nullability tightening, drop FK).
        Requires canonical DDL migration and consumer sign-off.
MINOR — backward-compatible addition (new nullable mapped field, widen type, add
        optional filter variable) that old consumers can ignore.
PATCH — documentation, description, `confidence` annotation, `ignored` state without
        field/binding change — no data-path effect.
```

Example per DEV Community table:

| Before | Change | After |
|---|---|---|
| `canonical.orders 1.4.0` | add `discount_code` nullable, unmapped | `1.4.1` (patch) or `1.5.0` if auto-mapped |
| `canonical.orders 1.4.0` | `rev` → `revenue_net` rename (mapped) | `2.0.0` — breaking, even though new col is nullable |
| `canonical.orders 1.4.0` | `customer_id` FK `customers.id → clients.id` | `2.0.0` — FK target change |
| `canonical.order_items 1.0.0` | new junction `PK(order_id, product_id)` m:n | `2.0.0` (new table / grain change) |

### 2.3 When `validate` rejects without a version increment — the gate rule

`validate` is the CLI that CI runs on every PR touching `contracts/` or `mapping/` YAML. Pseudocode — deterministic, no LLM:

```
validate(contract_path, base=origin/main):
  base_contract = load(base, contract_path) or registry.latest
  pr_contract   = load(PR, contract_path)
  if base_contract is None:
    # new entity — no compatibility check, just structural + execution validate
    return execution_validate(pr_contract)

  diff = compare_contracts(base_contract, pr_contract)
         # fields: added / removed / renamed / retyped / nullability / FK target /
         #         PK change / m:n grain / reserved-word quoting

  breaking_kinds = diff.kinds that are BREAKING per §1.2
                   # remove-mapped, rename-mapped, type-narrow, FK-retarget, PK-change, m:n

  pr_version  = pr_contract.version
  base_version = base_contract.version

  # Registry rule — datadef / Confluent major_version partitioning
  if breaking_kinds not empty and pr_version.major == base_version.major:
    emit REPORT breaking_kinds grouped by field + kind
          "Breaking changes detected without major version bump:"
          "  - RENAME mapped column `rev` → `revenue_net` (field `revenue` loses binding)"
          "  - FK target change `orders.customer_id: customers.id → clients.id`"
          "  Bump version from 1.4.0 → 2.0.0 (MAJOR) or make the change non-breaking"
          "  (add DEFAULT, deprecate-before-delete, preserve alias)."
    exit 1  # blocks merge — GitHub required check

  if pr_version <= base_version:
    emit "Version must increase (MAJOR/MINOR/PATCH) on any contract change."
    exit 1

  if breaking_kinds not empty and pr_version.major > base_version.major:
    # breaking but properly bumped — warn + require consumer approval stamp
    emit impact REPORT — consumers whose checks reference these fields
          "Breaking change acknowledged via major bump — requires consumer ACK"
    # optional: check `approvals: [finance, ops]` signed in PR
    exit 0 only if ACK present else exit 1

  if breaking_kinds empty and pr_version.major > base_version.major:
    emit "Major bump without breaking change — did you mean MINOR/PATCH?"
    exit 1  # avoid major inflation

  # Non-breaking path — still execute-validate generated SQL before passing
  return execution_validate(pr_contract)
```

This is the `data-contract-registry` pattern exactly: `422 with every breaking change called out by field name and kind` where each `kind` is routable to a selective CI gate — mizcausevic-dev/data-contract-registry (L3). And `dbt-data-contracts` standardized exit codes `0 / 1 / 2` (L3).

**Blast radius** (when available) is derived from lineage: which downstream checks/marts read the breaking field — a `ViolationAttributor` (Natnael-Alemseged/data-contract-enforcer, L4) style: registry-first blast radius + lineage BFS + `git blame`. Minimum wedge implementation: list every mapping/contract/check file that references `field` by name.

### 2.4 CI wiring (GitHub Actions sketch — copyable)

```yaml
# .github/workflows/contract-gate.yml
name: contract-gate
on:
  pull_request:
    paths: ["contracts/**", "mappings/**", "canonical/**"]
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }            # need base for diff
      - uses: actions/setup-python@v5
        with: { python-version: "3.12" }
      - run: pip install -e ".[contracts]"
      - name: validate contracts vs base
        run: |
          # exits 0/1/2 — required check blocks merge on 1
          python -m wedge contract check --base origin/main --diff-report
      - name: execution validate (ephemeral DuckDB + BigQuery dry-run)
        run: |
          python -m wedge validate --ephemeral-duckdb --dialect-matrix all
      # Optional: publish report as PR comment
      - uses: actions/upload-artifact@v4
        with: { name: validate-report, path: .wedge/validate-report.json }
```

The gate is **shift-left** (Datadef Guides, L3): not a nightly job, not a warehouse-side monitor, but a PR-time rejection. Datadef phrasing adopted verbatim: *"Enforce data contracts through automated validation in CI/CD pipelines, schema registries that reject non-compliant changes, runtime validation at ingestion points, and monitoring dashboards that track SLA compliance. The key is shifting enforcement left to catch issues before they reach production."*

### 2.5 "Breaking without bump" — concrete rename example that CI must reject

PR: source CSV header `rev` renamed to `revenue_net` (same semantics, new identifier).

```yaml
# contracts/orders/v1.4.0.yaml (base)
version: 1.4.0
fields:
  - name: revenue
    source: rev                  # mapped
    type: DECIMAL(12,2)
    nullable: false
  - name: customer_id
    source: cust_id
    type: UUID
    fk: { target: customers.id }

# PR — mappings/orders.yaml (no version bump, no alias)
version: 1.4.0                   # ← unchanged — gate must fire
fields:
  - name: revenue
    source: revenue_net          # ← rename, old `rev` vanished
    type: DECIMAL(12,2)
```

CI output must be (deterministic, field+kind):

```
$ python -m wedge contract check --base origin/main
✗ Breaking changes without major version bump (1.4.0 → 1.4.0 unchanged):
  kind=RENAME_MAPPED_FIELD  field=revenue  detail="source `rev` removed, `revenue_net` added — binding lost"
  consumer impact: canonical.orders.revenue (downstream: quality check revenue_not_null, mart revenue_by_region)
  fix: bump to 2.0.0 or add alias: source: { any_of: [rev, revenue_net] } with deprecation window

Exit 1 — merge blocked.
```

If the author bumps correctly:

```yaml
version: 2.0.0  # MAJOR
fields:
  - name: revenue
    source: revenue_net
    aliases: [rev]               # compat shim for replay of old drops
    type: DECIMAL(12,2)
```

CI passes only with consumer ACK (e.g., `approvals: [finance]`) or with `aliases` covering the transitive window (FULL_TRANSITIVE analogue).

---

## 3. Compatibility Matrix for Mapping YAML — Which Changes Need a Version Bump

The matrix is the single artifact the `validate` gate and the RFC review look at. It is the mapping-YAML counterpart of Confluent's `BACKWARD / FORWARD / FULL` table, but scoped to YAML contract semantics (derived from datadef.io contract taxonomy + GAPS §1.2 row).

**Columns:** `Scope` = where the change is authored; `Bump` = minimum SemVer bump; `Migration` = whether canonical DDL must change; `Gate` = which `validate` sub-check fires.

| Scope | Change description | Bump | Canonical DDL migration? | Gate check |
|---|---|---|---|---|
| **Field binding** | Add new **unmapped** source column | PATCH / none | No | `added-unmapped` — info only |
|  | Map new nullable source column to **new** canonical field | MINOR | Add column (and optional FK) | `added-mapped-field` |
|  | Map new non-nullable source column (no default) to new canonical field | **MAJOR** | Add `NOT NULL` column — backfill required | `added-not-null` |
|  | **Remove/DROP** mapped source column (field deleted) | **MAJOR** | Alter canonical field nullable/default or drop | `removed-mapped-field` |
|  | **Rename** mapped source column (`rev` → `revenue_net`) | **MAJOR** | Alias or migration; if canonical field also renames then DDL rename | `renamed-mapped-field` (critical) |
|  | Reorder columns | PATCH | No | `reorder` |
| **Field type** | Widen string / `INT → DECIMAL` / add timezone | MINOR | Optional widen | `type-widen` |
|  | Narrow `DECIMAL → INT` / remove timezone / `VARCHAR → DATE` | **MAJOR** | Narrow column + possible data clean | `type-narrow` |
|  | Change canonical field type itself | **MAJOR** | `ALTER TABLE ... ALTER COLUMN ... TYPE` | `canonical-type-change` |
| **Nullability** | `NOT NULL → NULLABLE` (relax) on mapped field | **MAJOR** (conservative — consumers assumed non-null) | `ALTER COLUMN DROP NOT NULL` | `nullability-relax` |
|  | `NULLABLE → NOT NULL` (tighten) | **MAJOR** | `ALTER COLUMN SET NOT NULL` + validation | `nullability-tighten` |
| **Constraints** | **FK target change** `customers.id → clients.id` | **MAJOR** | `DROP CONSTRAINT … ADD CONSTRAINT … REFERENCES new_target` | `fk-retarget` |
|  | FK column type mismatch after retarget | **MAJOR** | Must conform or fail | `fk-type-mismatch` |
|  | Drop FK constraint (column stays) | **MAJOR** | Drop constraint semantic | `fk-dropped` |
|  | Add new FK | MINOR | Add FK | `fk-added` |
|  | PK change (surrogate → composite) | **MAJOR** | PK rebuild + index rebuild | `pk-change` |
|  | `FK≡PK` drift (junction PK half is FK, target changed) | **MAJOR** | Junction PK + FK rebuild | `fk-eq-pk-drift` |
| **Cardinality / grain** | New **m:n junction** table (`order_items(order_id, product_id)`) | **MAJOR** | New junction DDL with composite PK + dual FK | `mn-junction-added` |
|  | Remove junction / collapse to 1:N | **MAJOR** | Drop junction | `mn-junction-removed` |
|  | Junction PK change (composite widen/narrow) | **MAJOR** | Alter junction PK | `mn-pk-change` |
| **Identifier hygiene** | Add reserved-word / special-char column (`order`, `Revenue ($)`, `__EMPTY_3`) without quoting through IR | **BLOCK** until IR quoting pass | No DDL — mapping alias/quoting | `reserved-identifier-unquoted` |
|  | Fix quoting via IR alias (quoted passthrough) | MINOR / PATCH | No | `identifier-quoted` |
| **Rule / policy** | Change `confidence`, add annotation, toggle `ignored: false→true` | PATCH | No | info |
|  | Change `deterministic condition` expression (YAML predicate) | MINOR (new filter) or MAJOR if it drops rows for an existing canonical field | May alter canonical population | `condition-change` |
|  | Change filter/variables scoping | MINOR or MAJOR per population effect | Depends | `filter-scope-change` |

**How `validate` maps each row to a `kind` for selective gates:**

Borrowed from `data-contract-registry`'s structured `kind` set (L3). Each kind below carries `field`, `canonical_target`, `old_binding`, `new_binding`:

```
RENAME_MAPPED_FIELD, REMOVED_MAPPED_FIELD, ADDED_NOT_NULL, TYPE_NARROW,
CANONICAL_TYPE_CHANGE, NULLABILITY_TIGHTEN, NULLABILITY_RELAX,
FK_RETARGET, FK_DROPPED, FK_TYPE_MISMATCH, PK_CHANGE, FK_EQ_PK_DRIFT,
MN_JUNCTION_ADDED, MN_JUNCTION_REMOVED, MN_PK_CHANGE, RESERVED_UNQUOTED
```

Selective gates: a team can block deploys on `FK_RETARGET` while allowing `TYPE_WIDEN` through warning. The registry `422` body groups by `kind` (mizcausevic-dev/data-contract-registry — L3) exactly enables this.

**Cross-reference to source-of-truth guarantees (L3-L4):**

- Canonical contract pinned & versioned in `contracts/` (registry/GIT/contracts table) with a single authoritative expectation both engines validate against — cross-engine-reconciliation `schema-validation-pre-checks` (L4) https://www.cross-engine-reconciliation.org/data-extraction-hashing-workflows/schema-validation-pre-checks/
- `dlt` schema contracts modes `evolve / freeze` (L3) — the wedge corresponds to `freeze` with `FULL_TRANSITIVE` for canonical surface; `evolve` is only for staging landing zones https://dlthub.com/docs/general-usage/schema-contracts
- Every PK gets `unique + not_null`, every FK gets `relationships + not_null`, every closed set gets `accepted_values` — schema-tests canon (L4) https://amirulislamalmamun.com/practice/data-engineering/concepts/045-schema-tests/ — see §4 implementation.

---

## 4. m:n + FK≡PK Handling in Validation — How `canonical.orders.customer_id` Retarget Propagates

### 4.1 The shape

The wedge's canonical model is relationally constrained (CRITIQUE §10b — explicitly `m:n + FK≡PK`):

```
customers(id PK, …)              ← target A
clients(id PK, …)                ← target B (after split)
orders(id PK, customer_id FK → customers.id, …)         1:N
order_items(order_id FK → orders.id,
            product_id FK → products.id,
            PRIMARY KEY (order_id, product_id))          ← junction, PK is pair of FKs (FK≡PK)
tags: products ↔ categories via product_categories(product_id FK, category_id FK, PK(product_id, category_id))
```

Prisma 8 model — `@@id([postId, tagId])` enforces one record per pair; implicit m:n without a junction is rejected (L3) https://www.prisma.io/docs/orm/v8/data-modeling/relational-databases . Schemity note: when the associated context is later extracted into its own service/schema, an FK column that was half of a PK forces a harder split (migration cost multiplies) (L4) https://schemity.com/blog/many-to-many-shouldnt-mean-hand-building-the-junction-table/ . EF Core `PostTag` shows the same composite-PK junction DDL (L3).

### 4.2 What "FK target change propagates" means — the failure taxonomy

Change: product's owning entity is refactored — `canonical.orders.customer_id` that previously `REFERENCES customers(id)` is retargeted to `clients(id)` (or `customer_master(id)` after Satel split).

| Propagation vector | Symptom | Detection by `validate` |
|---|---|---|
| **Referential integrity** | old `customer_id` values missing in new target → orphan rows; load fails or silently inserts orphans if FK lifted | FK target check: target table catalog diff + FK catalog (`information_schema.table_constraints` / `pg_constraint`) diff |
| **FK≡PK half goes stale** | junction `order_items.product_id REFERENCES products(id)` — if `products` is split into `products` + `product_variants`, one FK half now points to wrong granularity | `fk-eq-pk-drift` kind — `validate` diffs junction `PRIMARY KEY` column list + FK target list |
| **Type / nullability divergence after retarget** | `customers.id UUID` vs `clients.id VARCHAR` → FK type mismatch; or `clients.id` allows nulls | `fk-type-mismatch` + `nullability` check; see `dlt` nuance on `freeze` vs `evolve` (L3) |
| **Lineage & downstream mart grain** | downstream mart `revenue_by_customer` that joined `orders ⋈ customers` now joins `orders ⋈ clients` → row multiplication or disappearance; metric lineage breaks | lineage BFS (data-contract-enforcer — L4): enumerate consumers of `orders.customer_id` by name reference; mark impact; if junction grain changes, mart's GROUP BY grain flag |
| **Audit / lineage keys shift** | `__dpone__parent_id` (SHA-256 parent row identity for normalized nested data) in dpone lineage (L4) changes when PK composition changes | `validate` asserts `__dpone__*` namespace isolation — user data must not create `__dpone__*` columns; lineage check diffs PK hash inputs (L4) https://github.com/PaulKov/dpone/blob/master/docs/load-lineage.md |
| **Exactly-once / idempotent write** | retry replays that keyed deduplication on `(order_id, product_id)` may collide differently after PK redefinition if old key allowed duplicates | concurrency/consistency check (§10a) — atomic unit becomes junction row identity |

Percona `pt-online-schema-change` notes are the operational precedent: atomically renaming the original and new tables does **not** work when foreign keys refer to the table; the tool must `DROP` + `ADD` FK on every child before cutover (`--alter-foreign-keys-method rebuild_constraints` is the consistent choice) (L3) https://docs.percona.com/percona-toolkit/pt-online-schema-change.html / https://www.percona.com/blog/how-pt-online-schema-change-handles-foreign-keys/ . This is the correct mental model for canonical DDL migration: a single `ALTER` that rebuilds every child FK that references the moved PK — not a "view swap."

### 4.3 Validation procedure for FK≡PK + m:n drift (what `validate` actually does)

`validate` runs before any canonical load. Its inputs are: (a) previous canonical catalog snapshot (from `contracts/` DDL or live `information_schema`), (b) PR's proposed canonical DDL (derived from YAML IR), (c) mapping bindings.

```
def validate_fk_and_junctions(base_ddl, pr_ddl, bindings):
    base_catalog = parse_ddl(base_ddl)   # tables, PKs, FKs, types, nullability
    pr_catalog   = parse_ddl(pr_ddl)

    fks_base = {fk.name: fk for fk in base_catalog.foreign_keys}
    fks_pr   = {fk.name: fk for fk in pr_catalog.foreign_keys}

    for name in fks_base.keys() | fks_pr.keys():
        if name not in fks_base:  continue  # FK_ADDED — MINOR (log)
        if name not in fks_pr:    emit(FK_DROPPED, level=BLOCK, advice="MAJOR — FK removed")
        if fks_base[name].target != fks_pr[name].target:
            # the core case: orders.customer_id retarget
            if fk_is_half_of_pk(fks_base[name], base_catalog) or fk_is_half_of_pk(fks_pr[name], pr_catalog):
                emit(FK_EQ_PK_DRIFT, level=BLOCK, major=True,
                     detail=f"{name}: {fks_base[name].target} → {fks_pr[name].target} and FK is component of PK")
            else:
                emit(FK_RETARGET, level=BLOCK, major=True,
                     detail=f"{name}: {fks_base[name].target} → {fks_pr[name].target}")

        # type / nullability drift on either leg
        if column_type(pr_catalog, fks_pr[name].source_col) != column_type(base_catalog, fks_base[name].source_col):
            emit(FK_TYPE_MISMATCH, ...)

    # Junction-specific pass
    for tbl in junc_tables(pr_catalog):   # tables whose PK is composite of FK cols
        pk_cols = pr_catalog.pk(tbl)
        fk_cols = fk_sources_for(tbl, pr_catalog)
        if set(pk_cols) == set(fk_cols):
            # FK≡PK invariant — any PK column that is also an FK source
            # changing FK target invalidates the grain
            compare_junction(base_catalog, pr_catalog, tbl)

    # Impact propagation pass — blast radius
    impacted = lineage_bfs(bindings, changed_field="orders.customer_id")
               # lineage-bfs mirrors data-contract-enforcer's registry-first BFS (L4)
               # every downstream asset whose query references orders.customer_id
    emit(IMPACT_REPORT, impacted, advice="consumer ACK required if MAJOR")
```

Helper `fk_is_half_of_pk`:

```python
def fk_is_half_of_pk(fk, catalog):
    tbl = catalog.table(fk.source_table)
    return fk.source_col in catalog.pk(tbl)   # junction detection; also catches composite FK≡PK
```

This must run **per migration** (the registry "compatibility group" via `major_version` metadata — Confluent L3 — is the correct scope to bound transitive checks). Transitive mode (`BACKWARD_TRANSITIVE`) validates `N+1` against **all** prior versions, not just latest — Apicurio (L3) — which is where an old `rev` alias that was removed two versions ago surfaces as a re-break if replay of old drops still needs it.

### 4.4 DTO / migration sketch (canonical DDL that `validate` emits and then execution-tests)

For `orders.customer_id: customers.id → clients.id`:

```sql
-- generated migration for MAJOR 1.x → 2.0.0 (Postgres flavour — §5.2)
ALTER TABLE canonical.orders
  DROP CONSTRAINT fk_orders_customer_id,      -- rebuild_constraints analogue
  ADD  CONSTRAINT fk_orders_customer_id
       FOREIGN KEY (customer_id) REFERENCES canonical.clients(id)
       DEFERRABLE INITIALLY DEFERRED;        -- Django ticket pattern for deferred FK add (L3, doctrine/Doctrine edge note)

-- if FK≡PK junction involved (order_items)
ALTER TABLE canonical.order_items
  DROP CONSTRAINT pk_order_items,            -- PK rebuild required when PK half retargets
  DROP CONSTRAINT fk_order_items_product_id,
  ADD  CONSTRAINT pk_order_items            PRIMARY KEY (order_id, product_id),  -- preserved shape
  ADD  CONSTRAINT fk_order_items_product_id
       FOREIGN KEY (product_id) REFERENCES canonical.products(id);
-- index maintenance precedes FK per GitLab docs (L3): add concurrent index before FK
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_order_items_product_id ON canonical.order_items(product_id);

-- FK validation suite (auto-generated, runs in execution-validate phase §6)
-- every PK gets unique+not_null, every FK gets relationships+not_null (dbt tests canon, L4)
-- implemented as Soda/CanonIQ-style generated rules (see CanoniQ, L4):
-- https://github.com/Buchiexplores/canoniq
```

The FK test harness mirrors the industry bias: "The single rule that catches the most bugs: every primary key on every table gets `unique` and `not_null`. Every foreign key gets `relationships` and (almost always) `not_null`." — Amirul Islam (L4) https://amirulislamalmamun.com/practice/data-engineering/concepts/045-schema-tests/

---

## 5. Null / Case / Quoting Variance per Warehouse — The Dialect Matrix That Kills "Any Warehouse" Claims

CRITIQUE §1's kill cases — `SELECT Revenue ($) AS revenue` without quoting and `SELECT order FROM ...` reserved-word failure — are not edge cases; they are the predictable output of IR-less YAML→SQL. The matrix below is the contract the compiler's IR + per-warehouse lowering must satisfy. `validate` execution-tests every row (ephemeral DuckDB + BigQuery/Trino dry-run via sqlglot — §5.3, §6).

### 5.1 Scope — three warehouses in plan + two for transpilation parity

Plan (§2): DuckDB, Postgres, BigQuery. Matrix includes Snowflake and Trino/Presto for sqlglot parity (30+ dialects — sqlglot, L3) and for the pre-migration team that still runs Snowflake.

### 5.2 Identifier / quoting / case / reserved words

| Behaviour | DuckDB | Postgres | BigQuery | Snowflake | Trino/Presto |
|---|---|---|---|---|---|
| **Quoted char** | `"` (double quote) — ANSI | `"` — folds to lower | `` ` `` backtick — but `"` also accepted in some modes; canonical is `` ` `` | `"` — QUOTED_IDENTIFIERS_IGNORE_CASE session param controls case (L3) https://docs.snowflake.com/en/en/sql-reference/identifiers-syntax | `"` |
| **Unquoted folding** | **preserves case but case-insensitive** — "fully case insensitive throughout the system but preserving their case" (L3) https://duckdb.org/docs/lts/sql/dialect/postgresql_compatibility | **folds to lower** — `FOO` ≡ `foo` ≡ `"foo"`; `"Foo"` distinct (L3) https://www.postgresql.org/docs/19/sql-syntax-lexical.html (incompatible with SQL standard which folds to upper) | identifiers are case-sensitive; reserved keywords must be quoted with `` ` `` (L3) https://docs.cloud.google.com/bigquery/docs/reference/standard-sql/lexical | unquoted → UPPER; quoted → preserved case; `QUOTED_IDENTIFIERS_IGNORE_CASE=TRUE` makes lowercase quoted identifiers behave as upper (L3) | unquoted → lower |
| **Quoted case sensitivity** | quoted identifiers **also case-insensitive** — unlike Postgres (L3) https://duckdb.org/docs/lts/sql/dialect/keywords_and_identifiers | quoted = case-sensitive | backtick-quoted = case-sensitive | default quoted = case-sensitive (param-dependent) | quoted = case-sensitive |
| **Reserved-word as identifier** | `SELECT 123 AS SELECT` → fail; `SELECT order FROM t` → syntax error — same class as MetricFlow GH #2058 where `SELECT "order" AS order` still broken because the *alias* was unquoted (L3) | same — must quote | same — must backtick-quote `FROM`, `ORDER`, `WHERE` | same — must quote | same |
| **Illegal-char identifier** `Revenue ($)` | must quote — `Revenue ($)` illegal unquoted (whitespace + `()` + `$`) | same | same (backtick) | same | same |
| **Starts-with-digit / `col_14` numeric suffix** | `1col` invalid unquoted; `col_14` fine (L3) | same | same | same | same |
| **Empty / auto-named `__EMPTY_3`** | fine if quoted; mapping IR must not assume natural language header | same | same | same | same |
| **OSI/Snowflake YAML fallback** | — | — | — | `SNOWFLAKE` dialect preferred, fallback `ANSI_SQL`; absent entries silently skipped (L3) https://docs.snowflake.com/en/sql-reference/stored-procedures/system_create_semantic_view_from_osi_yaml → so a missing dialect entry is a silent omission bug `validate` must catch | — |
| **Alias quoting** | quoting the column is insufficient if the alias is unquoted (`SELECT "order" AS order` still fails — GH #2058, L3) | same | same (backtick alias) | same | same |

**IR lowering rule (non-negotiable):** YAML identifiers are stored in IR as **semantic names**; every `SELECT … AS <alias>` and every column reference is emitted through an `Identifier(quoted=True)` node whose `quote_char` is dialect-selected by the lowering pass (`"` for DuckDB/Postgres/Snowflake/Trino, `` ` `` for BigQuery). `validate` asserts that every generated identifier that is a reserved word or contains a non-`[A-Za-z0-9_]` character or starts with a digit is quoted in the dialect's quoted form — unconditionally. This is the missing "IR" from CRITIQUE §1.

### 5.3 Null / three-valued logic / aggregation / string dialect

| Behaviour | DuckDB | Postgres | BigQuery | Snowflake | Why it gates `validate` |
|---|---|---|---|---|---|
| **Any comparison with NULL** | returns `NULL` — including `NULL = NULL` (L3) https://duckdb.org/docs/lts/sql/data_types/nulls | same (SQL standard three-valued logic) — Modern SQL `IS [NOT] DISTINCT FROM` (L3) https://modern-sql.com/feature/is-distinct-from | same | same | mapping filters that do `col <> 3` on a nullable column silently drop null rows; correct is `IS DISTINCT FROM 3` — `validate` warns and compiler option `nullSafeComparisons: prefer_is_distinct_from` |
| **Null-safe equality** | `IS NOT DISTINCT FROM` supported (dialect matrix, L4) https://www.biersons.com/posts/postgres-distinct-from-and-comparing-potetially-null-values/ ; `COALESCE(col, 0) <> 3` is non-SARGable (index not used) — SO/DBA (L4) https://stackoverflow.com/questions/33828329/why-use-is-distinct-from-postgres ; DuckDB docs explicitly call out `IS DISTINCT FROM` + `IS (NOT) DISTINCT FROM` (L3) https://duckdb.org/docs/lts/sql/expressions/comparison_operators | `IS DISTINCT FROM` since PG9.4; `IS NOT DISTINCT FROM` | `IS DISTINCT FROM` supported (Medium — L4) | `IS DISTINCT FROM` | `validate` prefers `IS DISTINCT FROM` over `COALESCE`-masking |
| **`COALESCE` / `IFNULL`/`NVL`/`ISNULL`** | `COALESCE(col, 0)`; `IFNULL` synonym | `COALESCE` (standard) | `IFNULL(col, 0)` (2-arg) | `IFNULL` / `NVL` synonyms | transpiled via sqlglot — do not hand-mint per dialect |
| **Aggregates over all-NULL input** | `STDDEV`, `VAR`, etc → `NULL` (BigQuery docs variant); `COUNT(*)` → 0 regardless | same semantics — L3 aggregated function tables show `NULL` return when ignored inputs exhausted | same | same | drift checks that `STDDEV(revenue)` without `FILTER (WHERE revenue IS NOT NULL)` may be `NULL` vs 0 — `validate` seeds non-null fixture |
| **`STDDEV_SAMP` single non-null → 0?** | DuckDB `STDDEV` on single input returns 0; Postgres `stddev_samp` on single non-null returns `NULL` — cross-engine variance the cross-engine reconciliation pre-checks flag (L4) cross-engine-reconciliation harness fingerprint — L4 https://www.cross-engine-reconciliation.org/data-extraction-hashing-workflows/schema-validation-pre-checks/ + wmk Python canonicalizer UDF pushdown (L4) | — | — | — | `validate` must pin the **normalization rules** that collapse `NUMBER/DECIMAL/NUMERIC` and `VARCHAR/STRING/TEXT` to canonical types alongside the contract — exactly the "cross-engine type map agreed" pre-check (L4) |
| **Date / timestamp parsing** | `DATE`, `DATETIME`, `TIMESTAMP` — per-dialect function names diverge (`STRPTIME` vs `TO_DATE` vs `PARSE_DATE`) | `::DATE`, `TO_DATE` | `PARSE_DATE`, `PARSE_TIMESTAMP` | `TO_DATE`, `TO_TIMESTAMP` | type-narrow detection (§1.2 row 7) — is `VARCHAR → DATE` safe? `validate` must know dialect date function |
| **Regex flavour** | `REGEXP_MATCHES` (Postgres), `REGEXP_CONTAINS` (BigQuery), `regexp_matches` (DuckDB) — different | — | — | — | currency parsing `^\$?([\d,]+\.?\d*)$` (CRITIQUE §2) — `validate` tests the regex in both source dialect and canonical dialect |
| **Collation / case in grouping** | DuckDB case-insensitive grouping may collapse `SKU-Alpha-001` vs `sku-alpha-001` that BigQuery treats as distinct | Postgres case-sensitive | BigQuery case-sensitive | Snowflake UPPERCASE folding | `validate` fixture includes mixed-case probe rows per entity key |

**Sqlglot transpilation note:** `parse_one(sql, read=dialect).sql(write=dialect)` (sqlglot — L3) https://sqlglot.com/sqlglot.html — and `transpile(sql, read="spark", write="duckdb")`. But: *"The parser is intentionally lenient, so it can accept queries that a real engine would reject. SQLGlot is a transpiler, not a validator. A query that parses successfully may still fail at execution time."* — sqlglot docs (L3). Therefore `validate` must do **parse+transpile AND execute** (ephemeral DuckDB), not just transpile — §6.

### 5.4 Matrix artifact — dialect lowering table for the mapping compiler

| Mapping YAML concern | DuckDB lowering | Postgres lowering | BigQuery lowering |
|---|---|---|---|
| Identifier quoting for `Revenue ($)` / `order` / `__EMPTY_3` | `"` — `SELECT "Revenue ($)" AS "revenue"` | `"` — `SELECT "Revenue ($)" AS "revenue"` (lower-folded name, but quoted form preserved) | `` ` `` — `` SELECT `Revenue ($)` AS `revenue` `` |
| Alias quoting (MetricFlow #2058 fix) | quote alias even when column is quoted | quote alias | backtick alias |
| Null-safe inequality `col <> 'USA'` | `IS DISTINCT FROM 'USA'` preferred | same | same |
| Coalesce default `col → 0` | `COALESCE(col, 0)` | `COALESCE(col, 0)` | `IFNULL(col, 0)` transpiled |
| Type canonicalization | `VARCHAR → TEXT` collapsed | `STRING → VARCHAR` collapsed | `STRING → VARCHAR` collapsed |
| Date parse | `TRY_CAST` guarded (defensive if DuckDB type coercion bug PR #24167 hits) | — | `SAFE_CAST` |

This matrix is checked into `contracts/dialect-matrix.yaml` (or `SCHEMA-EVOLUTION-POLICY.md` appendix) and is asserted by `validate --dialect-matrix all`.

---

## 6. Test Harness — Executes Generated SQL Against an Ephemeral Warehouse (Validate ≠ Syntax-Only)

CRITIQUE §1 is explicit: MetricFlow's `dbt sl validate` did **not** catch `invalid identifier` or `ambiguous column name 'YELLOW'` until query time (GH #12512, GH #1930 — L3); `dbt sl validate` is stale until you forget `dbt parse` (L3). `validate` that only checks YAML syntax is theater. The wedge's `validate` must **parse → transpile → execute on an ephemeral warehouse** on every PR.

### 6.1 Architecture — two layers, one gate, no BigQuery spend

```
YAML (contracts/ + mappings/)
  │
  ├─► IR (semantic manifest — YAML→IR parser with JSON Schema)
  │     │
  │     ├─► layer 1: structural + IR checks (no warehouse needed)
  │     │          - JSON Schema of YAML (required fields, allowed condition grammar)
  │     │          - manifest consistency (field → column binding, PK→FK target exists,
  │     │            `expr` vs column-level dimension staleness per GH #12512)
  │     │          - reserved-word / illegal-char quoting audit (fail if any unquoted)
  │     │          - compatibility diff vs base (§2) — kind-classified
  │     │          - sqlglot parse + transpile smoke per dialect (BigQuery/Snowflake
  │     │            dry-run without creds — §6.3): catches dialect-unsupported syntax
  │     │
  │     └─► layer 2: execution harness (ephemeral DuckDB — real query engine)
  │                - materialize ephemeral DuckDB per `validate` run (in-memory or tmpfile)
  │                - DDL: create staging + canonical tables mirroring IR (all FK/PK/nullability)
  │                - seed fixtures: per-field probes (null, empty string, mixed-case,
  │                │              locale currency "$1,234" vs "1 234,00 €", reserved-word alias,
  │                │              orphan FK value, duplicate PK pair for junction)
  │                - compile IR → SQL (one SELECT per mapping rule, plus canonical load JOIN)
  │                - PREPARE + EXPLAIN + EXECUTE each SQL on DuckDB — asserts no error,
  │                │  correct row count, correct null distribution, FK test suite passes
  │                - lineage BFS impact — enumerate downstream consumers of changed fields
  │                - sufficiency gate: exit 1 if any of (a) SQL parse error, (b) execution
  │                │  error, (c) FK/PK/not_null test failure, (d) reserved-word unquoted
  │
  └─► CI exit code: 0 pass / 1 breaking-gate failure / 2 infra error (dbt-data-contracts convention)
```

This mirrors the **di0** gate model (mmurakaru/di0 — L3): `dialect: snowflake, validation: sqlglot-offline, execution: noop` — execution always gated on validation, authoring is optional. And the **cross-engine reconciliation** guard: "Canonical contract pinned and versioned … cross-engine type map agreed … pinned alongside the contract" plus the **wmk** pattern — one Python canonicalizer UDF registered in both engines (L4).

### 6.2 What "proves validate ≠ syntax-only" — the three probe suites

The P0 green criterion (GAPS §G-YAML-01) is explicit: *a quoted-reserved-word column compiles and `validate` executes the generated SQL on ephemeral DuckDB (and BigQuery-dialect dry-run), catching `ORDER` alias and `Revenue ($)` quoting bugs — not just YAML syntax.* The harness codifies three fixture sets:

#### Suite A — Quoting & reserved-word probes (catches CRITIQUE §1 kills)

```python
@pytest.mark.parametrize("col,alias", [
    ("Revenue ($)", "revenue"),   # illegal chars + whitespace
    ("order",       "order_"),    # reserved word
    ("__EMPTY_3",   "empty_3"),   # auto-named header
])
def test_quoted_identifier_compiles_and_executes(col, alias, ephemeral_duckdb):
    yaml = f"""
version: 1.5.0
fields:
  - {{ name: revenue, source: "{col}", type: DECIMAL }}"""  # col is the tricky one
    ir = parse(yaml)                          # JSON Schema + IR
    duck_sql = compile(ir, dialect="duckdb")  # SELECT "Revenue ($)" AS "revenue" ...
    bq_sql   = compile(ir, dialect="bigquery")# SELECT `Revenue ($)` AS `revenue` ...

    # layer 1 — sqlglot dry-run (no DuckDB needed) — catches BigQuery dialect gaps
    assert parses(duck_sql, dialect="duckdb")
    assert parses(bq_sql,   dialect="bigquery")   # backtick form
    assert "order" not in unquoted_identifiers(duck_sql)  # alias also quoted
    assert sqlglot.transpile(bq_sql, read="bigquery", write="duckdb")  # round-trip

    # layer 2 — ephemeral DuckDB execution (real engine) — proves it runs
    ephemeral_duckdb.execute(f'CREATE TABLE stg("{col}" VARCHAR)')
    ephemeral_duckdb.execute(f'INSERT INTO stg VALUES (\'12.34\'), (NULL)')
    ephemeral_duckdb.execute(duck_sql)   # was: SELECT Revenue ($) AS revenue → syntax error w/o quotes
    rows = ephemeral_duckdb.fetchall()
    assert len(rows) == 2
```

Failure mode this suite proves caught:

```sql
-- WITHOUT IR quoting (bug):
SELECT Revenue ($) AS revenue FROM stg   -- DuckDB/Postgres/BigQuery: syntax error

-- WITH IR quoting (fixed):
-- duckdb:
SELECT "Revenue ($)" AS "revenue" FROM stg
-- bigquery:
SELECT `Revenue ($)` AS `revenue` FROM stg
```

And the alias form of the MetricFlow #2058 bug:

```sql
-- BUG alias unquoted even when column quoted:
SELECT "order" AS order FROM stg   -- Postgres: alias order is unquoted → still syntax error

-- FIX both quoted:
SELECT "order" AS "order_" FROM stg   -- or canonical alias without reserved word
```

#### Suite B — Type / null / FK≡PK execution probes

```python
def test_fk_retarget_breaks_ephemeral_load(ephemeral_duckdb):
    # canonical.orders.customer_id FK was customers.id → now clients.id
    ephemeral_duckdb.execute("CREATE TABLE customers(id UUID PRIMARY KEY, name TEXT)")
    ephemeral_duckdb.execute("CREATE TABLE clients  (id UUID PRIMARY KEY, name TEXT)")
    ephemeral_duckdb.execute("CREATE TABLE orders   (id UUID PRIMARY KEY, customer_id UUID NOT NULL, "
                             "  FOREIGN KEY(customer_id) REFERENCES customers(id))")
    # seed: one order referencing a customer that does not exist in clients
    orphan_id = uuid4()
    ephemeral_duckdb.execute("INSERT INTO customers VALUES (?, 'Acme')", [uuid4()])
    ephemeral_duckdb.execute("INSERT INTO orders VALUES (?, ?)", [uuid4(), orphan_id])

    # IR that retargets FK to clients — execution must fail relationship test
    pr_ddl = "ALTER TABLE orders DROP CONSTRAINT fk_orders_customer_id; " \
             "ALTER TABLE orders ADD CONSTRAINT fk_orders_customer_id FOREIGN KEY(customer_id) REFERENCES clients(id)"
    ephemeral_duckdb.execute(pr_ddl)  # succeeds DDL

    # generated FK test (Amirul canon: every FK gets relationships + not_null)
    fk_test_sql = """
        SELECT o.customer_id
        FROM orders o LEFT JOIN clients c ON o.customer_id = c.id
        WHERE c.id IS NULL
    """
    orphans = ephemeral_duckdb.execute(fk_test_sql).fetchall()
    assert len(orphans) > 0   # → validate fails: FK retarget is breaking, orphan set non-empty
```

Suite B also covers:

| Probe | What execution proves | Fixture |
|---|---|---|
| `TYPE_NARROW` `DECIMAL → INTEGER` | `TRY_CAST` / `SAFE_CAST` path does not truncate | seed `123.45` → assert `123` vs error |
| `TYPE_NARROW` `VARCHAR → DATE` | locale-sensitive parse succeeds only when quoting + format correct | seed `"$1,234.00"` with regex `^\$?([\d,]+\.?\d*)$` — empty-string match guard |
| `NULLABILITY_TIGHTEN` | canonical load rejects more rows (visible via `WHERE col IS NULL` on load view) | seed every field with a NULL probe row; assert load rejects null row |
| `FK≡PK junction` | junction PK `(order_id, product_id)` — FK half staleness | seed duplicate PK pair `(1,1)` twice — second must violate PK if FK≡PK invariant held correctly |
| `m:n junction added` | new junction join does not cartesian-explode downstream mart | seed `order_items` with 2 orders × 3 products — mart `GROUP BY order_id` still returns 2 rows, not 6 |

#### Suite C — Null-safe comparison / warehouse-variance probe

```python
def test_null_safe_comparison(ephemeral_duckdb):
    ephemeral_duckdb.execute("CREATE TABLE stg(status VARCHAR)")
    ephemeral_duckdb.execute("INSERT INTO stg VALUES ('USA'), (NULL), ('DE')")

    # naive filter — silently drops NULL row (three-valued logic bug)
    buggy  = "SELECT * FROM stg WHERE status <> 'USA'"                     # returns 1 row (DE) — misses NULL
    correct = "SELECT * FROM stg WHERE status IS DISTINCT FROM 'USA'"      # returns 2 rows (NULL, DE)

    assert ephemeral_duckdb.execute(buggy  ).fetchall() != ephemeral_duckdb.execute(correct).fetchall()
    # validator must warn when mapping condition uses <> on nullable col
```

### 6.3 BigQuery / Snowflake without BigQuery/Snowflake — sqlglot dry-run + DuckDB as oracle

The critique asks: *"what warehouse does CI execute against; how to test BigQuery SQL without BigQuery."* The wedge's answer is tiered (sqlglot, L3 + dbt-fusion lesson, L3):

- **Transpile dry-run (no creds, no spend):** `sqlglot.parse_one(sql, read="duckdb").sql(dialect="bigquery")` / `snowflake` smoke — proves there is a lowering path (and that dialect-specific functions like `REGEXP_CONTAINS` were not emitted for Postgres) (L3) https://sqlglot.com/sqlglot.html . But documented as **not a validator**: "The parser is intentionally lenient … A query that parses successfully may still fail at execution time." So this is necessary, not sufficient.

- **Ephemeral DuckDB as execution oracle:** DuckDB runs the *semantically equivalent* query after sqlglot lowering (cross-engine type map pinned — L4). Float tolerance + date normalization per dbt-fusion's Rust-port lesson (L3) https://github.com/dbt-labs/dbt-fusion/commit/6c100b4e8a11cbfa4314577c3e4a341c779ed285 — the wedge applies the same record/replay pattern for its own `validate` harness: seeded fixtures are executed on DuckDB, and the Postgres flavour is asserted via `EXPLAIN` + deferred constraint equivalence (not a second live warehouse).

- **Optional nightly Snowflake/BigQuery run (not PR-blocking):** a scheduled job with warehouse creds executes the fixture suite on the real warehouse; delta vs DuckDB is reported as `WAREHOUSE_DRIFT` (not a gate — observability). This follows Elementary's "System catalog + `system.query.history`" permission model (L4) — not needed for correctness, useful for parity drift.

### 6.4 Full `validate` sequence (ordered — what a contributor sees)

```
$ python -m wedge validate --ephemeral-duckdb --dialect-matrix all
  [1/6] contracts schema  … OK (contracts/orders/v1.5.0.yaml JSON Schema OK)
  [2/6] manifest binding … OK (field revenue → source "Revenue ($)" type DECIMAL, FK ok)
  [3/6] quoting audit     … OK (reserved word `order` quoted in every SELECT alias)
  [4/6] compatibility     … ✗ BREAKING without MAJOR — see §2.5 report (exit 1 path)
  [5/6] transpile dry-run … OK (duckdb ✓  bigquery ✓  snowflake ✓ via sqlglot)
  [6/6] execute suite     … OK (ephemeral DuckDB :memory: — 24 fixtures, 0 errors, 2s)
        foreign keys      … OK (unique+not_null PKs, relationships+not_null FKs)
        null-safe checks  … WARN (status <> 'USA' on nullable col — suggest IS DISTINCT FROM)
CF. CanoniQ generate → transform → detect→gate flow (book bug): profiling → validate → transform
  validation rules checked post-transform (L4) https://github.com/Buchiexplores/canoniq
```

On failure the report is deterministic and field-addressed (the `422` precedent — L3) — not a stack trace — and includes the **fix** (bump to `2.0.0` or `aliases:`, `nullSafeComparisons:`).

### 6.5 Lineage & blast radius at `validate` time (traceability gate)

Borrowing dpone's framework-owned lineage namespace (`__dpone__*` — L4) and Niche.dev's metric manifest lineage (L4):

- every canonical write carries `__dpone__loads` run artifact + `__dpone__parent_id` SHA-256 parent row identity for normalized nested rows (L4) https://github.com/PaulKov/dpone/blob/master/docs/load-lineage.md
- per-field blast radius from data-contract-enforcer's `ViolationAttributor` (registry-first + lineage BFS + `git blame` — L4) — `validate` emits which downstream YAML/check/mart references each breaking field by name, so the "which rule broke revenue this week?" audit trace has an index from day one (addresses CRITIQUE §2 audit <2min gap — not the full ledger, but the contract-layer half).

---

## 7. Warehouse-Variance Matrix — Checked-In Artifact (`contracts/dialect-matrix.yaml`)

The matrix is the repo-bound companion to `SCHEMA-EVOLUTION-POLICY.md`. `validate --dialect-matrix all` asserts it. Below is the normative content to be checked in as `contracts/dialect-matrix.yaml` (lifted from §5 registry).

```yaml
# contracts/dialect-matrix.yaml — normative for validate --dialect-matrix all
# Evidence: DuckDB docs (L3) https://duckdb.org/docs/ ; Postgres lexical structure (L3)
#           https://www.postgresql.org/docs/19/sql-syntax-lexical.html ;
#           BigQuery lexical (L3) https://docs.cloud.google.com/bigquery/docs/reference/standard-sql/lexical ;
#           Snowflake identifiers (L3) https://docs.snowflake.com/en/en/sql-reference/identifiers-syntax ;
#           sqlglot dialects (L3) https://sqlglot.com/sqlglot/dialects.html ;
#           Modern SQL IS DISTINCT FROM (L3) https://modern-sql.com/feature/is-distinct-from ;
#           OSI Snowflake YAML (L3) https://docs.snowflake.com/en/sql-reference/stored-procedures/system_create_semantic_view_from_osi_yaml

version: 1.0.0
dialects: [duckdb, postgres, bigquery, snowflake, trino]
identifier:
  quote_char:
    duckdb: '"'
    postgres: '"'
    bigquery: '`'
    snowflake: '"'
    trino: '"'
  rules:
    must_quote_when:
      - reserved_word: true               # per duckdb_keywords() / sqlglot reserved set
      - matches: '[^A-Za-z0-9_]'           # Revenue ($), spaces
      - starts_with_digit: true           # 1col
    alias_must_also_be_quoted: true       # MetricFlow GH #2058
  case:
    duckdb: { unquoted: case_insensitive_preserve, quoted: case_insensitive }
    postgres: { unquoted: fold_to_lower, quoted: case_sensitive }
    bigquery: { unquoted: case_sensitive, quoted: case_sensitive }
    snowflake:{ unquoted: fold_to_upper, quoted: case_sensitive, param: QUOTED_IDENTIFIERS_IGNORE_CASE }
    trino:   { unquoted: fold_to_lower, quoted: case_sensitive }
null_handling:
  comparison_with_null_returns_null: true          # three-valued logic everywhere (L3)
  null_safe_equality_operator: "IS NOT DISTINCT FROM"
  null_safe_inequality_operator: "IS DISTINCT FROM"
  prefer_over_coalesce_mask: true                  # COALESCE is non-SARGable (L4)
  coalesce_spell:
    duckdb: "COALESCE" ; postgres: "COALESCE" ; bigquery: "IFNULL" ; snowflake: "IFNULL" ; trino: "COALESCE"
type_canonicalization:
  string: { varchar: TEXT, string: TEXT, text: TEXT }
  numeric:{ number: DECIMAL, numeric: DECIMAL, decimal: DECIMAL }
  note: "Pinned alongside contract per cross-engine pre-checks (L4) — wmk Python canonicalizer UDF pushdown pattern"
execution:
  ephemeral_oracle: duckdb   # :memory: — validate runs in CI with no warehouse creds
  transpile_dry_run: [bigquery, snowflake, trino]  # sqlglot offline (L3) — necessary not sufficient
  nightly_real_warehouse_parity: { enabled: false, suites: [bigquery, snowflake] }
```

CI asserts `contracts/dialect-matrix.yaml` is the same file `validate --dialect-matrix all` used — divergence is itself a gate failure (same staleness class as MetricFlow's `ensure you've ran dbt parse` — L3).

---

## 8. Integration — How This Closes CRITIQUE §10b and §1 Together

| Critique line | How this doc resolves it |
|---|---|
| §1: no grammar, no IR, no dialect lowering, no validate definition, "validate only checked YAML syntax" | IR is defined as semantic manifest (§6.1); dialect lowering via sqlglot + quote-char selection per §5.2; `validate` is the 6-step executed harness (§6.4) that both parses+transpiles and **executes** |
| §1 kill: `Revenue ($)` + `order` quoting bug slips through validate | Suite A (§6.2) materializes the quoting probe and executes it — BUG→FIX example shown; alias-unquoted form (GH #2058) explicitly caught |
| §1 Soda×Mapping two-compiler divergence | matrix §5.4 is the shared `quote_char` / type canonicalization contract both compilers consume; divergence becomes a gate failure |
| §10b: `rev` → `revenue_net` rename silently loses binding | Taxonomy row 5 = MAJOR; gate §2.3 emits `RENAME_MAPPED_FIELD` and exit 1 without MAJOR bump; example CI output in §2.5 |
| §10b: no version-bump policy or compatibility matrix | §2.2 SemVer scoping + §3 full matrix with `kind` set |
| §10b: m:n + FK≡PK not handled in validate | §4.2–4.4: junction detection (`fk_is_half_of_pk`), rebuild_constraints migration sketch, lineage BFS blast radius, per-suite FK test (Amirul canon) |
| §10b: null/case/quoting variance per warehouse unspecified | §5 full matrix (identifiers, null-safe, aggregates) with L3 sources per row; `IS DISTINCT FROM` preferred over `COALESCE` mask |
| GAPS acceptance: `validate` covers m:n+FK≡PK drift; warehouse-variance matrix checked in | matrix YAML normative artifact §7; execution suite B covers junction PK/FK staleness |

---

## 9. Open Decisions Deferred to SCHEMA-EVOLUTION-POLICY.md (the RFC this research feeds)

1. **Compat mode default** — `FULL_TRANSITIVE` (strictest, safest) vs `BACKWARD_TRANSITIVE` (more permissive) for the canonical surface. Confluent default is `BACKWARD` (L3); Apicurio recommends `BACKWARD_TRANSITIVE` when consumers lag >1 version (L3). Recommendation: default `BACKWARD_TRANSITIVE` for contracts (canonical is the "reader"), with per-entity `FULL` for FK-heavy entities (orders) where both writer and reader compatibility matters.
2. **Transitive window size** — does `FULL_TRANSITIVE` validate against all N versions forever, or a bounded window (last 5 MAJORs)? FinitData guidance: registry rejects before production, but registry UX degrades past large histories (L3) — worth bounding at 10 MAJORs with `aliases` preservation.
3. **Deprecation horizon before breaking removal** — Avro deprecation pattern (add default, deprecate, remove after N consumers migrated) → map to YAML `aliases:` + `deprecated_since:` field in contract.
4. **Junction governance: implicit vs explicit** — Prism 8 mandates explicit junction (`@@id([aId,bId])`) (L3); wedge should mandate explicit as well (implicit m:n rejected at `validate`), so the junction's composite PK is always reviewable.

---

## 10. Sources — Cited URLs and Levels

**L3 Official / docs (normative):**

- Confluent — Schema Registry Data Contracts; migration rules via `major_version` (JSONata/CEL) — https://docs.confluent.io/platform/current/schema-registry/fundamentals/data-contracts.html
- Confluent — Schema Evolution & Compatibility Types (BACKWARD/FORWARD/FULL/TRANSITIVE) — https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html ; 8.1 variant — https://docs.confluent.io/platform/8.1/schema-registry/fundamentals/schema-evolution.html
- Confluent — Schema Registry API Reference (Compatibility enum: Backward/Forward/Full/None × transitive) — https://docs.confluent.io/platform/current/schema-registry/develop/api.html
- Apicurio Registry 3.3 — Schema lifecycle best practices (BACKWARD_TRANSITIVE, avoid NONE) — https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-schema-lifecycle-best-practices.html
- Datadef — Data Contracts in CI/CD: Schema Compatibility Checks (Avro/Protobuf compat modes, shift-left) — https://datadef.io/guides/en/data-contracts
- Postgres 19/17 — Lexical Structure (unquoted folds to lower, quoted case-sensitive) — https://www.postgresql.org/docs/19/sql-syntax-lexical.html / https://www.postgresql.org/docs/17/sql-syntax-lexical.html
- DuckDB — Keywords & Identifiers; PostgreSQL Compatibility (identifiers always case-insensitive) — https://duckdb.org/docs/lts/sql/dialect/keywords_and_identifiers ; https://duckdb.org/docs/lts/sql/dialect/postgresql_compatibility
- DuckDB — NULL Values; Comparison Operators (`IS DISTINCT FROM`) — https://duckdb.org/docs/lts/sql/data_types/nulls ; https://duckdb.org/docs/lts/sql/expressions/comparison_operators
- BigQuery — Lexical Structure (identifiers, reserved keywords must be quoted) — https://docs.cloud.google.com/bigquery/docs/reference/standard-sql/lexical
- Snowflake — Identifier Requirements (`QUOTED_IDENTIFIERS_IGNORE_CASE`) — https://docs.snowflake.com/en/en/sql-reference/identifiers-syntax ; Bidirectional migration identifier translation — https://docs.snowflake.com/en/migrations/aim-for-datawarehouses/code-conversion/translation-references/bigquery/bigquery-identifiers ; OSI YAML dialect priority (SNOWFLAKE → ANSI_SQL fallback) — https://docs.snowflake.com/en/sql-reference/stored-procedures/system_create_semantic_view_from_osi_yaml
- sqlglot — Parser is intentionally lenient: parsing ≠ validating; transpile API `parse_one(sql, dialect).sql(write_dialect)` — https://sqlglot.com/sqlglot.html ; https://sqlglot.com/ ; Dialects extensibility — https://sqlglot.com/sqlglot/dialects.html
- Prisma 8 Data Modeling — Junction model with `@@id([postId, tagId])`; implicit m:n rejected — https://www.prisma.io/docs/orm/v8/data-modeling/relational-databases
- EF Core — Many-to-many join entity schema `PostTag(Id PK, PostId FK, TagId FK)` — https://learn.microsoft.com/en-us/ef/core/modeling/relationships/many-to-many
- GitLab DB docs — Foreign keys require concurrent index before FK; FK removal before index removal — https://docs.gitlab.com/development/database/foreign_keys/
- Percona Toolkit — pt-online-schema-change Foreign Keys (`rebuild_constraints` vs `drop_swap`) — https://docs.percona.com/percona-toolkit/pt-online-schema-change.html ; https://www.percona.com/blog/how-pt-online-schema-change-handles-foreign-keys/

**L3 Registry / contract OSS (precedent the gate copies):**

- mizcausevic-dev / PyPI data-contract-registry — `POST /contracts` → deterministic compatibility report or `422 kind+field` — https://pypi.org/project/data-contract-registry/ ; https://ofs.ccwu.cc/mizcausevic-dev/data-contract-registry
- PyPI dbt-data-contracts — `dbt-contracts check` exit codes `0/1/2`, breaking removes `amount` while `finance` is consumer — https://pypi.org/project/dbt-data-contracts/
- PyPI datalasi — versioned YAML contracts, `datalasi check contracts/` CI gate — https://pypi.org/project/datalasi/
- Dlt — Schema contracts `evolve / freeze` — https://dlthub.com/docs/general-usage/schema-contracts
- Confluent blog — Data Contracts in Schema Registry (compatibility groups via `major_version`) — https://www.confluent.io/blog/data-contracts-confluent-schema-registry/

**L3/L4 Cross-engine / lineage / methodology:**

- Cross-engine reconciliation — Schema Validation Pre-Checks (canonical contract pinned+versioned; cross-engine type map pinned alongside) — https://www.cross-engine-reconciliation.org/data-extraction-hashing-workflows/schema-validation-pre-checks/
- PaulKov/dpone — Load lineage `__dpone__*` namespace, SHA-256 parent identity — https://github.com/PaulKov/dpone/blob/master/docs/load-lineage.md
- Buchiexplores/canoniq — Profiles source, canonical schema propose+transform+validate+drift detect (Soda/CanonIQ auto-generated validation rules) — https://github.com/Buchiexplores/canoniq
- Natnael-Alemseged/data-contract-enforcer — Registry-first blast radius + lineage BFS + git blame (ViolationAttributor) — https://github.com/Natnael-Alemseged/data-contract-enforcer
- Warehouse-migration-kit — Python canonicalizer UDF pushdown for column fingerprint parity — https://github.com/PrasannakumarKasindala/warehouse-migration-kit
- Niche.dev — Metric manifest lineage spec (canonical SQL + PK + SHA digest) — https://niche.dev/blog/metric-lineage-audit-trails-production-models/
- stevenzg.com — Data Contracts (PR gate forces conversation; producer cannot merge without default/deprecate/major-bump) — https://stevenzg.com/software-development/data-engineering/data-contracts
- DEV Community — Data Contract Framework Implementation Guide (Major/Minor/Patch table 1.2.0→2.0.0/1.3.0/1.2.1) — https://dev.to/datanestdigital/data-contract-framework-data-contract-framework-implementation-guide-hhj
- VEX Data — Data Contracts Explained (CI compatibility check before merge, registry query pattern) — https://www.vexdata.io/post/data-contracts-explained-the-data-engineer-s-guide-to-preventing-pipeline-failures

**L4 Guidance / blog / benchmark (warehouse-variance, FK, drift):**

- Modern SQL — NULL-Aware Comparison: `is [not] distinct from` — https://modern-sql.com/feature/is-distinct-from
- Biersons — `DISTINCT FROM` in Postgres (COALESCE vs IS DISTINCT FROM correctness) — https://www.biersons.com/posts/postgres-distinct-from-and-comparing-potetially-null-values/
- Stack Overflow 33828329 — Why `IS DISTINCT FROM`; `COALESCE` is non-SARGable, index not used — https://stackoverflow.com/questions/33828329/why-use-is-distinct-from-postgres
- DBA StackExchange 217983 — Non-SARGable predicate impacts index usage — (referenced via SO)
- dbSyntax — Replace NULL recipes (`IFNULL`/`NVL`/`ISNULL`/`COALESCE` per engine) — https://dbsyntax.com/reference/recipes/replace-null-with-zero
- Prisma 6 m-n introspection — implicit m-n via `@@id` conventions — https://www.prisma.io/docs/orm/v6/prisma-schema/data-model/relations/many-to-many-relations
- Schemity — Many-to-many shouldn't mean hand-building the junction (split cost when FK≡PK half moved) — https://schemity.com/blog/many-to-many-shouldnt-mean-hand-building-the-junction-table/
- AuditBuffet — Every table has explicit primary key — https://auditbuffet.com/patterns/ab-000897
- RelationalDBDesign — Many-to-many junction design `PK(OrderID, ProductID)` — https://www.relationaldbdesign.com/database-design/module6/define-manyToMany-relationships.php
- Amirul Islam — Schema tests: `not null, unique, FK, accepted values` (PK/FK canon) — https://amirulislamalmamun.com/practice/data-engineering/concepts/045-schema-tests/
- Confluent Learn — Schema Compatibility course (BACKWARD/FORWARD/FULL + transitive) — https://developer.confluent.io/courses/schema-registry/schema-compatibility/
- Confluent Patterns — Schema Compatibility (backward/forward flavours) — https://developer.confluent.io/patterns/event-stream/schema-compatibility/
- Confluent Blog — Schema Registry Best Practices (Full compatibility note) — https://www.confluent.io/blog/best-practices-for-confluent-schema-registry/
- DEV Community — Why I stopped trusting JSON schemas, started enforcing contracts in CI (`currency` removal → CI diff → exit 1 / `transactions_v2`) — https://dev.to/aniketsoni/why-i-stopped-trusting-your-json-schemas-and-started-enforcing-data-contracts-in-ci-3mdp
- FinitData — Schema evolution strategies for production pipelines (registry rejects before production) — https://finitdata.com/schema-evolution-strategies-for-production-data-pipelines/
- Medium /balajibal — SQL Gateway with sqlglot AST — https://medium.com/@balajibal/from-regex-to-deterministic-control-building-a-sql-gateway-with-sqlglot-8f0aee90d7e0
- mmurakaru/di0 — `dialect: snowflake, validation: sqlglot-offline, execution: noop` gating — https://github.com/mmurakaru/di0
- Dbt-labs — MetricFlow `invalid identifier` / stale manifest (`dbt parse`) — GH #12512 ; `ORDER` alias still broken — GH #2058 ; ambiguous column `YELLOW` not caught until query time — GH #1930 (L3, cited in CRITIQUE)
- Percona — Don't Auto pt-online-schema-change for Tables With Foreign Keys (`auto` vs `rebuild_constraints`) — https://www.percona.com/blog/dont-auto-pt-online-schema-change-for-tables-with-foreign-keys/
- openark GH — The problem with FKs in Online Schema Changes (`rebuild_constraints` sequence) — https://code.openark.org/blog/mysql/the-problem-with-mysql-foreign-key-constraints-in-online-schema-changes

**L4-L5 (cross-check / context):**

- FinitData / vexdata / dev.to frameworks surveyed — used only to corroborate registry-pattern invariants, not as normative spec.

---

## 11. Minimal File List to Close G-SCHEMA-01

To satisfy GAPS acceptance (G-SCHEMA-01 green: *CI gate rejects a column-rename without a version bump; `validate` covers m:n + FK≡PK drift case; warehouse-variance matrix checked into repo*):

1. `contracts/dialect-matrix.yaml` — normative matrix from §7 (checked in).
2. `contracts/{entity}/v{MAJOR}.{MINOR}.{PATCH}.yaml` — per-entity SemVer contract (registry root — at least one sample entity `contracts/orders/v1.5.0.yaml`).
3. `wedge/cmd/validate.py` (or `src/wedge/validate.py`) — the 6-step CLI (§6.4) with `--ephemeral-duckdb --dialect-matrix` flags; exit `0/1/2`.
4. `.github/workflows/contract-gate.yml` — required check (§2.4) that runs `contract check --base origin/main` on every PR touching `contracts/` / `mappings/`.
5. `tests/validate/test_validate_execution.py` — ephemeral-DuckDB harness (Suite A/B/C) plus the `test_fk_retarget_breaks_ephemeral_load` and `test_quoted_identifier_compiles_and_executes` cases above — proves rename without MAJOR bump fails CI, and proves quoting/alias bug fails before it ever reaches Postgres/BigQuery.

Without (5) the gate is syntax-only — the CRITIQUE §1 kill — so (5) is not optional.

---

## 12. Acceptance Checklist — G-SCHEMA-01

- [ ] CI rejects `rev` → `revenue_net` rename on `contracts/orders/v1.4.0` when version stays `1.4.0` — exit 1 with `RENAME_MAPPED_FIELD` kind (example in §2.5 reproduces locally via `wedge contract check --base origin/main`).
- [ ] CI passes the same rename when bumped to `2.0.0` with consumer ACK or `aliases: [rev]` shim, and the execution suite confirms DuckDB load succeeds on the retarget.
- [ ] `validate --ephemeral-duckdb` covers the **m:n + FK≡PK** case: junction `order_items` (`PK(order_id, product_id)`) with FK-half retarget — `fk-eq-pk-drift` fires, junction PK rebuild migration is generated, and the FK relationship test fails before the load (Suite B).
- [ ] **Warehouse-variance matrix** checked into `contracts/dialect-matrix.yaml` (row-for-row traceable to §5 / L3 sources): quoting char per dialect, alias-quoting invariant (GH #2058), `IS DISTINCT FROM` vs `COALESCE`, type canonicalization.
- [ ] Ephemeral-DuckDB harness passes Suite A (quoting/reserved-word probes `Revenue ($)`, `order`, `__EMPTY_3` + alias-unquoted form) and Suite B/C (FK orphans, PK duplicate, null-safe). `sqlglot` transpile dry-run passes for BigQuery/Snowflake without warehouse creds.
- [ ] `validate` output is deterministic field-addressed `kind+field` report (not a stack trace) — selective gate per `kind` is routable (mizcausevic precedent).
- [ ] Drift taxonomy table (§3) with bump + migration flags reviewed with the canonical team — no "nullable = safe" shortcut for mapped renames.

---

*G-SCHEMA-01 research synthesis — cite levels inline; URLs live-fetched 2026-08-30 via SearxNG/Exa. Reuse is synthesis (not quote-stuffing): every taxonomy gate and variance cell is traceable to a cited official/doc source above. Follow the `Minimal File List` (§11) to make `validate` more than YAML lint — it executes the SQL it emits.*
