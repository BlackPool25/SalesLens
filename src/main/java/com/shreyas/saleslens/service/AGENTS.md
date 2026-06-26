# service/ — Business Logic Layer

**Part of SalesLens** — see root AGENTS.md for overall conventions, build commands, and project structure.

## OVERVIEW
Core business logic: 19 classes across 6 packages (root + 5 subdomains). Highest complexity in the codebase — ~2,400 lines across all service files.

## STRUCTURE
```
service/
├── AuthService.java              # Register/login, password encoding (BCrypt 12), JWT generation
├── DataSourceService.java        # CRUD for data sources, user-scoped queries
├── OrderProducer.java            # Kafka producer for `inventory-updates` topic
├── OrderConsumer.java            # Kafka consumer for `inventory-updates` topic
├── SchemaManagementService.java  # Promote/demote canonical fields, registry mutations
├── SemanticMapperService.java    # Heuristic-primary + optional LLM advisory mapping (~429 LOC)
├── TransformationService.java    # Raw record → canonical JSON transformation
├── inference/                    # Schema inference engine
│   ├── SchemaInferenceService.java    # Drift detection, schema instantiation
│   └── TypeDetectionService.java      # Deterministic type chain (INTEGER→DECIMAL→...→FREE_TEXT)
├── ingestion/
│   └── IngestionOrchestrator.java     # CSV save → JobOperator.start() → return jobId
├── quality/                      # Data quality engine
│   ├── QualityChecker.java            # Interface (check() → List<QualityIssue>)
│   ├── QualityEngineService.java      # Orchestrates all 6 checkers + ProfilingService drift detection
│   ├── QualityScoreService.java       # Score calculation + letter grade
│   ├── ProfilingService.java          # Statistical baseline drift detection (null rate, distribution, range)
│   ├── CompletenessChecker.java       # Null checks on required fields
│   ├── ValidityChecker.java           # Format/type validation
│   ├── UniquenessChecker.java         # Duplicate detection
│   ├── ConsistencyChecker.java        # Cross-field logical constraints
│   ├── TimelinessChecker.java         # Date recency boundaries
│   └── AccuracyChecker.java           # Range/value reasonableness
└── advisory/                     # (NEW) Optional LLM advisory features
    └── QualityExplanationService.java # Async LLM quality explanations (best-effort, non-blocking)
```

## WHERE TO LOOK
| Task | File(s) |
|------|---------|
| Field mapping logic | `SemanticMapperService.java` — heuristic-primary chain + optional LLM advisory |
| Schema inference | `SchemaInferenceService.java`, `TypeDetectionService.java` |
| Quality checks | `quality/*Checker.java` — 6 implementations |
| CSV ingestion flow | `IngestionOrchestrator.java` → `batch/csv/CsvIngestionJobConfig.java` |
| Canonical registry | `SchemaManagementService.java` — promote/demote field mutations |
| Kafka messaging | `OrderProducer.java`, `OrderConsumer.java` |

## UNIQUE PATTERNS
- **Heuristic-primary mapping**: Heuristic chain (exact → Levenshtein → token overlap → type fallback) runs first — always, with zero external dependencies. Ollama LLM is optional advisory; its result is used only if confidence exceeds heuristic confidence and output passes registry validation.
- **Statistical drift detection**: `ProfilingService` compares current batch FieldProfile statistics against historical baselines. Requires ≥3 batches (cold-start guard). Detects null rate shifts, value distribution skew, and range expansion.
- **Static mutable registry**: `SemanticMapperService.REGISTRY` is `static final CopyOnWriteArrayList` — mutated at runtime by promote/demote endpoints
- **Pluggable checkers**: `QualityChecker` interface + 6 implementations wired into `QualityEngineService` — add new dimensions by implementing the interface and registering
- **Deterministic type chain**: `TypeDetectionService` tries types in strict order: INTEGER → DECIMAL → BOOLEAN → DATE → DATETIME → EMAIL → PHONE → CURRENCY_AMOUNT → CATEGORY → FREE_TEXT
- **Programmatic Batch jobs**: `IngestionOrchestrator` launches jobs via `JobOperator.start()` with job parameters, not auto-execution
- **Type fallback in mapping**: `HeuristicSimilarityMatcher` class (inner) handles no-match fields with a 0.55 confidence type-based fallback

## ANTI-PATTERNS
- Never delete `QualityChecker` implementations from the `checkers` list in `QualityEngineService` — all 6 must be present
- Never use `@Profile("!test")` directly on service beans (use `OllamaConfig` pattern instead)
- Never call `OrderProducer` without a valid `IngestionJob` context — it logs jobId and sourceId
- **ProfilingService cold-start guard**: The service requires ≥3 batches of profiling data before drift detection activates. Do not force-drift detection with fewer batches — it will produce unreliable results.
- **QualityExplanationService is best-effort only**: Never depend on its return value for pipeline decisions. It returns `CompletableFuture<Void>` and failures are silently logged. Blocking on it defeats the "non-blocking" purpose.
- **SemanticMapperService LLM advisory**: The LLM result is used only if it passes registry validation AND has higher confidence than the heuristic result. Do not bypass this validation — without it, the LLM can hallucinate non-existent entity/field names.
- **`ChatLanguageModel` must be `@Autowired(required = false)`** in the SemanticMapperService. Use `@Profile("!test")` / `@Profile("test")` on the bean definition to provide a test mock.
