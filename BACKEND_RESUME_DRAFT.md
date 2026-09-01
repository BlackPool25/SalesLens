# Shreyas S Joshi — Backend Systems Engineer (Referral-Ready, 1-Page ATS)
Bangalore, India | shreyasjoshi2511@gmail.com | +91 7892055781 | github.com/BlackPool25 | linkedin.com/in/shreyas-s-joshi | portfolio-sable-pi-47.vercel.app | LeetCode: BlackPool25 (257)

## SUMMARY — Backend / Infra focus (2 lines, no AI filler)
Pre-final B.Tech (AIML, BMSIT — 9.39) + B.Sc CS (BITS Pilani — 8.91), Expected May 2027. Built offline erasure-coded transfer protocol (Rust/RaptorQ) and 8-hr Docker-isolated runtime generator (BUILD & CONQUER 3.0 Winner). Seeking Backend / Systems Engineer referral — Docker, Linux, FastAPI/Spring Boot, PostgreSQL/Redis, protocol design.

## SKILLS — 3-line hierarchy (12-18 tokens, ATS-alias aware)
Languages: Rust, Python, Java, TypeScript, SQL, Dart | Backend/Infra: Docker, Linux, FastAPI, Spring Boot, REST, WebSocket, BullMQ (Job Queue), Redis, PostgreSQL (pgvector/PostGIS), ChromaDB | Systems: RaptorQ fountain code, BLAKE3, FastCDC, zstd, CRC-32C/SHA-256, Docker Compose, GitHub Actions (CI), Observability-ready

## PROJECTS — 2 only (depth > breadth; honest metrics)

### QRStream — Offline Erasure-Coded File Transfer Protocol | github.com/BlackPool25/QRStream — Rust + Dart/Flutter + React | 170 commits, MIT, CI, v1.0.1 (6 assets)
- Designed wire-compatible offline protocol: compress → RaptorQ fountain-encode into K symbols → cycle QR grid (V27/V34/V40, 1×1 to 3×3, 12-30 fps, metadata re-emitted every 32 ticks for late-join) → ZXing-C++ decode → FrameBuffer dedup by ESI → RaptorQ decode (any K of K+repair) → inflate + SHA-256 gate before save; caps 16 MiB / transfer.
- Implemented Rust codec via flutter_rust_bridge 2.12 FFI (71KB Rust + 27KB C++ ZXing), continuous linear tile scaling with nearest-neighbour hard edges (camera-decodable at any scale), per-frame CRC-32C + whole-file SHA-256; measured honest throughput ~46 KB/s (1 MiB random ~23s, 512 KiB ~12s) via Playwright headless Chromium + virtual camera — docs/PERF.md, not promises.
- Shipped 3-OS releases with checksum-verified install.sh (~/.local/share/qrstream), Fedora/RHEL RPM, Android APK (stable keystore via GitHub Secrets fixing INSTALL_FAILED_UPDATE_INCOMPATIBLE), Windows installer (MSVC app-local); 118 Flutter tests + cargo build; docs/ARCHITECTURE, THREAT-MODEL, DEVICE-MATRIX.

### AgentDock — Visual Builder → Docker-Isolated Agent Runtimes — BUILD & CONQUER 3.0 WINNER (1st Place, 8 hrs, 80 teams) — Altreno Club, BMSIT — Team: Aditya Prasad, Varnika Kirani Raghavendra
Tech: TypeScript, Bun, Hono, BullMQ/Redis, FastAPI/Python, ChromaDB/sentence-transformers, Dockerode, React Flow
- Built in 8 hrs from OBS-setup boilerplate pain: visual canvas (React Flow) that compiles natural-language agent descriptions into fully self-contained Docker Compose runtimes (API gateway + Redis + FastAPI workers) — no runtime coupling to builder backend; demo: judges stopped rubrics to ask to run locally vs single-purpose apps.
- Solved container isolation under timer: volume mounts + network route debugging for dynamically spawned agents to share files / communicate without leaking into builder — achieved reliable file-transfer between isolated containers where other teams hardcoded single apps.
- Engineered orchestration stack: Hono gateway + BullMQ job queue (Redis), Python FastAPI agent runtime, RAG memory via ChromaDB + offline sentence-transformers (per-agent Git-backed markdown volumes), LLM Gateway (5 providers: OpenAI/Anthropic/Groq/Gemini/Ollama with failover/rate-limiting).

## OPEN SOURCE — specific PR links (not project links)
- milvus-io/milvus-proto#639 (MERGED 2026-07-24) — feat: UUIDArray proto for type-safe UUID primary keys (Go/Rust vector DB infra) — github.com/milvus-io/milvus-proto/pull/639 ; companion pymilvus#3720 (UUID field SDK) — shows proto→SDK end-to-end (CNCF-adjacent)
- Optional 1-line alt: koala73/worldmonitor PR #7058 (CVE dep bumps via osv-scanner) + #7057 (widget sandbox origin gate) if SIH context relevant

## ACHIEVEMENTS
- 1st Place, BUILD & CONQUER 3.0 (Altreno Club, BMSIT) — 8-hr hackathon, AgentDock Docker-isolated runtime generator
- 2nd Place, CTF Contest (Access Denied Club, BMSIT)
- LeetCode 257 solved (132 Easy / 118 Medium / 7 Hard) — 80.06% acceptance, beats 93.73% runtime — github.com/BlackPool25

## EDUCATION
- B.Tech, Artificial Intelligence and Machine Learning — BMS Institute of Technology and Management, Bangalore — CGPA 9.39 / 10 — Expected May 2027
- B.Sc, Computer Science — BITS Pilani (Online) — CGPA 8.91 / 10 — Expected May 2027

---
VERIFICATION — 3 SCENARIO CONTRACTS (you can check in 60s):
1. Happy (ATS + human 6s): cat resume.pdf | pdftotext - | grep -i "Docker.*Redis.*PostgreSQL" && grep "46 KB/s" && grep "BUILD & CONQUER.*1st"
2. Edge (1-page strict): pdfinfo resume.pdf | grep Pages → 1; grep skill tokens → 18 max, not 59; grep projects → 2, not 8
3. Regression (links): curl -I https://github.com/BlackPool25/QRStream && curl -I https://github.com/milvus-io/milvus-proto/pull/639 → 200; grep "shreyasjoshi2511@gmail.com" intact

ATS NOTES: Single-column, Calibri 10pt, 0.625in margins, text-extractable PDF (Tagged: yes), no tables/columns/images/textboxes, visible URLs (linkedin.com/in/... not hidden hyperlink text), alias-aware keywords (PostgreSQL (pgvector), Job Queue (BullMQ)).
