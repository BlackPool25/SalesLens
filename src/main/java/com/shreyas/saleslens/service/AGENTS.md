# service/ — Business Logic Layer

**Part of SalesLens** — see root AGENTS.md for overall conventions, build commands, and project structure.

## OVERVIEW
Core business logic: 16 classes across 4 packages (root + 3 subdomains). Highest complexity in the codebase — 2,179 lines across all service files.

## STRUCTURE
```
service/
├── AuthService.java              # Register/login, password encoding (BCrypt 12), JWT generation
├── DataSourceService.java        # CRUD for data sources, user-scoped queries
├── OrderProducer.java            # Kafka producer for `inventory-updates` topic
├── OrderConsumer.java            # Kafka consumer for `inventory-updates` topic
├── SchemaManagementService.java  # Promote/demote canonical fields, registry mutations
├── SemanticMapperService.java    # Heuristic + Ollama LLM field matching (~559 LOC, largest file)
├── TransformationService.java    # Raw record → canonical JSON transformation
├── inference/                    # Schema inference engine
│   ├── SchemaInferenceService.java    # Drift detection, schema instantiation
│   └── TypeDetectionService.java      # Deterministic type chain (INTEGER→DECIMAL→...→FREE_TEXT)
├── ingestion/
│   └── IngestionOrchestrator.java     # CSV save → JobOperator.start() → return jobId
└── quality/                      # Data quality engine
    ├── QualityChecker.java            # Interface (check() → List<QualityIssue>)
    ├── QualityEngineService.java      # Orchestrates all 6 checkers, creates run
    ├── QualityScoreService.java       # Score calculation + letter grade
    ├── CompletenessChecker.java       # Null checks on required fields
    ├── ValidityChecker.java           # Format/type validation
    ├── UniquenessChecker.java         # Duplicate detection
    ├── ConsistencyChecker.java        # Cross-field logical constraints
    ├── TimelinessChecker.java         # Date recency boundaries
    └── AccuracyChecker.java           # Range/value reasonableness
```

## WHERE TO LOOK
| Task | File(s) |
|------|---------|
| Field mapping logic | `SemanticMapperService.java` — LLM prompt + heuristic fallback chain |
| Schema inference | `SchemaInferenceService.java`, `TypeDetectionService.java` |
| Quality checks | `quality/*Checker.java` — 6 implementations |
| CSV ingestion flow | `IngestionOrchestrator.java` → `batch/csv/CsvIngestionJobConfig.java` |
| Canonical registry | `SchemaManagementService.java` — promote/demote field mutations |
| Kafka messaging | `OrderProducer.java`, `OrderConsumer.java` |

## UNIQUE PATTERNS
- **Dual mapping strategy**: Ollama LLM first, then heuristic fallback (exact → Levenshtein → token overlap → type fallback)
- **Static mutable registry**: `SemanticMapperService.REGISTRY` is `static final CopyOnWriteArrayList` — mutated at runtime by promote/demote endpoints
- **Pluggable checkers**: `QualityChecker` interface + 6 implementations wired into `QualityEngineService` — add new dimensions by implementing the interface and registering
- **Deterministic type chain**: `TypeDetectionService` tries types in strict order: INTEGER → DECIMAL → BOOLEAN → DATE → DATETIME → EMAIL → PHONE → CURRENCY_AMOUNT → CATEGORY → FREE_TEXT
- **Programmatic Batch jobs**: `IngestionOrchestrator` launches jobs via `JobOperator.start()` with job parameters, not auto-execution
- **Type fallback in mapping**: `HeuristicSimilarityMatcher` class (inner) handles no-match fields with a 0.55 confidence type-based fallback

## ANTI-PATTERNS
- Never delete `QualityChecker` implementations from the `checkers` list in `QualityEngineService` — all 6 must be present
- Never use `@Profile("!test")` directly on service beans (use `OllamaConfig` pattern instead)
- Never call `OrderProducer` without a valid `IngestionJob` context — it logs jobId and sourceId
