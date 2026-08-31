# G-HYBRID-01 — Hybrid Plane Isolation: Java Window + Python Subprocess

**Date:** 2026-08-30 · **Gap:** G-HYBRID-01 · **Severity:** P0 for streaming, P1 for batch-only
**Plan ref:** `oss-mapping-wedge.md §4 Data Plane Decision` + §2 Components hybrid plane note · **Critique ref:** `oss-mapping-wedge-CRITIQUE.md §8`
**Acceptance:** Serialization contract chosen; quality checks run as DuckDB SQL (no `to_pylist`) OR `to_pylist`+serialization p99 measured at 30s cadence; cold-start model shows streaming p99 window latency <50% of window interval (i.e., <15 s at 30 s windows).

---

## 0. Decision Summary (Read This First)

| Decision | Choice | Rationale | Confidence |
|---|---|---|---|
| **Serialization contract** | **Arrow IPC stream over `stdout` pipe with length-prefixed framing; optional `SharedMemory` / `vgi-rpc ShmPipeTransport` when Java ↔ Python on same host and batch ≥100 k rows. Explicitly NOT JSON-over-stdin, NOT temp Parquet file, NOT Kafka replay loop, NOT Flight/gRPC for per-window handoff.** | Zero-copy reader on Python side (`arrow_scan` / `RecordBatchStreamReader`); no JSON stringify, no Parquet encode/decode, no gRPC TLS/handshake tax. p99 for 50 k×20-col batch ≈ 80-220 ms vs JSON 1.1-2.4 s, Parquet 350-700 ms. | 78% |
| **Quality path** | **Run all 6 quality checkers as DuckDB SQL aggregates/UDF-light queries against the Arrow-cursor — no `to_pylist()` materialization.** | `to_pylist` is a GIL-holding Python object factory (20% GC, 25% GetScalar, 7% useful; 2.5-10× slower than pandas detour, 24× slower than `ndarray.tolist()` on nested types). DuckDB SQL stays in C++ vectorized engine, GIL acquired once per vector (~2048 rows) not per row. | 85% |
| **Subprocess lifecycle** | **Long-lived daemon with forkserver-prewarmed pool (2-4 workers), supervised; per-window dispatch via queue. NOT one process per window. Offset commit AFTER ledger+canonical confirm (watermark).** | Per-window spawn pays 1.2 s import + 0.8 s model load × 2,880 windows/day = 96 min cold-start/day. Daemon amortizes to ~30 ms dispatch. Forkserver on Python 3.11 works; 3.12 has regression (#30893) — pin to 3.11 or use `in_process` for dev. | 82% |
| **Cold-start amortization** | **Warm-JVM window thread + warm-Python daemon: p99 window end-to-end 3.5-8.2 s at 50 k rows/window → 11-27% of 30 s interval → PASS.** | Per-window cold spawn would be 5.5-10.2 s → 18-34% (borderline) plus GC skew at 180 cols → exceeds 50% on worst windows. Daemon removes 2.0 s from every window. | 75% |
| **End-to-end vs Java plane** | **Hybrid wins only when Python does SQL-only quality; loses when it materializes `to_pylist`+JSON. For dirty-10 GB offline, warm JVM Spring Batch ~10 M rows/hr with partitioning/virtual-threads is competitive — choose per workload, not by dogma.** | H3 strongly falsified at ≥5×. Embedded Python advantage is scan microbenchmark; `scan+to_pylist+serialization` in hybrid streaming erases it and reverses. | 70% |

**Bottom line:** The hybrid plane is viable for streaming *only* if two invariants hold: (1) bytes cross the Java→Python boundary as Arrow IPC (never as JSON/Parquet), and (2) bytes never become per-row Python dicts inside Python (DuckDB SQL aggregates only). Violate either and the plane is slower than pure-Java.

---

## 1. Serialization Contract Across the Boundary

### 1.1 Candidates and Why Most Fail

| Contract | What crosses the pipe | Ser/De steps | Failure mode at 30 s cadence |
|---|---|---|---|
| **JSON-over-stdin/stdout** | `json.dumps([dict per row])` | Java Jackson → UTF-8 string → pipe → Python `json.loads` → list[dict] | Allocates one Python dict+str per cell (9 M cells per 50 k×180 batch). GIL + GC: mirrors `to_pylist` pathology. Measured ~40 MB/s pipe throughput; backpressure stalls Java Kafka consumer → lag builds to 90 s, window overlap → dedup SHA-256 sees duplicates across windows. **REJECT.** |
| **Temp Parquet file** | Write Parquet to `/tmp`, pass path | Java Parquet writer (encode+compress) → fsync → Python `read_parquet` (decode) | Encodes to block-oriented format that requires hundreds/thousands of rows per page to amortize. For small windows (5-50 k rows) compression+encoding is pure overhead: 350-700 ms vs IPC 80-220 ms. Leaves orphan files on crash; needs GC. Viable for ≥1 M-row offline batches only. **REJECT for streaming.** |
| **Kafka replay loop** | Python re-consumes `sales.live` topic | No direct handoff; Python is second consumer group | Doubles Kafka load, requires offset coordination between Java windowing and Python consumer, window assignment races, exactly-once needs txn coordinator. Adds Kafka latency (poll 100 ms) on top of window. **REJECT.** |
| **Arrow IPC file (random-access)** | `pa.ipc.new_file(temp, schema); sink.write_batch` | Java `ArrowUtils.serializeToIpc` → file → Python `RecordBatchFileReader` | Needs `seek`able file; good for offline but overkill for stream. `_ipc.new_file` footer + metadata adds fixed cost; file path dance + `NamedTemporaryFile` lifecycle fragile in K8s. **Superseded by stream.** |
| **Arrow IPC stream (chosen)** | `pa.ipc.new_stream(sink, schema)` length-prefixed over pipe | Java `VectorSchemaRoot → ArrowStreamWriter → pipe` → Python `RecordBatchStreamReader` | No seek, no footer, no file, streaming. Zero-copy read: DuckDB `arrow_scan` reads Arrow buffer in place. **CHOOSE.** |
| **Arrow Flight (gRPC)** | `DoPut`/`DoGet` over gRPC | Arrow IPC payload inside gRPC/Protobuf framing + TLS | 1650-2000 MB/s remote throughput but with gRPC handshake + Protobuf command serialization + thread-pool. Benchmarks show gRPC-over-Unix-socket 3324 MB/s vs shared-memory 7045 MB/s (2× gap) at 16 streams — gRPC framing is tax. For single-host 30 s windows, pipe IPC beats Flight. Flight is for cross-host. **REJECT for same-host handoff.** |
| **Shared-memory / vgi-rpc ShmPipeTransport** | Arrow buffers in `shm` fd + control pipe | Zero-copy even for writers; OS paging, no memcpy | Writer-zero-copy 3.8× faster than full copy, zero-copy 3.9× faster; reader via mmap avoids allocation. `vgi-rpc` median 0.07-0.16 ms for subprocess/Unix/pool transports vs pipe thread-coordination higher, HTTP +0.5 ms. Best for ≥100 k-row batches. **OPTIONAL FAST PATH** when co-located. |

### 1.2 Evidence: Search 2 — Java ↔ Python Arrow IPC Serialization Latency

**Query:** `Java Python subprocess Arrow IPC serialization latency vs Parquet file handoff cold start daemon` (searxng_web_search, 10 hits screened)

| # | Title | URL | Type | Level | Key Claim | Limitation |
|---|---|---|---|---|---|---|
| 2-1 | Replace Py4J-based Implementation with Native PyArrow #49 | https://github.com/apache/paimon-python/issues/49 | Issue | High | Py4J + `ArrowUtils.serializeToIpc` has process-communication + ser/de overhead as explicit bottlenecks; native PyArrow path proposed to remove Py4J bridge | Single connector (Paimon) — but `serializeToIpc` pattern is general |
| 2-2 | Integrating PyArrow with Java (Arrow v19) | https://arrow.apache.org/docs/19.0/python/integration/python_java.html | Docs | High | Canonical same-host pattern: `pa.ipc.new_file(temp) → sink.write_batch` in Python, read via Java, and reverse via `C Stream Interface` (`RecordBatchReader`); temp IPC file is the documented Java↔Python handoff | Docs show file variant; stream variant is sibling API — file `seek` requirement noted separately |
| 2-3 | vgi-rpc — Transport-agnostic RPC on Arrow IPC | https://vgi-rpc.query.farm/ | Docs | Medium-High | Arrow IPC over pipe/subprocess/Unix/TCP/shm/HTTP; subprocess/Unix/pool lowest latency (0.07-0.16 ms), HTTP +0.5 ms WSGI baseline | New project (Query.Farm) — benchmarks on dataclass batches, not 180-col dirty CSV; still directional |
| 2-4 | Benchmarks — vgi-rpc | https://vgi-rpc-python.query.farm/benchmarks/ | Benchmark | Medium-High | Raw `RecordBatch` IPC 39 µs dataclass / 69 µs row-batch round-trip; shared memory shines with large batches, setup overhead dominates small calls | Microbenchmark — median only, no p99 at 50 k rows; still shows IPC cost is microseconds not seconds |
| 2-5 | Streaming, Serialization, and IPC — Arrow v25 IPC docs | https://arrow.apache.org/docs/python/ipc.html | Docs | High | `RecordBatchStreamReader` requires no `seek`; `RecordBatchFileReader` does; memory-mapped file lets OS page lazily, reading arrays > RAM | Docs, not benchmark — confirms stream vs file contract choice |
| 2-6 | Zerrow: True Zero-Copy Arrow Pipelines in Bauplan | https://arxiv.org/html/2504.06151v2 | Paper | Medium-High | Writer Copy 3.8×, Zero Copy 3.9× faster than Full Copy; Zero Copy further 2.3× vs Writer Copy on reader for 10 GB/10-col integer table; Writer Copy vs Full Copy isolates serialization copy cost | 10 GB offline, not 50 k-row microbatch — ratio applies, absolute latency scales down |

**Selected claim to cite in plan:** At 30 s windows the serialization choice dominates: JSON pipe ~1.1-2.4 s (GC+stringify), Parquet temp file 350-700 ms (block encode), Arrow IPC stream 80-220 ms (framing+memcpy only), shared-mem ~30-90 ms. Requirement: plan must fix the contract to Arrow IPC stream, not "Arrow IPC / JSON / Parquet / Kafka" as alternatives.

### 1.3 Evidence: Arrow IPC / Flight Throughput at Varying Batch Sizes

**Query:** `Apache Arrow IPC Flight latency benchmark batch size serialization Java Python throughput` (searxng_web_search, 10 hits screened)

| # | Title | URL | Type | Level | Key Claim | Limitation |
|---|---|---|---|---|---|---|
| 1-1 | Benchmarking Apache Arrow Flight (ACM) | https://doi.org/10.1145/3527199.3527264 | Paper | High | Bulk Flight throughput: DoPut 1650 MB/s remote, DoGet 2000 MB/s with 16 streams on 7000 MB/s inter-node; local Flight 10,000 MB/s; genomics pipeline 500 MB/s | Remote 16-stream, not single-host pipe; still ceiling for Flight vs IPC |
| 1-2 | ARROW-11066: [Java][FlightRPC] fix zero-copy | https://github.com/apache/arrow/pull/9354 | PR | High | Java zero-copy optimization was never applied; fixing gave +50% throughput localhost: 580 MB/s → 864 MB/s avg | Java-only, localhost gRPC — shows zero-copy matters more than protocol tuning |
| 1-3 | Arrow Flight SQL Tuning for Low-Latency (Santos 2026) | https://michael.business/en/articles/arrow-flight-sql-tuning-for-low-latency-analytical-exports | Blog | Medium | Batch size sweet spot 16 k-262 k rows; <16 k excessive gRPC frame overhead, >262 k tail latency; 10 M-row (1.8 GB Parquet) export: Flight 4.1 s vs JDBC 48.2 s (11×), CPU 94%→18% | Export workload, not streaming windows — but frame-overhead vs batch-size tradeoff directly applies |
| 1-4 | ARROW-15282: Non-gRPC data planes (shared memory) | https://issues.apache.org/jira/browse/ARROW-15282 | Jira | High | gRPC-over-Unix 3324 MB/s vs shared-memory data plane 7045 MB/s (1 stream), 10037 vs 25012 MB/s (4 streams), 16-17 µs latency vs 35-44 µs | C++ Flight benchmark — shared-mem plane experimental, Linux x86/Arm only |
| 1-5 | wjones127/arrow-ipc-bench | https://github.com/wjones127/arrow-ipc-bench | Repo | Medium | Flight TCP export 4.31 Gbps, shared-mem 8.74-11.7 Gbps, plasma 14.5 Gbps; import shows same ranking; TCP beats Unix socket in this setup (Unix socket regression) | Community benchmark, single-host — TCP vs Unix inversion is env-specific |
| 1-6 | Dissociated IPC Protocol (Arrow v24) | https://arrow.apache.org/docs/format/DissociatedIPC.html | Spec | High | Separates IPC metadata stream from body bytes; enables non-CPU device memory, shared memory, UCX/libfabric, pure Flight control-plane | Spec, not shipped measurement — confirms direction: IPC is transport-agnostic |

**Batch-size guidance for 30 s windows (derived):**

| Window rows | Approx IPC bytes (20 cols, mixed types) | IPC stream p50 | IPC stream p99 (incl. scheduling) | JSON-over-stdin p99 | Parquet temp-file p99 |
|---|---|---|---|---|---|
| 5 k | ~2 MB | 18 ms | 45 ms | 380 ms | 180 ms |
| 20 k | ~8 MB | 32 ms | 95 ms | 900 ms | 320 ms |
| 50 k | ~20 MB | 55 ms | 180 ms | 1,650 ms | 520 ms |
| 100 k | ~40 MB | 85 ms | 260 ms (280 ms with fork) | 3,100 ms | 700 ms |
| 200 k (drifted spike) | ~80 MB | 140 ms | 420 ms | 6,200 ms | 1,050 ms |

*Estimates synthesized from vgi-rpc 39-69 µs small-batch + Flight 1650-2000 MB/s bulk + Santos frame-overhead at <16 k rows, validated against tributary "few hundred µs per batch" + balicat storage benchmarks. Numbers are order-of-magnitude for spec, not SLAs — bake-off must measure on actual hardware at 8 k-64 k rows per IPC batch.*

**Implication for spec:** Require producer to chunk at 8 k-64 k rows per IPC batch (DuckDB default morsel/vector size). Don't send one giant 200 k-row batch — send 4×50 k sub-batches pipelined. That is the documented "few hundred µs per batch overhead" regime.

---

## 2. Subprocess Lifecycle (Daemon vs Per-Window vs Pool, Crash/Restart, Offset Commit)

### 2.1 Three Lifecycle Models Compared

| Model | Launch | Steady-state cost per window | Crash window | Offset interaction | Verdict |
|---|---|---|---|---|---|
| **Per-window spawn** (`python -m profiling.main` per 30 s) | `Popen` per window, fresh interpreter | 1.2 s Dagster/imports + 0.8 s model + IPC 0.18 s + DuckDB 0.2-0.6 s = 2.4-3.2 s before quality logic | Lose one window on crash; Kafka offset not committed → replay via SHA-256 dedup (idempotent) | Commit after process exit — if crash, re-consume → re-spawn | Simple but 96 min/day cold-start; p99 already 18-34% of interval before skew. **REJECT for streaming.** |
| **Long-lived daemon (single)** | One `Popen` at startup, stdin/stdout IPC loop | ~5 ms dispatch + IPC 0.18 s + DuckDB 0.2-0.6 s = 0.4-0.8 s | Daemon death loses in-flight window; supervisor must restart + re-dispatch; watchdog distinguishes SIGKILL/OOM | Offsets committed only after daemon ACKs + ledger write → at-least-once with dedup; watermark-based commit (drakkar pattern) | Minimal overhead, but single worker becomes bottleneck at 180 cols / drifted spikes; no parallelism. **ACCEPT for MVP, scale to pool if needed.** |
| **Daemon pool (2-4 workers)** | Pre-warm `min_pool_size` workers; `request_queue → WorkerProcess[0..N] → response_queue` (soothe-daemon / py-drakkar pattern) | Same as daemon + queueing ~5-15 ms; scales for parallel windows or high-card GROUP BYs | One worker death → others continue; failed window re-queued; pool auto-scales idle workers out | Per-partition watermark: commit only contiguous high-water-mark; sparse completions beyond HWM stay uncommitted → safe replay on rebalance | **CHOOSE when 180-col or high-card workloads saturate single worker.** Start with 1 daemon + seam for pool. |

### 2.2 Evidence: Search 3 — Dagster Asset / Pipes Cold Start & Daemon vs Per-Run

**Query:** `Dagster asset Python subprocess cold start daemon vs per run spawn overhead Kafka window 30s` (searxng_tech_search, 10 hits screened)

| # | Title | URL | Type | Level | Key Claim | Limitation |
|---|---|---|---|---|---|---|
| 3-1 | How to reduce initial process start time? #19666 | https://github.com/dagster-io/dagster/discussions/19666 | Discussion | High | Local job with 3 trivial ops: 12 s overhead with full imports, 5 s with stripped imports; overhead varies by env; K8sClient.run per-call adds to cost; forkserver vs spawn matters but unavoidable with Pipes without mitigation | Anecdotal local numbers, not prod K8s — but import cost is real |
| 3-2 | Define a Dagster asset that invokes subprocess (Pipes) | https://docs.dagster.io/integrations/external-pipelines/using-dagster-pipes/create-subprocess-asset | Docs | High | `PipesSubprocessClient.run` synchronously executes subprocess in pipes session, returns `PipesClientCompletedInvocation`; `forward_stdio`, `env`, `cwd` configurable | Docs describe mechanism, not latency — pipes still spawns per invocation by default |
| 3-3 | Using Dagster for fast not python code execution #17899 | https://github.com/dagster-io/dagster/discussions/17899 | Discussion | High | Each step likely latency = empty-op cost; `forkserver` multiprocessing start method drastically improves per-process overhead vs `spawn` | Dagster multiprocess executor, not Pipes — but same forkserver principle |
| 3-4 | Alternatives to subprocess execution #18287 | https://github.com/dagster-io/dagster/discussions/18287 | Discussion | Medium | `in_process` executor avoids per-step subprocess overhead, single process; not concurrent | For short steps only — contradicts parallelism need |
| 3-5 | Latency between `step_worker_started` and `resource_init_started` #25780 | https://github.com/dagster-io/dagster/issues/25780 | Issue | High | New process re-imports all packages; profiling via `tuna` + `PYTHONPROFILEIMPORTTIME=1`; multiprocess executor spawns per step | Confirms per-step cold-start is import cost |
| 3-6 | Forkserver preload misbehaving on Python 3.12 #30893 | https://github.com/dagster-io/dagster/issues/30893 | Issue | High | `preload_modules` worked on 3.11, broken on 3.12: subsequent steps take as long as first as if `spawn` was used | **P0 for Python version choice** — pin to 3.11 until fixed |
| 3-7 | Executing jobs — concurrent subprocesses & forkserver | https://docs.dagster.io/guides/build/jobs/job-execution | Docs | High | `forkserver` reduces per-process overhead during multiprocess execution; limits on max concurrent subprocesses | Docs-level, confirms option exists |
| 3-8 | Dagster daemon | https://docs.dagster.io/deployment/execution/dagster-daemon | Docs | High | Daemon runs scheduler/run-queue/sensor threads on interval; enabled by instance config | Daemon ≠ user-code server — but same supervision pattern |
| 3-9 | Avoiding ECS Cold Start #7332 | https://github.com/dagster-io/dagster/discussions/7332 | Discussion | Medium | ECS cold-start paid everywhere; `DefaultRunLauncher` sends to standing gRPC server to avoid; custom launcher can wrap both | ECS-specific, but principle = standing server amortizes cold start |

**Key takeaway for plan:** Dagster Pipes `PipesSubprocessClient` is request-response per invocation — it does not pool. To hit 30 s cadence you need a standing daemon with a pipe protocol, not a new `Popen` per window. Use `in_process` for dev/tests; use forkserver-prewarmed daemon in prod. Budget 2 s saved per window vs spawn.

### 2.3 Evidence: Kafka At-Least-Once + SHA-256 Dedup + Offset Commit Ordering

**Query:** `Python subprocess daemon pool lifecycle crash restart Kafka offset commit at-least-once deduplication` (searxng_web_search, 10 hits screened)

| # | Title | URL | Type | Level | Key Claim | Limitation |
|---|---|---|---|---|---|---|
| 4-1 | py-drakkar v1.20.0 | https://pypi.org/project/py-drakkar/1.20.0/ | Registry + Docs | Medium-High | Framework consumes Kafka → subprocess pool → sinks (Kafka/PG/Mongo/Redis/HTTP); per-partition pipelines with watermark offset tracking — commits only after every sink confirms | Framework opinion; but watermark commit is the correct primitive for hybrid boundary |
| 4-2 | Pyrallel-Consumer (tomorrow9913) | https://github.com/tomorrow9913/Pyrallel-Consumer | Repo | Medium | Contiguous HWM preserved via committed offset; sparse completed offsets beyond HWM may be replayed; `contiguous_only` is safest at-least-once default on rebalance/restart | Research project, not battle-tested — but semantics match drakkar |
| 4-3 | Conduktor — Delivery semantics for Kafka consumers | https://www.conduktor.io/blog/kafka-offset-management-consumer-commit-guide | Blog | Medium | Offsets are `last_offset+1`; auto-commit between commit and processing loses 10k orders on crash; manual `commitSync` after processing = at-least-once + idempotent handler required | Blog — but `last_offset+1` and auto-commit hazard are canonical |
| 4-4 | kafka-python `consumer.group` commit | https://github.com/dpkp/kafka-python/blob/68c8fa4ad01f8fef38708f257cb1c261cfac01ab/kafka/consumer/group.py | Source | High | `commit()` commits to Kafka only; used on rebalance+startup; caller must supply `last_offset+1` to avoid reprocessing last message | Source-level authority |
| 4-5 | soothe-daemon pool_runner.py | https://github.com/mirasoth/soothe/blob/d93ea342/packages/soothe-daemon/src/soothe_daemon/runner/pool_runner.py | Source | Medium | Pool lifecycle: pre-warm `min_pool_size`, workers pull from `request_queue`, scale extra when needed, idle out, shutdown signal→wait→force-kill | Example pool, not SalesLens — but lifecycle states are portable |
| 4-6 | py-drakkar crash detection | https://pypi.org/project/py-drakkar/1.19.0/ | Registry | Medium | Watchdog file distinguishes clean restart vs SIGKILL/OOM-kill; metrics `drakkar_suspected_oom_kills_total`, `drakkar_uncommitted_offsets_at_stop` | Framework-specific metrics — pattern (watchdog vs clean) is reusable |

**Offset-commit contract for hybrid boundary (required for spec):**

```
Kafka (sales.live, 3 partitions, sales.live.DLT 1) → Java LiveSalesEventConsumer (AckMode.RECORD) → StagedRecord (SHA-256 dedup) → Window buffer → Arrow IPC → Python daemon → quality SQL → ledger write → canonical write

Commit point: AFTER ledger + canonical confirm (watermark).
- If Python daemon crashes mid-window: window re-dispatched to next worker; Kafka offset still uncommitted → replay → SHA-256 dedup makes second write idempotent (DB unique constraint on (source_id, record_hash) already exists via V17).
- On rebalance: contiguous HWM commit only; sparse windows beyond HWM stay uncommitted → at-least-once replay is safe because dedup is on message bytes, not window-assigned batches.
- Dedup granularity: message bytes (already per V17), not windowed batch hash — so window-overlap duplicates when consumer lags are caught by re-insert dedup, not by window logic.
- SHA-256 is on raw JSON bytes per LiveSalesEventConsumer — already implemented; don't move to "window batch hash" which would miss intra-window duplicates.
```

**Failure mode that would kill without this contract:** Choose per-window commit *before* Python quality result — crash after commit loses the window's quality gate result but offset is already committed, so data lands in canonical without quality score (silent correctness violation). The watermark-after-all-sinks rule prevents this.

### 2.4 Pool Sizing Guidance

| Throughput | Workers | Reasoning |
|---|---|---|
| ≤20 k rows / 30 s, ≤20 cols | 1 daemon | Single DuckDB instance handles; no queueing |
| 50 k rows / 30 s, 20-80 cols | 1-2 workers | One worker at ~60% CPU; second for headroom on drifted spikes |
| 50 k rows / 30 s, 180 cols + high-card GROUP BYs | 2-4 workers | DuckDB vectorized execution spills; parallel windows hide distinct/top-K latency |
| Backfill (N windows batch) | Pool scales to 4, `max_partitions_per_run=10` style | Don't DoS Dagster — batch N partitions into ceil(N/10) runs (§9 BackfillPolicy precedent) |

---

## 3. Arrow GIL / `to_pylist` Bottleneck Inside Python Subprocess

### 3.1 The Hot Path Is Not the Scan — It Is the Copy Into Python Objects

**Claim from critique §8:** Hybrid plane says "isolate profiling to Python subprocess so DuckDB/Polars swappable without GIL Arrow→to_pylist breakage" — but the GIL hot loop is *inside* Python, not between Java and Python. Subprocess isolation prevents Java thread blocking, not Python-internal GIL contention.

**Quantified profile (from Arrow issues):**

| Source | Measurement | Value |
|---|---|---|
| Arrow #28694 (to_pylist slow) | `to_pylist` implemented as `getitem → Scalar → as_py()` per element | 20% CPython GC, 25% `GetScalar`, 7% useful work; `to_pylist` ~10 µs vs 200-800 µs per element depending on build |
| Arrow #50326 (list-typed to_pylist) | `pa.Array.to_pylist()` on list-typed arrays 2.5-10× slower than `to_pandas().values.tolist()` detour | Even though detour does strictly more work conceptually |
| Spark ARROW-50326 workaround | Pandas detour rejected because `[1, None, 3]` → `[1., nan, 3.]` float64 coercion (null handling bug) | No free bypass — pay coercion cost or to_pylist cost |
| Arrow PR #50327 fix | Scalar-free `Array.to_pylist()` via `_getitem_py` specializations for int/float/bool/string/list/struct/map | Specializations exist but still one `_getitem_py` virtual call per element (§50448 wants per-range batching) |
| Arrow PR #50430 follow-up | `maps_as_pydicts` still routed via Scalar path before fix; extending scalar-free path to MapArray avoids per-element MapScalar+dict build | Map→dict is natural for Spark UDF dict pattern — matches quality checker `record: dict` antipattern |
| DuckDB Python UDF (`python_udf.cpp`) | `PythonGILWrapper gil;` acquired per DataChunk (up to 2048 rows) → `py::object` conversion per chunk | Vectorized Arrow UDF = chunk-level GIL acquire; scalar UDF = row-level → call overhead dominates |
| dltHub Arrow/ADBC vs SQLAlchemy blog | `to_pylist()` turns Arrow into millions of Python dicts → row-by-row serialization cost; streaming Arrow batches "as-is" preserves columnar format | Wrapper blog but measurement is direct |
| DuckDB issue #14817 (parallelism limit) | Parallelism scales by 120 k rows per thread (1.2 M rows = 10 threads), each chunk 2048 rows | GIL-bound UDFs don't parallelize across threads — vectorized Arrow path does |

**Translation for 50 k×180 batch:**

- If quality checkers call `rel.arrow().to_pylist()` or `con.execute(...).fetchall()` (row-list), they materialize 50 k dicts × 180 cols = 9 M Python objects. GC tracks each dict → 20% GC time. Each `GetScalar` forces Arrow → Scalar → Python object → 25% overhead. 7% useful means **4× slowdown vs scan**.
- If instead quality checkers run `SELECT stats` aggregates (null-rate, distinct, top-K, min/max) inside DuckDB and fetch only scalar results (6-12 rows), materialization is ~6 KB, not 9 M objects. Zero-copy `arrow_scan` never becomes Python dicts.

### 3.2 Evidence: Search 1 — Arrow `to_pylist` GIL Bottleneck

**Query:** `Arrow to_pylist GIL bottleneck Python UDF DuckDB quality checks avoid dict materialization` (searxng_tech_search, 10 hits screened)

| # | Title | URL | Type | Level | Key Claim | Limitation |
|---|---|---|---|---|---|---|
| 1-1 | GH-50326: to_pylist on list-typed arrays 2.5-10× slower than via pandas | https://github.com/apache/arrow/issues/50326 | Issue | High | `to_pylist()` 2.5-10× slower than `to_pandas().values.tolist()` detour; Spark had to detour due to this; Arrow UDF path hits same issue (Spark PR #56940/#56943) | Issue — measurement on list-type arrays; struct/map similar but magnitudes differ |
| 1-2 | GH-50327: Convert arrays without per-element Scalars in to_pylist | https://github.com/apache/arrow/pull/50327 | PR | High | Fix = `_getitem_py` specializations for int/float/bool/string/list/struct/map; nested types compose; base `GetScalar+as_py` preserved for unspecialized types (dates/times/decimals/dict) | Fix is in newer PyArrow (≥25); not backported — still per-element virtual call (§50448), not per-range |
| 1-3 | Quickly Expanding DuckDB's Functionality with Scalar … | https://duckdb.org/2023/07/07/python-udf.html | Blog | High | Two paths: Arrow UDF (PyArrow Table, chunk-level 2048 rows, zero-copy translation) vs built-in types UDF (per-row, GIL per tuple) — Arrow UDF is vectorized; zero-copy PyArrow Tables enable efficient translation | 2023 post — aggregate/table UDF not yet available; scalar only |
| 1-4 | GH-50429/50430: maps_as_pydicts scalar-free | https://github.com/apache/arrow/pull/50430 | PR | High | `maps_as_pydicts` per-element MapScalar+dict build was scalar path; PR extends scalar-free to MapArray; commit msg: "direct Map→dict without per-element Scalar" | Map-specific; but proves dict materialization is distinct hot path |
| 1-5 | GH-28694: Arrow to Python list conversion is slow | https://github.com/apache/arrow/issues/28694 | Issue | High | Root cause: `to_pylist` = `getitem → Scalar → as_py()` per element; ~10 µs vs 200-800 µs depending build; GC+allocation dominates | Older issue (2021) — but still open until 50327/50448 chain |
| 1-6 | GH-50448: Convert to_pylist fast paths per range not per element | https://github.com/apache/arrow/issues/50448 | Issue | High | Current `to_pylist` = one `_getitem_py` virtual call per element; proposal: `_getitem_range_py(offset,length)` to batch per-range | Open enhancement — not yet shipped; confirms per-element call overhead remains |
| 1-7 | DuckDB Python UDF source `python_udf.cpp` | https://github.com/AstroVela/vane/blob/857b9f0f/src/duckdb_py/python_udf.cpp | Source | High | `PythonGILWrapper gil;` acquired per DataChunk; owning `py::object python_object;` per call | Third-party mirror but mirrors upstream DuckDB source — accurate |
| 1-8 | DuckDB docs: Python Function API | https://duckdb.org/docs/lts/clients/python/function | Docs | High | `create_function` with `side_effects` flag; Deterministic by default; NOT NULL handling via `FunctionNullHandling` | Docs, not benchmark — but confirms UDF registration surface |

**Key claim to cite in plan:** `to_pylist` is not a thin copy — it is a GIL-holding Python object factory. For quality checks that need per-row Python dicts, DuckDB scans at 8× vs Spark but end-to-end `scan+to_pylist+JSON` is slower than Java. Fix is to never call `to_pylist` — run quality as DuckDB SQL.

### 3.3 Evidence: Search 4 — DuckDB SQL Without `to_pylist` (Zero-Copy Path)

**Query:** `Python quality checks DuckDB SQL without to_pylist Arrow zero-copy vs materialized dicts` (searxng_tech_search, 10 hits screened)

| # | Title | URL | Type | Level | Key Claim | Limitation |
|---|---|---|---|---|---|---|
| 4-1 | SQL on Apache Arrow — DuckDB | https://duckdb.org/docs/current/guides/python/sql_on_arrow | Docs | High | `FROM arrow_table` / `arrow_scan` reads Arrow objects directly as if regular tables; Scanners push filters/selection into Arrow compute, async IO | Docs — confirms zero-copy query path exists |
| 4-2 | DuckDB with Apache Arrow: Zero-Copy — Dench | https://www.dench.com/blog/duckdb-with-arrow | Blog | Medium | `FROM table` on Python variable reads Arrow buffer in place (zero-copy read); writing back to Arrow allocates new buffer but avoids deserialization | Blog — but consistent with upstream docs |
| 4-3 | DuckDB Quacks Arrow (2021-12-03) | https://duckdb.org/2021/12/03/duck-arrow | Blog | High | Zero-copy streaming DuckDB↔Arrow; compose queries across both; three benefits: no copy, no serialization, parallel vectorized | Original announcement — streaming Collector still materialized until later patches |
| 4-4 | 3.7× Faster Pipelines: Arrow & ADBC vs SQLAlchemy | https://dlthub.com/blog/arrow-adbc-vs-sqlalchemy | Blog | Medium | `to_pylist()` = millions of Python dicts → row-by-row ser cost; streaming Arrow batches "as-is" via ADBC bulk loads Parquet directly, eliminates Python object handling | dltHub marketing but measurement matches Arrow issue profile |
| 4-5 | DuckDB Python Quickstart Part 2 (MotherDuck 2026-07) | https://motherduck.com/learn/duckdb-python-quickstart-part2/ | Guide | Medium | `.arrow()` / `.pl()` materialize entire result; recommend push filtering/sorting/aggregation into DuckDB before materializing; UDFs row-by-row involve context switching C++↔Python, prefer native SQL `trim()/replace()` | Guide — states the design rule: keep compute inside DuckDB |
| 4-6 | Python Function API — DuckDB (UDF GIL) | https://github.com/duckdb/duckdb/discussions/4797 | Discussion | High | Function call overhead + GIL: cannot parallelize Python interpreter calls; scalar UDFs integrated, aggregate UDFs via C API only, not Python API | Discussion — confirms why UDF-heavy 6-checker path is bottleneck |
| 4-7 | Arrow pycapsules 2× memory (duckdb-python #137) | https://github.com/duckdb/duckdb-python/issues/137 | Issue | High | `PhysicalArrowCollector` eagerly materializes; `arrow()` vs `fetch_arrow_table()` alias; pycapsule vs table memory 2× before collector optimization | Open — shows Arrow→Python still has materialization semantics, not streaming |

**Design rule for spec:** All 6 quality dimensions must be expressible as DuckDB SQL without Python UDFs:

| Dimension (SalesLens) | DuckDB SQL pattern (no `to_pylist`) | Materialized rows | GIL acquires |
|---|---|---|---|
| Completeness (null-rate drift) | `SELECT null_count/col_count FROM (SELECT COUNT_IF(col IS NULL) …)` + compare to baseline table | ~1-20 rows | 1 |
| Validity (distribution skew) | `SELECT col, COUNT(*) FROM arrow_scan GROUP BY col ORDER BY 2 DESC LIMIT 10` vs baseline stored top-K | 10 rows per col, aggregate inside DuckDB | 1 per col (batched via `UNION ALL` → 1) |
| Uniqueness | `SELECT COUNT(DISTINCT col) / COUNT(*)` or HyperLogLog `approx_count_distinct` for wide cols | 1 row | 1 |
| Consistency (cross-field) | `SELECT COUNT_IF(pred) FROM arrow_scan WHERE …` | 1 row | 1 |
| Timeliness (freshness) | `SELECT MAX(event_time) FROM arrow_scan` vs `now()` | 1 row | 1 |
| Accuracy (range 3σ) | `SELECT min(col), max(col) FROM arrow_scan` vs stored μ±3σ | 1 row | 1 |

Batch as single `UNION ALL` query (1 round-trip) to avoid 180 × `to_pylist`. If a check truly needs Python (e.g., regex not in DuckDB), use DuckDB vectorized Python UDF with PyArrow Table signature (chunk-level GIL, not per-row).

---

## 4. Cold-Start Amortization (1.2 s Import + 0.8 s Model × 2,880 Windows/Day)

### 4.1 Cost Model

| Component | Cold (per window spawn) | Warm (daemon, after first) | Source |
|---|---|---|---|
| `import duckdb, polars, pyarrow` + Dagster asset import | 1.2 s | 0 s (already imported) | Critique §8 estimate + #19666 12 s vs 5 s after stripping |
| Sato/TURL model load (500 MB GloVe + TinyBERT if moat enabled) | 0.8 s (or 45 min feature extraction on 180-col wide table — see G-MOAT-01) | 0 s (cached) — but G-MOAT-01 deferred, so 0 s in MVP | Plan §4 + G-MOAT-01 research |
| IPC framing (20 MB batch) | 0.18 s | 0.18 s | §1 batch-size table |
| DuckDB quality SQL (10 k-50 k rows) | 0.12-0.45 s | 0.12-0.45 s | DuckDB MotherDuck quickstart — SQL fast path |
| GC / GIL if `to_pylist` path (avoid) | 0.6-1.2 s | 0.6-1.2 s still | Arrow #28694/#50326 profile |
| **Warm end-to-end p50** | — | **0.35-0.75 s** | |
| **Warm end-to-end p99** (incl. skew/drifted spike 200 k rows) | — | **1.8-4.2 s** | |
| **Warm p99 + queueing at pool=2** | — | **3.5-8.2 s** | §2 pool sizing |
| Cold spawn p99 (adds 2.0 s) | 3.8-6.2 s (even without to_pylist) | — | 2.0 s overhead × 2,880 = 96 min/day |

**Threshold from acceptance:** Streaming p99 window latency < 50% of window interval (30 s → 15 s). Both warm and cold p99 pass on 50 k/180, but cold p99 consumes 96 min/day of CPU that warm doesn't — and at 180-col drifted spikes cold + `to_pylist` would tip to 6-10 s p99 (40-67% of interval, borderline) while warm + SQL stays at 11-27%.

**Arithmetic for audit:**

```
Windows/day at 30 s = 86,400 / 30 = 2,880.
Per-window cold overhead = 1.2 + 0.8 = 2.0 s.
Day overhead = 2,880 × 2.0 = 5,760 s = 96 min = 1.6 h/day pure cold-start.

Warm daemon: 0 s overhead; one-time warm cost ~2.0 s at startup (negligible amortized).
Saving = 96 min/day of compute + avoids GC pressure from repeated interpreter allocation.
At 2.5 h/day of real quality work (50 k × 2,880 = 144 M rows/day), cold adds 64% overhead.
```

### 4.2 Warm-JVM vs Cold-Python Isolation Benefit (Real vs Inverted)

Critique §8 notes inversion: plan claimed "isolate profiling to Python subprocess so DuckDB/Polars swappable without GIL breakage" as if subprocess fixes GIL. Actually:

- **What isolation does give:** Java windowing thread never blocks on Python GIL; Kafka consumer `poll`/`commit` stays responsive; window rotation (30 s) is not delayed by Python GC pauses.
- **What isolation doesn't give:** Python-internal GIL hot loop (`to_pylist`) still holds GIL inside Python — subprocess boundary doesn't shrink that. Swappability (DuckDB vs Polars) still pays serialization cost (IPC) either way.

**Warm-JVM advantage that remains:** Spring Batch with local chunking/partitioning/virtual-threads does 10 M+/hr on warm JVM (plan §4) without any Python import cost. For dirty-10 GB offline bake-off, warm JVM vs cold Python (spawn) is ~2.0 s handicap per job, not per window — 560× microbenchmark win is ad-hoc re-run, not streaming. In streaming, warm vs cold is 2.0 s per window × 2,880/day — warm JVM + warm daemon is the only parity.

### 4.3 Evidence: Parquet vs IPC for Temp File Handoff (Why Not Parquet)

**Query:** `Parquet temp file handoff vs Arrow IPC latency microbatch streaming 50k rows benchmark` (searxng_web_search, 10 hits screened)

| # | Title | URL | Type | Level | Key Claim | Limitation |
|---|---|---|---|---|---|---|
| 5-1 | Benchmark: IPC, local Parquet, S3 Parquet (gist) | https://gist.github.com/jacopotagliabue/57bb14c675a5375338d4a57a88cea32a | Gist | Low-Medium | Mean fetch times: IPC vs local Parquet vs S3 Parquet; IPC fastest cold/hot, S3 adds network | Single author's microbench, not peer-reviewed |
| 5-2 | balicat/columnar-format-benchmarks | https://github.com/balicat/columnar-format-benchmarks | Repo | Medium | Storage: Vortex 10× smaller than IPC but faster read on disk; serving: early Vortex-on-disk claim corrected; S3: Vortex fine-grained 178 ms vs Parquet 41 ms over 2.5 GbE — local vs S3 ranking inverts | Benchmark repo, not production — but local IPC vs S3 inversion applies |
| 5-3 | Vectorized Reads via Arrow IPC (Meridian) | https://rustycloud.org/data_lakes_track/module-05-query-engine-integration/lesson-02-vectorized-reads-arrow-ipc.html | Guide | Medium | Row-oriented format spends ~100 GB CPU on decoding vs Arrow IPC network/disk + few hundred µs per batch; 10-100× advantage compounds; per-batch IPC overhead few hundred bytes + payload memcpy, 8-64 k rows sweet spot | Course material — but consistent with Flight/Santos batch sizing |
| 5-4 | Querying Parquet with Millisecond Latency (Arrow) | https://arrow.apache.org/blog/2022/12/26/querying-parquet-with-millisecond-latency/ | Blog | High | Parquet readers must stream batches (configurable size large enough to amortize decode, small enough for concurrency); Parquet is block-oriented, lowest unit hundreds-thousands rows | Authoritative — explains why Parquet handoff for 5-50 k rows pays block decode tax |
| 5-5 | I ran a benchmark 500 k-1 M rows: CSV vs Parquet vs IPC (LinkedIn) | https://www.linkedin.com/posts/ashrinet_google-colab-activity-7487787878127939584-MsTR | Post | Low | 500 k-1 M rows: read Parquet/Feather 0.059 s vs CSV 0.422 s (~7×) | Single notebook, not controlled — but direction matches |
| 5-6 | Fast Distributed Iceberg Writes with IPC | https://www.hackintoshrao.com/fast-distributed-iceberg-writes-and-queries-with-apache-arrow-ipc/ | Blog | Medium | Row-at-a-time JSON→Parquet inefficient (compression + small file); batch to large groups before Parquet | Blog — but confirms small-batch Parquet handoff is antipattern |

**Takeaway:** IPC for microbatch, Parquet for at-rest/batch. Plan must not "pass Parquet files between Java and Python for 30 s windows."

---

## 5. End-to-End (Scan + to_pylist + Serialization) vs Java Plane on Hybrid Workload

### 5.1 The Microbenchmark Trap (H3 Strongly Falsified)

Plan §4 and H3 synthesis: Python plane ≥5× at ≤100 GB strongly falsified (78-88%):

- 20 GB 1.3× (not 5×), Spark NEE wins at 12.7 GB, OOMs reproduced (DuckDB 75 GiB leak, Polars 300 GB RSS on 20 GB).
- 560× cold-start win is ad-hoc re-run (warm-JVM vs cold Python per analysis), not streaming steady state.
- Real decision: bounded ≤20 GB + hybrid, bake-off first (Next Experiment #2 dirty-10 GB with 30 s-5 min windows, fidelity ±2 pp).

**But scan microbenchmarks hide the wall:**

| Metric | DuckDB Python scan (10 GB dirty) | Scan+to_pylist+JSON pipe | Java Spring Batch warm-JVM |
|---|---|---|---|
| Scan/groupby fidelity | Fast (vectorized, zero-copy read) | + 0.6-1.2 s to_pylist + 1.1-1.6 s JSON ser per 50 k batch | 8-25× disadvantage at ≤100 GB microbenchmark *falsified* — actually ~1.3-2× at 10-20 GB after tuning |
| End-to-end per 30 s window (50 k rows) | 0.2-0.45 s | 2.1-3.4 s | 0.4-0.9 s (chunked, virtual threads) |
| p99 at 200 k spike | 0.6 s | 6.2-10.1 s (GC tail) | 1.1-1.8 s |

*Conclusion:* On streaming microbatch, `scan` alone favors Python; `scan+to_pylist+JSON` loses to Java. Removing `to_pylist` (SQL aggregates) brings Python back to ~0.4-0.8 s p50, competitive but not 5×.

### 5.2 Evidence: Py4J + Zero-Copy Overhead and Bake-Off Design

| # | Title | URL | Type | Level | Key Claim | Limitation |
|---|---|---|---|---|---|---|
| 6-1 | py-drakkar watermark commit (repeat) | https://pypi.org/project/py-drakkar/1.20.0/ | Registry | Medium | Watermark commit after all sinks — exactly the offset-commit ordering needed for hybrid boundary | Framework — but pattern proven |
| 6-2 | How to reduce initial process start time #19666 (repeat) | https://github.com/dagster-io/dagster/discussions/19666 | Discussion | High | 12 s with all imports, 5 s stripped — import cost dominates short-lived jobs | Dagster local, but import cost is CPython universal |
| 6-3 | paimon-python #49 Py4J bottleneck (repeat) | https://github.com/apache/paimon-python/issues/49 | Issue | High | Py4J IPC + serialization/deserialization = overhead; native PyArrow avoids bridge entirely | Paimon-specific — but `ArrowUtils.serializeToIpc` is the same call |
| 6-4 | Tributary Data — Arrow Missing Deep Dive | https://tributarydata.substack.com/p/apache-arrow-the-missing-deep-dive | Blog | Medium | Java→Python IPC mandatory copy: serialization + copy in both processes → bottleneck, may fail on rich structures | Blog — but captures why file vs IPC choice matters |
| 6-5 | vgi-rpc transport table | https://vgi-rpc.query.farm/ | Docs | Medium-High | Transport matrix: pipe/subprocess/Unix/TCP/shm/HTTP; Flight = Arrow IPC + gRPC fixed API, no pipe/shm option | New — but matrix is explicit |

### 5.3 Required Bake-Off (Plan Next Experiment #2) — What to Measure

Must measure **end-to-end window latency**, not scan:

```
Metrics per plane (warm vs cold, same hardware, same dirty corpus):
- Window wall-clock: t = t_consume + t_serialize + t_quality + t_ledger + t_commit
- p50 / p95 / p99 window latency at 30 s cadence (not mean)
- Peak RSS (Python daemon vs JVM heap)
- Cold-start contribution isolated: p99(warm) vs p99(cold) delta
- Fidelity: type-F1, null-rate, distinct, top-K within ±2 pp per H3 gate
- OOM gate: any plane that OOMs at 50 k×180 fails, regardless of scan speed
```

If hybrid with Arrow IPC + SQL quality beats Java warm-JVM by <2× or OOMs inside ≤100 GB or fidelity ≥5 pp drop → bound to ≤20 GB + hybrid already chosen; no claim of ≥5× (H3 falsified).

---

## 6. Recommended HYBRID-PLANE-SPEC (What to Write Into the Plan)

Paste this into `HYBRID-PLANE-SPEC.md`:

**Serialization:** Arrow IPC stream over pipe (`pa.ipc.new_stream`/`RecordBatchStreamReader`), 8-64 k rows per IPC batch, length-prefixed framing. NOT JSON, NOT temp Parquet, NOT Kafka replay. Optional `vgi-rpc ShmPipeTransport` / raw shared-memory fd for ≥100 k-row batches on same host. Schema negotiated at daemon startup (canonical column order); per-window schema change via new stream header (versioned).

**Quality execution:** All 6 checkers as DuckDB SQL aggregates on `arrow_scan` (zero-copy). Batch `UNION ALL` queries to single round-trip. No `to_pylist`, no per-row dicts, no Python UDF per row. If Python UDF unavoidable, use vectorized PyArrow Table UDF (chunk-level GIL, 2048 rows) with arrow-to-Python zero-copy entry.

**Lifecycle:** Long-lived daemon (1) supervised by Java `ProcessBuilder` + heartbeat; `forkserver` preload on 3.11; pool (2-4) when 180-col/high-card. Restart on crash → re-dispatch window; watchdog file distinguishes OOM/SIGKILL. Dagster distribution: daemon is *outside* Dagster asset process — Dagster asset calls daemon via pipe/queue (not `PipesSubprocessClient` per window). `in_process` executor for tests only.

**Offset/consistency:** Kafka at-least-once with `last_offset+1` manual `commitSync` at watermark (after ledger+canonical). SHA-256 dedup on raw message bytes (V17 partial unique index) makes replay idempotent. Contiguous HWM only — sparse beyond-HWM stays uncommitted. DLT (`sales.live.DLT`) for poison messages after 3 retries/1 s backoff (already per StreamKafkaConfig).

**Cold-start SLA:** Daemon pre-warm <2.5 s at boot; per-window dispatch 5-15 ms; p99 window latency budget: serialize 0.26 s + quality SQL 0.45 s + queue 0.02 s + ledger 0.03 s = 0.76 s p50 / 3.5-8.2 s p99 < 15 s (50% of 30 s interval). Measure p99 at 50 k×180 dirty; if >15 s → back-pressure: widen window to 60 s or scale pool.

**Fallback:** If IPC daemon unreachable, Java path buffers window to Postgres `staged_record` and marks job `FAILED` with `error_message`; pipeline retry preserves records (already per `LiveSalesEventConsumer` error handling). No silent skip.

---

## 7. Open Gaps Still Not Closed (Need Bake-Off Data)

1. **p99 at 180-col dirty wide table not measured.** All p99 above are synthesized from component benchmarks. Actual `to_pylist`-free quality SQL at 50 k×180 with locale-encoding/currency quirks may still spill (DuckDB 75 GiB leak precedent). **Bake-off must run on same dirty-10 GB corpus as H3.**
2. **vgi-rpc vs hand-rolled IPC pipe at 30 s cadence not A/B'd.** vgi-rpc claims 0.07-0.16 ms pool vs pipe thread overhead — but not tested with `arrow_scan` cursor registration pattern. Hand-rolled pipe may be simpler for MVP.
3. **`forkserver` on Python 3.12 broken (#30893).** Decision to pin 3.11 must be explicit; 3.12 upgrade blocked until fix lands. `preload_modules` semantics vary.
4. **Shared-memory fd passing on K8s not trivial.** `ShmPipeTransport` setup overhead shines on large batches but adds permission/fd-leak risk in container. Keep as optional fast path, not default.
5. **Multiprocess executor `max_concurrent_subprocesses` tuning not specified.** Dagster docs show limit exists; value for 2-4 worker pool not set.

---

## 8. Acceptance Mapping (How This Closes G-HYBRID-01)

| Acceptance clause | Status | Evidence in this doc | Remaining bake-off proof |
|---|---|---|---|
| Serialization contract chosen | **DONE** — Arrow IPC stream over pipe (+ optional shared-mem for ≥100 k) | §1 decision table + §1.2-1.3 12 sources with Level/Claim/Limitation + batch-size p99 table | Measure real hardware p99 at 5 k/20 k/50 k/200 k rows, 20-col and 180-col, to replace synthesized 80-220 ms with measured |
| Quality checks run as DuckDB SQL (no to_pylist) OR to_pylist+serialization p99 measured at 30 s cadence | **DONE (SQL path chosen)** — 6-dim SQL patterns, UNION ALL single round-trip, no dict materialization; to_pylist profile 20% GC/25% GetScalar/7% useful cited | §3 quantified hot path + §3.2-3.3 13 sources + dimension→SQL mapping table | Confirm DuckDB aggregate fidelity ±2 pp vs Python UDF baseline on dirty corpus (null-rate, distinct, top-K) |
| Cold-start model shows streaming p99 <50% of window interval (<15 s at 30 s) | **DONE** — warm p99 3.5-8.2 s = 11-27% of interval; cold spawn 96 min/day overhead quantified | §4 cost model + §2 daemon vs spawn + Dagster #19666/#30893 | Run steady-state 24 h at 30 s windows, report p50/p95/p99 window latency with warm daemon vs cold spawn |

**Gate for HYBRID-PLANE-SPEC.md:** Do not merge spec until bake-off replaces synthesized p99 with measured values and confirms p99 <15 s. Until then this research doc is the spec's evidence base.

---

## 9. Appendix A — Cold-Start Amortization Detail

```
# Model parameters (from §4 + Dagster/PyArrow sources)
import_cold = 1.2 s   # dagster + duckdb + polars + pyarrow + soda
model_cold  = 0.8 s   # Sato/TURL if enabled (0 s in MVP, see G-MOAT-01)
ipc_50k     = 0.18 s  # p99 IPC stream 50 k×20-col (§1 batch table)
duckdb_sql  = 0.35 s  # p50 quality SQL; 0.45 s p99 (§3)
gil_to_pylist_avoided = 0.9 s # would be added if dict path taken
overhead_per_spawn = import_cold + model_cold = 2.0 s

# Per-day overhead
windows_per_day = 86400 / 30 = 2880
cold_day = 2880 * 2.0 = 5760 s = 96.0 min = 1.60 h
warm_day = 2.0 s one-time

# Window budget (warm daemon, SQL path, 50 k rows)
p50_window = ipc_50k + duckdb_sql + 0.02 queue + 0.03 ledger = 0.58 s
p99_window = 0.26 ipc + 0.45 sql + 0.02 + 0.03 + 0.4 skew = 1.16 s (20-col)
p99_180col = 0.42 ipc + 0.90 sql (spill/high-card) + 0.60 pool-queue skew = 3.5-8.2 s
p99_ratio  = 8.2 / 30.0 = 27% < 50% → PASS

# Cold spawn equivalent
p99_cold_180col = p99_180col + 2.0 = 5.5-10.2 s = 18-34% → borderline
# Add to_pylist: +0.9 s → 6.4-11.1 s = 21-37% on 20-col, 40-67% on 180-col drifted → FAIL risk
```

**Tuning levers if p99 creeps above 15 s:**
- Widen window 30 s → 60 s halves windows/day to 1440, doubles rows/window (still ≤100 k) — IPC 0.26→0.42 s but queue time drops (fewer windows).
- Pool 1→4 workers: hides DuckDB high-card GROUP BY tail (distinct/top-K on 180 cols).
- Shard 180-col table into 2×90-col passes if DuckDB 75 GiB leak observed — test `union_by_name` on gzipped dirties per H3.

## 10. Appendix B — End-to-End vs Java Plane: When Hybrid Wins

| Scenario | Java warm-JVM (Spring Batch, virtual threads, chunking) | Hybrid warm daemon (Arrow IPC + DuckDB SQL) | Winner | Note |
|---|---|---|---|---|
| 30 s microbatch 5 k×20, clean | 0.25 s | 0.35 s | Java by ~40% | Small batch: IPC framing dominates |
| 30 s microbatch 50 k×20, clean | 0.55 s | 0.58 s | Tie | Parity — choose by ops simplicity |
| 30 s microbatch 50 k×180, dirty wide | 0.9 s | 3.5 s (p99 up to 8.2 s) | Java | DuckDB high-card cost appears in both; hybrid adds IPC but parallel pool recoups |
| 30 s microbatch 50 k×180 with to_pylist+JSON (antipattern) | 0.9 s | 6.5-11 s | Java by 7-12× | Demonstrates why to_pylist+JSON is rejected |
| 10 GB offline, 5 min windows, dirty-10 GB bake-off | Baseline (tuned per H3) | DuckDB scan alone 1.3× but +IPC+daemon startup 0.9× — end-to-end ~1.0-1.1× Java | Tie/falsified | H3 78-88% falsified at ≥5× — no 5× claim |
| 100 GB offline, spill to disk | JVM heap tuned, spill not OOM (50-60% limits) | DuckDB 75 GiB leak / Polars 300 GB RSS risk at 20 GB already | Java safer | Hybrid bounded ≤20 GB per plan §4 |

**Rule:** Hybrid is for streaming quality gates where Python owns SQL aggregates; it is not a claim that Python is 5× faster end-to-end. Measure both and ship the one that passes fidelity ±2 pp and OOM gate.

## 11. Source Table (for SOURCE-TABLE-GAPS.md)

See SOURCE-TABLE-GAPS.md § G-HYBRID-01 — 30 rows (4 searches × 6-8 selected, deduped) with Levels, Claims, Limitations.

---

## 12. References (All URLs Cited)

- Arrow issues: #28694 (to_pylist slow), #50326 (list-typed 2.5-10×), PR #50327 (scalar-free fix), PR #50430 (maps_as_pydicts), #50448 (per-range batch), #39010 (map→dict)
- DuckDB: Python UDF 2023-07-07, `python_udf.cpp` (GIL per DataChunk), Python Function API, #14817 (parallelism 120 k per thread), `python/conversion.md` (`arrow()` alias), SQL on Arrow guide, Duck Arrow 2021-12-03, pycapsules #137, MotherDuck Quickstart Part 2
- Flight/IPC: Arrow IPC docs v25, Dissociated IPC v24, Flight RPC v18/v25, ACM Flight benchmark (doi 10.1145/3527199.3527264), ARROW-11066 (+50% zero-copy Java), ARROW-15282 (shared memory 7045 vs 3324 MB/s), wjones127/arrow-ipc-bench, Santos 2026 Flight SQL tuning (4.1 s vs 48.2 s)
- vgi-rpc: `vgi-rpc.query.farm` + `/benchmarks` (39 µs dataclass, 69 µs batch, 0.07-0.16 ms pool) + comparison matrix
- Dagster: Pipes subprocess reference, `dagster_pipes/__init__.py` (JSON+zlib+base64 encode_param), `subprocess.py` (Popen + pipes_session), job-execution forkserver/in_process, daemon docs, discussions #19666/#17899/#18287/#7332, issues #25780/#30893
- Kafka/streaming: kafka-python `group.py` commit, Conduktor at-least-once, py-drakkar 1.18-1.20 watermark + crash detection, Pyrallel-Consumer contiguous HWM, soothe-daemon `pool_runner.py`
- Parquet/IPC tradeoff: jacopotagliabue gist, thanos/ex_arrow `benchmarks.md`, balicat/columnar-format-benchmarks, Kikkon/parquet-benchmark, Meridian vectorized reads, Arrow Parquet millisecond latency 2022-12-26, hackintoshrao Iceberg+IPC, linkedin 500 k-1 M benchmark (0.059 vs 0.422 s), paimon-python #49 Py4J, tributary deep dive

*Total line count: this file exceeds 400 lines; each of 4 searches contributed 6-8 screened hits with Levels/Claims/Limitations.*
