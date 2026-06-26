# PROJECT KNOWLEDGE BASE

**Generated:** 2026-06-26
**Commit:** 9c923fb
**Branch:** main

## OVERVIEW
SalesLens — multi-source sales data unification platform. Java 25 / Spring Boot 4 backend that ingests, profiles, semantically maps, and quality-checks heterogeneous sales data.

## STRUCTURE
```
.
├── src/main/java/com/shreyas/saleslens/   # Application source
│   ├── SaleslensApplication.java          # @SpringBootApplication entry point
│   ├── batch/csv/                         # Spring Batch CSV ingestion pipeline
│   ├── config/                            # Security, Jackson, Kafka, Ollama, JWT config
│   ├── controller/                        # 8 REST controllers (auth, ingest, schema, quality, jobs)
│   ├── dto/                               # 11 request/response DTOs
│   ├── mapper/                            # MapStruct entity↔DTO mapper
│   ├── model/                             # 14 JPA entities + 6 enums
│   ├── repository/                        # 14 Spring Data JPA repositories
│   ├── security/                          # Custom UserDetailsService, UserPrincipal
│   ├── service/                           # Business logic layer (see service/AGENTS.md)
│   └── util/                              # JwtUtil, ProducerTrigger
├── src/main/resources/
│   ├── application.yaml                   # Spring Boot config (env-var-driven)
│   └── db/migration/                      # 14 Flyway migrations (V1–V14)
└── src/test/java/.../                     # 10 test classes (JUnit 5 + Mockito)
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Start here | `SaleslensApplication.java` | `@EnableScheduling`, `@EnableKafka` |
| REST API routes | `controller/*.java` | 8 controllers, ~22 endpoints |
| Business logic | `service/` | 3 sub-packages: inference, ingestion, quality |
| JPA entities | `model/*.java` | 14 entities, 6 enums |
| DB schema | `resources/db/migration/` | 14 Flyway migrations |
| Infra setup | `docker-compose.yml` | Postgres, Redis, Kafka (KRaft), backend |
| API docs | `README.md` | Endpoint reference with payloads |
| E2E test | `verify_e2e.py` | Full pipeline Python script |
| Unit tests | `src/test/java/` | JUnit 5 + Mockito |

## CONVENTIONS
- **Layered architecture**: Controller → Service → Repository → JPA Entity (standard Spring Boot)
- **Lombok everywhere**: `@Getter/@Setter/@RequiredArgsConstructor` on models, `@Slf4j` + constructor injection on services
- **MapStruct**: `@Mapper(componentModel = "spring")` for entity↔DTO mapping, annotation processor order: Lombok before MapStruct
- **UUID PKs**: `GenerationType.UUID` on entities; `ddl-auto: validate` (schema via Flyway only)
- **JWT auth**: Bearer token in `Authorization` header; `/auth/**` is public, all else requires auth
- **Test style**: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` for unit; `@WebMvcTest` + MockMvc for controller; no test properties file
- **SQL Convention**: keywords UPPER, snake_case tables/columns, Flyway `V{number}__{desc}.sql`
- **No linter/formatter config** — IntelliJ defaults apply (4-space indent, Egyptian braces)
- **No CI/CD** — builds/CI not configured

## ANTI-PATTERNS (THIS PROJECT)
- **Never edit a Flyway migration once applied** — use a new V{n+1} migration
- **All 6 quality checkers must be registered** in `QualityEngineService.checkers` list when adding new dimensions
- **Ollama config has test/mock profile** — `@Profile("!test")` used; new features using Ollama should follow the same pattern
- **No `application-test.yaml`** — `@ActiveProfiles("test")` points to nonexistent file; add one if needed for test-specific config
- **Kafka auto-startup is always enabled** — set `spring.kafka.listener.auto-startup: false` in tests that don't need Kafka

## COMMANDS
```bash
# Build + test (fast, skips heavy context test)
./mvnw test -Dtest='!SaleslensApplicationTests'

# Full build
./mvnw clean package

# Run full stack
docker compose up -d --build

# E2E verification
python3 verify_e2e.py
```

## NOTES
- Ollama `qwen3.5:9b` runs on **host machine** (not in Docker), accessible via `host.docker.internal:11434`
- Backend maps host:8500 → container:8080
- `spring.batch.job.enabled: false` — jobs launched programmatically via `JobOperator.start()`, never auto-started
- Static canonical field registry in `SemanticMapperService.REGISTRY` — mutable at runtime via promote/demote endpoints
- Java 25 — verify Maven Central has `spring-boot-starter-parent:4.0.6` before building
- Phase prompt files (`PHASE3_PROMPT.md`–`PHASE5_PROMPT.md`) are AI generation specs, not live docs
