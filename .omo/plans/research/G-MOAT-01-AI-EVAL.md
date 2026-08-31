# G-MOAT-01 — AI Model Track: Learned Type Inference + LLM Eval Harness + Production AI Handling

**Date:** 2026-08-30 · **Gap:** G-MOAT-01 (Sato/TURL/RECA learned type inference) + founder-flagged LLM handling gap
**Status:** Research complete — 8 mandated SearxNG blocks (3 tech + 5 academic/web) + 5 snowball follow-ups, 80+ primary hits screened
**Plan section:** `oss-mapping-wedge.md` §2 (harder moat) + §5 (LLM strategy) · **Critique:** `oss-mapping-wedge-CRITIQUE.md` §4 + §8
**Search queries executed (8 required, 13 delivered):**

| Block | Query | Tool | Hits screened | Best cited |
|-------|-------|------|---------------|------------|
| A1 | `Sherlock Sato semantic type detection transfer dirty CSV retail wide table ablation 180 columns` | searxng_web_search | 10 | 7 |
| A2 | `TURL RECA related table context inference latency wide table feature extraction cost training 570k tables` | searxng_academic_search | 10 | 6 |
| A3 | `Sato CRF topic model training cost inference latency wide table feature extraction GloVe BERT 500MB` | searxng_academic_search | 10 | 5 |
| B1 | `LLM schema matching evaluation harness accuracy at K HitRate F1 tokens calls cost benchmark Valentine OAEI` | searxng_web_search | 10 | 6 |
| B2 | `LLM evaluation contamination leakage MIMIC Synthea benchmark inflated held out test vs validation split` | searxng_academic_search | 10 | 6 |
| B3 | `LLM prompt management caching fallback latency p99 production handling schema matching retrieval stratification` | searxng_web_search | 10 | 5 |
| C1 | `Ollama qwen3 8B vs GPT-4 hosted LLM production handling self-hosted cold start model loading GPU VRAM` | searxng_web_search | 10 | 6 |
| C2 | `LLM hallucination CHOKE high certainty calibration failure schema matching confidence miscalibration mitigation` | searxng_academic_search | 10 | 6 |
| — | `Dagster model lifecycle asset check retrieval gating Python subprocess cold start` (snowball C) | searxng_web_search | 10 | 4 |
| — | `Sherlock semantic type dirty data abbreviation transfer retail table accuracy degradation` (snowball A) | searxng_academic_search | 10 | 3 |
| — | `Doduo SATO column type annotation inference speed benchmark wide table 180 columns feature extraction time` (snowball A) | searxng_tech_search | 10 | 4 |
| — | `Scalable schema mapping bidirectional stable matching sampling aggregation tokens calls cost` (snowball B) | searxng_academic_search | 10 | 5 |
| — | `TS-Guessing contamination detection 52% MMLU benchmark leakage exact match` (snowball B) | searxng_academic_search | 10 | 4 |

---

## 0. Summary Judgment

| Block | Strongest Finding | Verdict |
|-------|-------------------|---------|
| **A: Learned Type Inference** | Sherlock 686K cols × 1,588 features (L1) and Sato +1.4s/6.4K tables = 0.2ms/table POST-feature is *not* feature-extraction cost; retail dirty transfer (abbrev `NMFMT`/`col_14`, `__EMPTY_3`, 180-col wide) is **unmeasured and likely negative** without retraining; RECA needs a related-table corpus (= no signal on 20 isolated CSVs); TURL 570K-table 80-epoch pretrain + Doduo 512-token ceiling + TabEmb quadratic inter-column edges together prove the moat is a 6-12 week ML research program, not an MVP stub. | **DEFER to post-MVP research track** — MVP ships regex chain (INTEGER→…→CATEGORY[unique<20]→FREE_TEXT) + statistical profiling; Sato/TURL gated on dirty-CRM F1 +2pp @ p99 <500ms held-out benchmark. |
| **B: LLM Eval Harness** | Scalable 2025 shows 70B-instruct + bidirectional stable matching + aggregation + type prefilter beats GPT-4 77.44 vs 62.20 (+15.2pp, L2) but **does NOT replicate at 7-9B off-health**; KG-RAG4SM shows retrieval *helps* uniform (ReMatch −6.5/−31 ablation, L3) vs H2 predicts ≤15 hurts / ≥50 helps, crossover unproven at qwen3:8B; Valentine m:n + HitRate@K + contamination 52-57% TS-Guessing (L2) jointly imply any harness without `tokens×calls` cost axis, cardinality stratification (J/K held-out), and leakage controls is **leakage-inflated theatre**. | **CLOSABLE but only via factorial** — qwen3:8B vs GPT-4 × harness variants × retrieval off/on (3 cardinality strata, J/K held-out, 5 runs, tokens×calls, retail/CRM dirty tabular) before claiming 7-9B>GPT-4 or universal stratification. |
| **C: Production AI Handling** | Ollama Q4_K_M 5.03 GB (BF16 16.4 GB) cold-start 1.2s (FP16 4.8s), p99 210ms (Ollama) vs 89ms (vLLM) per 7B on A10G — at 30s Kafka windows cold-start 2s/window = 96 min/day overhead if not daemonised; **CHOKE 9-43% high-certainty hallucinations robust to temp scaling/top-2-gap/semantic-entropy (L2)** makes SalesLens "LLM>heuristic wins" triage formally unsafe without calibration probe + retrieval gating + blocking fallback. | **CLOSABLE with guardrailed pattern** — model-agnostic harness (`PipesSubprocessClient` + daemon, `blocking AssetCheck`, KV-prefix cache, hedged fallback chain, uncertainty probe, retrieval card.-stratified). |
| **D: Kill Criteria** | Single explicit gate: **ship learned inference only if dirty-CRM hold-out F1 > regex+statistical by ≥2pp AND per-table p99 <500ms on CPU warm AND 180-col wide-table OOM-free**; otherwise deferred; LLM 7-9B locality killed if GPT-4 single-pass ≥ 7-9B+technique at matched harness (Δ≤0) or retrieval interaction p>0.10. | **DEFER by default; promote only on measured gate.** |

**Overall P0 verdict: DEFERRED-MOAT / CLOSABLE-HARNESS — registry `CopyOnWriteArrayList` call sites stay on heuristic-first + LLM-conditional pattern already in SalesLens until all gates green. No new model wired as default.**

> **Evidence levels:** L1 systematic review, L2 peer-reviewed primary (VLDB/SIGMOD/KDD/EMNLP/NAACL), L3 official/docs/maintainer/discourse, L4 reputable engineering blog/benchmark, L5 opinion/preprint-without-peer-review. ⚠️ flag = pre-2022 stale (still cited only where architecture invariant).

---

## A. Learned Type Inference Transfer (Sherlock/Sato/TURL/RECA on dirty retail/CRM wide tables)

### A.1 Sherlock — the feature-engineering ceiling

**What we learned (L2 — KDD'19 peer-reviewed + L3 official site/repo):**

Sherlock is the canonical "features → DNN" baseline that every successor (Sato, Doduo, RECA) explicitly compares against. Architecture and scale must be understood before any transfer claim.

| Dimension | Claim | Source | Level | Limitation / Caveat |
|-----------|-------|--------|-------|---------------------|
| Corpus | 686,765 columns matched to 78 DBpedia types from VizNet corpus (Web tables + visualization-harvested columns) by header-string match | https://sherlock.media.mit.edu/ · https://arxiv.org/abs/1905.10688 | **L2** | VizNet/WebTables only — zero retail/CRM dirty CSVs; header-matching induces label noise on abbreviation-heavy tables |
| Features | 1,588 features per column: statistical properties + character distributions + word embeddings + paragraph vectors (PV) | same + https://sherlock.media.mit.edu/assets/2019-Sherlock-KDD.pdf | **L2** | Feature extraction is the bottleneck, not the DNN; no per-column timing broken out |
| Model | Multi-input deep neural network (4 sub-networks: `char`/`word`/`para`/`stat` → concat → dense → softmax 78-way) | same | **L2** | Fixed 78-way — adding a retail domain type (e.g., `SKU`, `STORE_ID`) requires retraining + header-relabeling |
| Metric | Support-weighted F1 0.89 (macro reported ~0.71 in paper tables) — exceeds ML baselines / dictionary / regex to `0.89` | same | **L2** | VizNet held-out only; no cross-corpus (dirty CSV) evaluation reported |
| Dependencies | Repo requires ~500 MB of data into `data/` dir + GloVe + paragraph vectors + `01-data-preprocessing.ipynb` full pipeline before inference | https://github.com/mitmedialab/sherlock-project | **L3** | Single-host Python (no Dagster integration); 500 MB–2 GB embedding closure must travel with the model |
| Failure mode on dirty headers | SAP system table `T005` country example: column `NMFMT` = standard name, `INTCA` = ISO code, `XPLZS` = zip-code — *none match DBpedia types via header*; Tableau string fallback proves deterministic detectors also collapse without comprehensible header | https://arxiv.org/abs/1905.10688 Fig.2 discussion · https://vis.csail.mit.edu/pubs/sherlock.pdf | **L2** | Exactly the `col_14` / `__EMPTY_3` / `cust_nm` / `rev` retail pattern — transfer failure is baked in |

**Pre-2022 staleness flag:** ⚠️ KDD'19 (2019). Architecture invariant still holds (successors explicitly build on it), but F1 numbers are stale vs Doduo/BERT-era. Use only for feature-count/feature-types/dependency claims, not for SOTA claim.

**Why this blocks a "stub" claim:**

Feature extraction of 1,588 features × N columns × unique-values enumeration is an **ETL job**, not a function call. On a 180-column wide table: 1,588 × 180 = **285,840 feature values** before the DNN sees a single vector. The repo preprocessing notebook materialises character-distribution histograms, embedding lookups per token, and paragraph-vector inference — each with its own cold-start (GloVe load) and GIL cost if done per column loop. No paper reports p99 for 180-col; all latency numbers are on VizNet-average tables (~4-5 cols). Treating `0.89 F1` as portable to `__EMPTY_3` with locale `$1,234.00` vs `1 234,00 €` is a category error — the evaluation never saw that distribution.

---

### A.2 Sato — contextual upgrade with topic model + CRF structured prediction

**What we learned (L2 — VLDB'20 peer-reviewed + L3 GitHub):**

Sato's contribution is deliberately *post-Sherlock*: it fixes Sherlock's two diagnosed gaps — long-tail types needing large samples and **single-column myopia** (Fig.1 example: `Florence/Warsaw/London/Braunschweig` is `city` vs `location` vs `country` only with table context).

| Dimension | Claim | Source | Level | Limitation |
|-----------|-------|--------|-------|------------|
| Architecture | Hybrid: topic-aware single-column predictor (Sherlock DNN + topic subnetworks = table-intent signal) + structured-prediction module (linear-chain CRF over the m columns jointly) — final labels are globally consistent | https://www.vldb.org/pvldb/vol13/p1835-zhang.pdf §3 · https://github.com/megagonlabs/sato README | **L2/L3** | CRF assumes topic coherence across columns — fails when table is `col_14` soup with no topic |
| Topic model | LDA-style topic inference over column values/headers as documents; topic vector concatenated to Sherlock features | same paper Fig.2 | **L2** | Trained on VizNet topical structure; retail abbreviation topics (e.g., `rev`/`amt`/`qty` co-occurrence) not in corpus |
| Dataset | 26K tables for training (multi-column setting), test splits `D_mult` etc. | https://ar5iv.labs.arxiv.org/html/1911.06311 | **L2** | Still WebTables-derived — not dirty CSV |
| Accuracy uplift | Support-weighted F1 0.925 and macro 0.735, exceeding Sherlock by **+3.6pp support-weighted** (Sato abstract claim); per-paper ablations isolate topic vs CRF contributions | https://doi.org/10.14778/3407790.3407793 abstract | **L2** | Δ is on clean WebTables; no dirty-CSV ablation reported |
| Training cost | 81s and 367s for training time (two settings) on presumably single-machine — authors note "we do not need to retrain unless we obtain significant additional training data. Thus … difference is not critical." | https://cagataydemiralp.io/projects/table-understanding/Sato-VLDB20.pdf §5.4 · https://ar5iv.labs.arxiv.org/html/1911.06311 §5.4 | **L2** | Understates transfer retraining: domain shift to retail dirty *does* require retraining or finetune; 81/367s figure is wall-clock on clean data, not cost of curating retail labels |
| Inference latency | **"Sato takes +1.4 s than Base to generate predictions for all 6.4K tables in the test set of D_mult, which is 0.2 ms per table. … average prediction time per table 0.8 ms"** — measured on **64-core / 512 GB RAM** machine with pre-extracted features | same §5.4 | **L2 — critically qualified** | **0.2ms is POST-feature CRF overhead only**; excludes the 1,588-feature extraction + GloVe/PV embedding load (the actual bottleneck); 180-col wide table not measured; GPU vs CPU delta not reported |
| Pretrained artifact | Repo includes pretrained model for replication | https://github.com/megagonlabs/sato | **L3** | Pretrained on VizNet — transfer to dirty retail is zero-shot by default |

**Founder-facing production failure mode (derived from critique §4, corroborated by above):**

Wide retail CSV: 180 columns, 400k rows, mixed encodings, locale `1 234,00 €`, headers `col_14` / `__EMPTY_3` / `amt` / `cust_nm`. Feature extraction iterates 180 cols: for each, build character distributions over 400k distinct-ish values, look up GloVe for token fragments, run PV inference — wall-clock **~tens of minutes** inside the Dagster user-code pod. The 500 MB GloVe + PV model is loaded per subprocess spawn (see §C cold-start). The Dagster pod OOMs at 8 GB limit. Retry repeats identically. Meanwhile SalesLens regex chain (`allMatch` over the chain INTEGER→…→FREE_TEXT) finished in `~12s` on same sample because it is a single-pass per-column regex scan with no embeddings. Because the plan said "not regex chain", there was no fallback wiring — pipeline hard-fails, no error message beyond OOM, 3 weeks of ML debugging masquerading as a "2-day stub."

Second kill: `col_14` / `__EMPTY_3` have no header semantics — topic model produces uniform prior, CRF enforces coherence around a meaningless topic → predicts `FREE_TEXT` for everything, **worse than** `CATEGORY[unique<20]` heuristic that at least catches low-cardinality code columns (e.g., `region` codes 5 distinct values → correctly `CATEGORY`).

---

### A.3 TURL — pre-training 570K tables, TinyBERT Transformer with visibility matrix + MER

**What we learned (L2 — VLDB'21 + L3 repo):**

| Dimension | Claim | Source | Level | Limitation |
|-----------|-------|--------|-------|------------|
| Corpus | 570,171 / 5,036 / 4,964 tables for pretraining/validation/testing; split filtered to entity-column >50% cells linked + subject column present; average table **13 rows, 2 entity columns** | https://www.vldb.org/pvldb/vol14/p307-deng.pdf §4 · https://sigmodrecord.org/publications/sigmodRecord/2203/pdfs/10_turl-deng.pdf | **L2** | Scale is depth, not width — average is tiny (13×2); 180-col wide table is >90× larger in column dimension, out-of-distribution |
| Encoder | Structure-aware Transformer: **N=4 layers, d_model=312, k=12 heads, TinyBERT init**; visibility matrix masks attention to same-row/same-column + caption/header context | same §3 · https://arxiv.org/abs/2006.14806 | **L2** | 312-dim is small but visibility matrix is O((rows×cols)²) attention; 400k rows is impossible — expects sampling, not full materialisation |
| Pretraining objectives | Masked Language Modeling (MLM) on table tokens + **Masked Entity Recovery (MER)** — randomly mask entities, recover from row/column neighbours + caption/header; goal: encode factual knowledge into entity embeddings | same | **L2** | MER assumes linkable entities (Wiki-entity linked cells); retail dirty CSVs have no entity links — objective degrades to MLM on opaque strings |
| Training cost | **80 epochs** on 570K tables — "weeks on multi-GPU" implication (not wall-clock stated; hardware not in excerpts); Adam 1e-4 | https://vldb.org/pvldb/vol14/p307-deng.pdf + https://doi.org/10.14778/3583140.3583149 contrast | **L2 inferred** | No training-wall-clock disclosed in excerpts; inference-level paper — but 80 epochs × 570K tables is unambiguously *not* a stub cost |
| Docker | `xdeng/transformers:latest` PyTorch + Transformers environment | https://github.com/sunlab-osu/TURL | **L3** | Running inside Dagster `PipesSubprocessClient` means either fat image (GBs) or per-run pull — cold-start multiplied |
| Evaluation tasks | 6 tasks: entity linking, column type annotation, relation extraction, table augmentation etc. | same repo | **L2** | Column type annotation is one of six — not the optimisation target alone; SOTA claim is benchmark-aggregate, not retail-transfer |

**Wide-table pathology:** TURL serialises a table into a token sequence for the Transformer. BERT's 512-token limit applies (see Doduo below). A 180-col table at even 8 tokens/col = 1,440 tokens > 512; wide-table handling is either truncation (lose signal) or splitting (lose inter-column attention). The visibility matrix that is the core innovation (row/column-aware attention) becomes the scalability limiter on wide tables.

---

### A.4 RECA — related-table corpus search as signal

**What we learned (L2 — VLDB'23):**

| Dimension | Claim | Source | Level | Limitation |
|-----------|-------|--------|-------|------------|
| Idea | Retrieve **schema-similar + topic-relevant** neighbour tables via Jaccard/topic search; inter-table context enhances column prediction; novel named-entity schema; handles wide tables by design; shorter input token sequences → data/learning efficient | https://vldb.org/pvldb/vol16/p1319-sun.pdf abstract · https://doi.org/10.14778/3583140.3583149 | **L2** | *Requires a corpus of related tables to search over* |
| Results | Support-weighted F1 **0.853 / 0.937** and macro 0.674 / 0.783 on two WebTables datasets, beating SOTA | same abstract | **L2** | WebTables datasets only; retail/CRM dirty not evaluated |
| Data efficiency | With 5% / 10% / 25% / 75% training data, still competitive — suggests sample efficiency is real | same PDF Fig/Table | **L2** | But 5% of WebTables ≠ 5% of retail — domain overlap confounds |
| Dependency | Corpus preprocessing: `compute_jaccard.py` (Jaccard distance between tables) + `pre-process-webtables.py` (table finding/alignment) + `tokenized_data/` — corpus must be aligned and tokenized offline | https://github.com/ysunbp/RECA-paper structure | **L3** | If customer has 20 isolated CSVs with no WebTables-style related corpus, RECA has no neighbour to retrieve — signal is zero by construction |
| Wide-table handling | Explicitly handles wide tables via shorter sequences — advantage over vanilla BERT | same paper | **L2** | Advantage is token efficiency, not type accuracy on abbreviation-heavy data |

**Transfer gap synthesis for RECA:** The plan has no "related table corpus" concept. A 5-person scale-up with 20 heterogeneous SaaS + Sheets + Postgres sources does **not** possess a curated corpus of schema-similar tables to search. Building one (crawl WebTables, align, Jaccard-index) is a data-engineering project of its own. Without it, RECA is a no-op. This alone disqualifies RECA as an MVP moat — it is a research infrastructure dependency, not a model drop-in.

---

### A.5 Successors that bound the ceiling — Doduo & TabEmb

**Doduo (L2 — ICDE'22, `doi 10.1145/3514221.3517906`) — the BERT-era successor that already beats Sato on benchmarks:**

| Dimension | Claim | Source | Level |
|-----------|-------|--------|-------|
| Method | Single BERT model with table serialisation; predicts column types + relations jointly; no 1,588 handcrafted features ("featurisation-free") | https://doi.org/10.1145/3514221.3517906 | **L2** |
| Efficiency knob | **8 tokens per column** already beats previous SOTA — "input data efficient"; max #cols reported 8/16/32 in ablations (512 token ceiling: 512/32≈16 tokens per col) | same PDF Table-rel § | **L2** |
| Improvement | Up to **+4.0% (CTA) and +11.9% (CPA)** over previous SOTA (Sato-class) | https://megagon.ai/publications/annotating-columns-with-pre-trained-language-models/ · https://doi.org/10.48550/arxiv.2104.01785 | **L2** |
| Limitation on wide tables | Explicitly flagged: "ingesting a full wide table may not be feasible … 512 tokens so ingesting a full wide table may not be feasible" + SportsTables paper: "very wide tables … 512 elements limit → table splitting or hierarchical processing might be needed" | same + https://dl.gi.de/bitstreams/5ae1849e-6819-48e9-aaa3-ba6b2da0940f/download | **L2** |
| Toolbox | `megagonlabs/doduo` pip-installable (`AnnotatedDataFrame` with `coltypes`/`colrels`/`colemb`) on Sato's VizNet-derived dataset | https://github.com/megagonlabs/doduo | **L3** |

**TabEmb (L2 — ACL'26, `aclanthology.org/2026.acl-long.757.pdf`):**

- Claim: **+4.9/+5.2/+9.2 micro-F1** over Doduo on CTA/CPA/TTA — but "currently constructs a fully connected column graph; for very wide tables, **quadratic inter-column edges** make graph computation expensive" + "more inference time than BERT-based Doduo" — textbook speed/accuracy tradeoff that re-proves wide-table is the scaling limiter.
- Level L2 (peer-reviewed ACL). Implication: even the 2026 SOTA has not solved wide-table cost.

**Synthesis for transfer:** Doduo's 8-tokens/col knob suggests a path to bound latency (sample few tokens per col) — but on abbreviation-heavy retail CSVs, 8 tokens sampled from `rev / rev_2 / amt / __EMPTY_3` are **noise**, not signal. Authentic mitigation requires domain-retrained embeddings or expanded abbreviation dictionary, i.e., retraining — not a stub.

---

### A.6 Feature extraction pipeline placement inside Dagster (Python UDF vs batch vs subprocess) + dependency closure + latency envelope

**No direct benchmark found for "1,588 features × 180 cols on dirty retail at 30s cadence"** — this itself is a finding (research frontier not production-measured). What exists:

| Claim | Source | Level | Envelope |
|-------|--------|-------|----------|
| Sato POST-feature CRF overhead 0.2 ms/table on 64c/512GB | https://cagataydemiralp.io/projects/table-understanding/Sato-VLDB20.pdf | **L2** | Not feature cost — treat as lower bound |
| Sherlock data closure 500 MB + `01-data-preprocessing.ipynb` pipeline | https://github.com/mitmedialab/sherlock-project | **L3** | Per-subprocess load if not daemonised — cold-start × daemon miss |
| TURL pretraining 570K tables × 80 epochs + TinyBERT 4×312×12 Docker image | https://www.vldb.org/pvldb/vol14/p307-deng.pdf · https://github.com/sunlab-osu/TURL | **L2/L3** | Moat training infra weeks/multi-GPU — solo-dev inoperable |
| Doduo pip `megagonlabs/doduo` + 8 tokens/col sampling knob mitigates wide-table sequence length | https://github.com/megagonlabs/doduo | **L3** | But sampling mitigates length, not dirty vocab |
| TabEmb quadratic inter-column edges → expensive on wide | https://aclanthology.org/2026.acl-long.757.pdf | **L2** | Graph cost O(cols²) = 180² = 32k edges |
| Dagster `PipesSubprocessClient.run` synchronously spawns subprocess per invocation; per-step Dagster process already pays 3-12s overhead with full imports (see §C.5) | https://docs.dagster.io/integrations/external-pipelines/using-dagster-pipes/create-subprocess-asset · https://github.com/dagster-io/dagster/discussions/19666 (H3-1) | **L3** | Add model load on top |

**Latency envelope for decision:**

| Component | Cold-start contribution | Warm contribution | Source |
|-----------|-------------------------|-------------------|--------|
| `import duckdb + light` Dagster step | 5-12s (full vs stripped imports) on local trivial ops | 0 after daemon warm | G-HYBRID-01 H3-1 |
| Sherlock/Doduo embedding load (GloVe/BERT, 500MB-2GB) | +0.8s–several seconds (model load per SalesLens Ollama analogue §C) | 0 if daemon-pinned in VRAM/RAM | §C C1 estimates |
| 1,588-feature extraction × 180 cols × 50k rows (dirty CSV) | **Unmeasured, estimated 45s–45min from critique §4 failure mode** (no citation — explicit gap) | Same — dominated by scan + embedding lookup, not amortizable | Critique §4 production failure mode (scenario) |
| Sato CRF structured prediction | +0.2 ms/table (= negligible) | same | Sato VLDB §5.4 |
| Doduo 8-token/col sampling + BERT forward | ~tens-hundreds ms per table on GPU (not cited for 180-col; TabEmb slower than Doduo) | same | ACL'26 TabEmb comparison |

**Implication:** The only latencies that are *measured and cheap* (Sato CRF 0.2ms, Doduo sampling) are the tail of the pipeline. The head (feature extraction, embedding load, dirty-string normalisation, locale handling) is unmeasured and dominates. Without a dedicated benchmark on the actual dirty corpus, any latency SLO claim is speculation.

---

### A.7 Dirty retail/CRM wide-table transfer — explicit ablations absent

**What was searched and NOT found:**

- No retrieved paper benchmarks Sherlock/Sato/TURL/RECA on a retail/CRM dirty CSV corpus with abbreviations (`rev`/`amt`/`cust_nm`), compound tokens, locale formats (`$1,234.00` vs `1 234,00 €`), unnamed `col_14` / `__EMPTY_3`, high-card null GROUP BYs, 180-column width. This negative result is itself evidence: **transfer is unstudied**.
- No ablation isolating header-less or abbreviation-heavy columns vs clean WebTables headers.
- No locale-format robustness study for these models.

**Closest proxies:**

- SAP `NMFMT`/`INTCA`/`XPLZS` (§A.1) as existence proof that real enterprise schemas use coded abbreviations invisible to DBpedia-trained models — L2.
- SportsTables corpus paper explicitly calls out "supporting wide tables: existing datasets consist of tables with [few cols]" as open problem — L2 — confirming standard benchmarks are narrow.

---

### A.8 Fallback contract + cost model

| Design choice | Recommendation | Rationale | Cost |
|---------------|---------------|-----------|------|
| MVP default | **Regex chain** `INTEGER→DECIMAL→BOOLEAN→DATE→DATETIME→EMAIL→PHONE→CURRENCY_AMOUNT→CATEGORY[unique<20]→FREE_TEXT` via `allMatch` on sample — already in SalesLens `ProfilingService` | Ships, 12s on 400k rows, deterministic, no deps, no OOM | $0, zero infra |
| Statistical fallback | Add profiling-driven fallback before FREE_TEXT: null-rate + distinct-count + top-K + entropy heuristics (already partially in quality checkers) | Catches low-cardinality codes that Sato would miss when header is `__EMPTY_3` | Same as profiling cost |
| Learned path (research track) | Gate on **held-out dirty-CRM benchmark** (curated retail/CRM CSVs, wide + abbrev + locale, 20+ tables, m:n ground truth for mapping-coupled eval). Promotion only if **Learned F1 > Regex+Statistical by ≥2pp** at **p99 <500ms per table on CPU warm** + **180-col OOM-free** + no more than 500MB closure. | Formalises critique §4 Severity P0→P2 via measurable gate | Benchmark curation 1-2 wks + training/finetune 2-4 wks; not on MVP critical path |
| Hosting | Don't host embeddings in Dagster user-code server hot path; if research-track promoted, serve via **standing daemon** (see §C Ollama topology analogue: shared S3-backed FS + HPA, not per-request spawn) — otherwise cold-start dominates 30s windows | §C C1 shows per-request spawn is fatal at 30s cadence | GPU VRAM pinning cost only if promoted |

---

## B. LLM Eval Harness (Valentine m:n, contamination 52% TS-Guessing, cardinality stratification, tokens×calls)

### B.1 Valentine / OAEI harness — what the harness actually measures

**Primary sources (L1/L3 — peer-reviewed + framework repo):**

| Claim | Source | Level | Key Detail | Limitation |
|-------|--------|-------|------------|------------|
| Valentine is the L1 systematic review-backed evaluation framework for schema matching / dataset discovery (ICDE'21 + VLDB'14 lineage) — classifies into 4 relatedness scenarios (unionable/joinable/view-unionable/semantically-joinable) | http://disi.unitn.it/~pavel/OM/articles/Koutras_ICDE21.pdf · https://www.vldb.org/pvldb/vol14/p2871-koutras.pdf · https://github.com/delftdata/valentine | **L1 (paper) / L3 (repo)** | Evaluated 7 classical matchers — **no LLM harness** in original Valentine; LLM papers retrofit onto it |
| Metrics module: Precision, Recall, F1 (harmonic mean over ground-truth valid matches) — standard for threshold-based matchers | https://github.com/delftdata/valentine/tree/d04f24a6cdddb349722e9d79ab58cb349abe3727 | **L3** | Assumes **1:1** or binary m:n via threshold — not ranked-list HitRate |
| Retrieval-enhanced (ReMatch) reformulates schema matching **as a retrieval problem** — appropriate metric is **Accuracy@K (Eq.1)**; notes "unlike F1, standard Accuracy@K is not well defined for m:n — we limited ourselves to 1:1 or m:1" and "for 1:1 and m:1, Accuracy@1 equivalent to F1 when using argmax not threshold" | https://arxiv.org/html/2403.01567v2 §4 | **L3 (preprint, but definitionally precise)** | **Explicitly punts m:n** — wedge canonical is 20-100 fields with explicit m:n + FK≡PK (oss-mapping-wedge.md §5/Exp.3-4) so ReMatch metric not sufficient |
| Schemora enriches schema metadata + vector+lexical retrieval, establishes **new SOTA on MIMIC-OMOP** with +7.49% HitRate@5 / +3.75% HitRate@3 over previous best; first LLM schema matching with open-source implementation | http://arxiv.org/abs/2507.14376 · https://arxiv.org/html/2507.10897 (LLMATCH sibling) | **L3 (arxiv 2025-07-18)** | MIMIC-OMOP is health only; HitRate@K ranked-list assumption requires O(target) calls (see LLMATCH Def 3.1 cost) |
| OAEI-LLM extends OAEI schema-matching datasets to understand LLM hallucinations — 7 datasets extended + hallucination taxonomy (different error types counted) + 10 LLMs benchmarked → LLM leaderboard for OM | https://ceur-ws.org/Vol-3953/361.pdf · https://arxiv.org/html/2503.21813v3 | **L3 (CEUR/arxiv)** | Benchmarks OM hallucination class, not m:n dirty tabular with costs |
| TaDA 2024 experimental study: compares **1:1 vs 1:N vs N:1 vs N:M task scopes**; finds **1-to-N dominates 1-to-1** (outperforms `sim` on 5/9 datasets; 1-to-N maximal F1 on DiCO/PrDE/SeVD); GPT-4 vs GPT-3.5 scope comparison; uses F1 + **decisiveness-score** (fraction non-unknown votes) with 5 repeats to handle hallucinations | https://tabular-data-analysis.github.io/tada2024/papers/TaDA.8.pdf · https://arxiv.org/html/2407.11852 §4 | **L2 (peer-reviewed workshop + arxiv)** | 9 datasets; not cardinality-stratified; decisiveness-score is unique to this harness — not standard |

**Synthesis for wedge harness:**

- No single harness covers **m:n + FK≡PK + HitRate@K + F1 + cost** jointly. Valentine = classical precision/recall (1:1 bias). ReMatch = Accuracy@K but 1:1/m:1 only. OAEI-LLM = hallucination taxonomy. TaDA = scope comparison + decisiveness.
- Wedge must ship a **composite harness** that reports **all three**: F1 (thresholded), HitRate@1/HitRate@3 (ranked), plus decisiveness/coverage (what fraction did the LLM actually answer vs `unknown`). Papers that report only one are incomparable.
- **FK≡PK equivalence** (LLMATCH §6: "treat FK matches as equal to PK") must be normative — otherwise `canonical.orders.customer_id` FK target change (§G-SCHEMA-01) creates false negatives in the benchmark itself.

---

### B.2 Cardinality stratification — does retrieval help or hurt?

**This is H2's load-bearing claim:** retrieval HURTS on ≤15 target fields (Δ≤−3pp) while HELPING on ≥50 (Δ≥+5pp), crossover ~15-30. Finding 2 marks this **55-68% directionally true even if magnitude fails**. What does fresh search add?

| Source | Claim | Level | Direction | Evidence | Limitation |
|--------|-------|-------|-----------|----------|------------|
| **KG-RAG4SM (arXiv 2501.08686)** | GPT-4o-mini + vector/graph-traversal/query KG-RAG **beats Jellyfish-8B by 35.89% precision / 30.50% F1 on MIMIC** and **69.20% precision / 21.97% F1 on small-target Synthea (8-table)** — where H2 *predicts retrieval hurts* (+context-poisoning admission: "irrelevant knowledge ... performance will be decreased, ranking/pruning required") | **L3 preprint** | **Contradicts H2 small-target hurts** — retrieval helped even at small target | Conflates scale+KG quality; KG quality not ablated; health domain lexical overlap (OMOP CDM) may inflate small-model gains |
| **ReMatch ablation (arXiv 2403.01567, Table ablations)** | `no-retrieval −6.5% accuracy@1`, `no-description −31% accuracy@1` — **uniform help, no small-target win reported** | **L3** | Contradicts H2 stratification — help is uniform | Not stratified by cardinality — ablation is global |
| **LLMATCH SchemaNet (arXiv 2507.10897)** | Prior benchmarks avg 15 cols single table vs **SchemaNet 135 cols / 14 tables (9×)**; Def 3.1 **complete ranked lists cost O(target) calls** — unproven at 135 cols; "overly small contexts fragment the task and degrade performance" — tasks exceeding single-prompt limit split into smaller prompts | **L3** | Supports stratification concern — small context hurts, large needs splitting | Not a retrieval stratification paper per se; measures context capacity |
| **Schemora (arXiv 2507.14376)** | Metadata enrichment + retrieval improves HitRate@5/3 to SOTA on MIMIC-OMOP — proves retrieval *can* help at scale, but not that it *hurts* at small | **L3** | Consistent with retrieval-helps-at-scale half of H2 | No ≤15-field small-target ablation |
| **TaDA (2024, Table 7)** | GPT-4 > GPT-3.5 **across all scopes** (1:1,1:N,N:1,N:M); combined 1-to-N+N-to-1 recommended — scale is additive, not technique-substitutable | **L2 (workshop peer-review)** | Contradicts "technique > scale" — scale helps everywhere | Not cardinality-stratified either |
| **Beyond Scale (arXiv 2607.24688, 1,215-run factorial)** | "Larger models do not consistently win; scale amplifies shortcut learning; **data/technique ≥ scale**" + practitioner "8B is your new baseline" | **L2 (arxiv, large-N)** | Supports technique≥scale directionally, but via 1,215 runs shows **non-monotonic** — not a clean "7-9B beats GPT-4" | Not schema-matching specific — general benchmark; preprint |

**Finding 2 context (for grounding):**

- Supporting H2 (would confirm): Scalable 2025 **77.44±0.69 (Llama-3.1-70B-GPTQ-INT4 + bidirectional stable matching + sampling/aggregation + prefilter) vs 62.20±2.40 (GPT-4 Matchmaker) = +15.2pp** — but this is **70B, not 7-9B** (confidence 70-80% that 70B+technique>GPT-4, but 45-60% that 7-9B claim is false) — L2.
- Contradicting H2 (would falsify at 7-9B): KG-RAG4SM GPT-4o-mini+KG beats Jellyfish-8B 35.89%/69.20% (see table), ReMatch −6.5%/−31% uniform help, TaDA GPT-4>3.5 all scopes — L3/L2.

**Harness implication:**

Stratification must be **measured, not assumed**. Required experiment (oss-mapping-wedge.md Exp.3): 3 cardinality strata (≤15 / 16-49 / ≥50 target fields) × retrieval off/on with **J/K tuned on held-out** (not test). Interaction p<0.10 needed to claim stratification. Without J/K held-out tuning, retrieval parameter choice leaks into the measurement (contamination-adjacent — see §B.3).

---

### B.3 Contamination & leakage — 52% TS-Guessing and the health-domain inflation problem

**Core result (L2 — NAACL'24 peer-reviewed):**

| Claim | Source | Level | Detail | Implication for wedge |
|-------|--------|-------|--------|----------------------|
| **TS-Guessing (Test-Set Guessing):** mask a wrong answer in MC and prompt model to fill the gap; or obscure unlikely word and ask to produce it. **ChatGPT 52% exact-match, GPT-4 57% exact-match** on MMLU test-set missing options — without seeing the full question | https://aclanthology.org/2024.naacl-long.482 · https://arxiv.org/html/2311.09783v2 · https://doi.org/10.48550/arxiv.2311.09783 | **L2 (NAACL'24)** | Model has memorised the benchmark distribution well enough to reconstruct held-out options — benchmark score is inflated |
| After **fully contaminating ChatGPT with MMLU**, EM nearly 100% | same, Table results | **L2** | Upper bound: contamination can drive near-perfect "generalisation" |
| TruthfulQA: commercial LLMs improve when given metadata in test set; Question-Multichoice 57% EM guessing missing option — shows format leakage | same | **L2** | Any benchmark where question+choices co-occur on the web is vulnerable |
| **ConTAM (Contamination Analysis Method):** large-scale survey 13 benchmarks × 7 models × 2 families, n-gram contamination metrics — finds **contamination may have much larger effect than reported in recent LLM releases**, differences between models/families | https://arxiv.org/html/2411.03923 | **L2 (arxiv, large-N)** | Reported GPT-4 gains over open models may be partially contamination, not capability |
| **GEM 2026 systematic review (55 studies):** "contamination underestimates, health overlap inflates" — health benchmarks overlap more with web corpora (MIMIC/Synthea-adjacent documentation is abundant online) | cited in findings.md §Finding 2 ( https://aclanthology.org/2026.gem-main.50.pdf ) | **L2** | Directly challenges MIMIC/Synthea as held-out for health-domain schema matching |
| **Rethinking Benchmark & Contamination with Rephrased Samples (arXiv 2311.04850):** rephrasing-based decontamination applied to RedPajama-1T / StarCoder shows **8-18% HumanEval overlap** in pretraining sets; **synthetic data generated by GPT-3.5/4 also contaminated** — pipeline contamination | https://doi.org/10.48550/arxiv.2311.04850 | **L2** | Even "synthetic" held-out may carry contamination |
| **Inference-Time Decontamination (ITD, Findings-EMNLP'24):** detect+rewrite leaked samples without altering difficulty — **reduces inflated accuracy by 22.9% on GSM8K, 19.0% on MMLU**; Phi-3 −6.7%, Mistral −3.6% on MMLU after ITD | https://aclanthology.org/2024.findings-emnlp.532.pdf | **L2** | Quantifies inflation magnitude — 20%+ phantom accuracy |
| **Reliability Gap (arXiv 2606.03305):** OLMo 2 post-training **explicitly includes GSM8K train split**, task: can detectors flag train vs test/validation? Shows **detection mechanisms can fail** to distinguish | https://doi.org/10.48550/arxiv.2606.03305 | **L2** | Even with ground truth, contamination detection is unreliable — held-out claims need procedural guarantees, not just metric thresholds |
| Detection via **test vs validation split** gap: expect comparable performance; contaminated model shows **higher accuracy on public test (more likely in training) vs private validation**; but Dodge et al. 2021 shows SQuAD/MultiNLI/SuperGLUE all vulnerable to web-scraped corpora orders of magnitude larger | https://mbrenndoerfer.com/writing/benchmark-contamination-llm-detection-mitigation · https://aclanthology.org/2023.findings-emnlp.722.pdf | **L3/L2** | Demonstrates that **health public splits are guaranteed to be in the scrape**; private hold-out must be truly private (not just "test split") |

**Founder-flagged eval rigor gap (direct quotes and implications):**

- Founder note says prior consultant delivered no `tokens×calls` cost axis, no cardinality stratification (already covered), and **no leakage controls**. Search confirms leakage controls are the hardest to get right — TS-Guessing 52%/57% is not a toy number; it's on MMLU, the most audited benchmark. MIMIC/Synthea are **worse** — medical schemas/docs/OMOP CDM are heavily web-crawled.
- **Schemora / LLMATCH / KG-RAG4SM** all evaluate on **MIMIC-OMOP** (health). Without a non-health dirty tabular replication (retail/CRM) + **J/K held-out tuning + 5-run variance** (oss-mapping-wedge.md Exp.3), the reported HitRate@K is **leakage-inflated by unknown magnitude** (ConTAM: "much larger effect than reported").
- **Valid harness pattern from search:** `J/K tuned on held-out` (Scalable § retrieval J/K), not on test; 5 repeats with CI non-overlapping; **non-health replication** (at least one retail/CRM dirty tabular); **rephrased/re-sampled hold-out** (ITD-style) or truly private corpus not on the web.

**Pre-2022 staleness warning for this block:** Dodge et al. 2021 discussion is ⚠️ pre-2022 but cited only for structural argument (web corpus size >> benchmark size), not for any model-specific score. TS-Guessing/ConTAM/GEM are 2023-2026 — current.

---

### B.4 Tokens × calls cost harness — the missing economic axis

**What we learned (L2/L3):**

| Claim | Source | Level | Cost Model |
|-------|--------|-------|------------|
| **Scalable core issues:** (1) inconsistent outputs (phrasing/structure sensitivity) → **sampling+aggregation**, (2) GLaV expressiveness strains context windows, (3) **computational cost of repeated LLM calls** → **data type prefiltering (N→k pool reduction, >50% token drop with 2 rules/prompt)** | https://arxiv.org/html/2505.24716v1 §1 · https://www.alphaxiv.org/abs/2505.24716 | **L2** | Explicitly frames cost as tokens + calls, not just accuracy |
| **Def 3.1 Stable Schema Matching** — formal problem: two schemas `[attr1]={a1..am}`, `[attr2]={b1..bn}`, ranked preference lists each direction, Gale-Shapley-style mutually-preferable matching; O(target) ranked lists = **O(n) LLM calls** if naive | https://research.engr.oregonstate.edu/idea/sites/research.engr.oregonstate.edu.idea/files/scalable_schema_mapping-technical_report.pdf Def 3.1 | **L2** | Cost = calls, each call = prompt+completion tokens |
| **LLMATCH (2507.10897) cost implication:** "Tasks exceeding single-prompt limit are split into multiple smaller prompts, ensuring largest source/target fit together. For consistency we approximate context size by word count. Overly small contexts fragment and degrade. ***Complete ranked lists cost O(target) calls, unproven at 135 cols***" | https://arxiv.org/html/2507.10897v1 §4 + Fig.8 | **L3** | 135-col target (SchemaNet) × ranked lists = 135 calls minimum — without prefilter, cost explodes; **N→k prefilter cuts pool >50%** (Scalable) |
| **Schemora scaling:** retrieval (vector+lexical) improves accuracy but **adds retrieval tokens + embedding calls** — HitRate@5 SOTA not free | http://arxiv.org/abs/2507.14376 | **L3** | Must be reported alongside accuracy; otherwise retrieval looks Pareto-dominant spuriously |
| **Prompt-Matcher CSP (Correspondence Selection Problem):** formally define **revenue maximisation under GPT-4 budget**; prove **NP-hard; (1−1/e)-approximation** via greedy + partial enumeration; budget per round = `budget/k`, k rounds user-chosen; needs `budget > cost(all correspondences)` and `> 3×mean cost` otherwise greedy degenerates | https://arxiv.org/html/2408.14507v1 §3-4 | **L2** | First rigorous budget-aware harness: exactly the `tokens×calls×cost` reporting missing from founder gap |
| **Scalable aggregation methods trade cost vs quality:** `union` (recall+, precision−), `majority vote` (best balance, used in paper), `intersection` (precision+, recall−); sampling = **multiple candidate sets from transformed prompts** (phrasing/layout perturbation) → **calls multiply by #perturbations** | https://www.alphaxiv.org/abs/2505.24716 · https://pdxscholar.library.pdx.edu/cgi/viewcontent.cgi?article=1392&context=compsci_fac | **L2** | More perturbations = higher recall but linear call cost; must be in cost report |
| **Bidirectional merging:** averaging vs multiplication vs stable matching algorithm — stable matching seeks **mutually acceptable** preference, not just high one-direction score; cheap to compute post-call | same | **L2** | Compute cost negligible vs LLM calls — Pareto win if it raises F1 without extra calls |

**Harness reporting template (actionable — Exp.3 gate):**

For every (model × harness variant × retrieval on/off × cardinality stratum) cell, report:

```
Accuracy@1 / HitRate@1 / HitRate@3 / F1  (mean ± SD over 5 runs, CI)
+ decisiveness/coverage (fraction non-unknown, per TaDA)
+ tokens_in × tokens_out (mean per mapping)  — via API usage or tokenizer
+ calls_per_mapping (argmax vs ranked-list vs bidirectional vs retrieval rounds)
+ cost_per_mapping ($ for hosted / amortised $/GPU-hr for self-hosted)
+ latency p50/p95/p99 (end-to-end including retrieval + type prefilter)
```

Without `tokens_in × calls` the "qwen3:8B is 10× cheaper" claim (oss-mapping-wedge.md §5) is unverifiable. Prompt-Matcher CSP is the cited formalism that makes "cheaper" precise: budget-constrained F1 frontier, not point accuracy.

**The 10× cheaper measurement (founder-flagged):** plan §5 says "report tokens×calls per mapping for 10× cheaper measurement". Search shows the way to measure it already exists (Prompt-Matcher budget + Scalable N→k prefilter >50% drop + LLMATCH O(target) calls). Harness must enforce it.

---

### B.5 Eval contamination → overclaimed 7-9B>GPT-4

Put §B.2 and §B.3 together: the single paper that *does* show technique>scale (Scalable 2025, +15.2pp) is at **70B-GPTQ-INT4**, not 7-9B. Every 7-9B comparison in search either favours GPT-4-family (KG-RAG4SM GPT-4o-mini+KG beats Jellyfish-8B 35.89/69.20; TaDA GPT-4>3.5 all scopes) or is missing. The 70B win is real (L2) but **does not license extrapolation to qwen3:8B**. The only way to license it is the Exp.3 factorial — which contamination §B.3 shows must be on a **private or ITD-rephrased** dirty corpus, otherwise the 52-57% leakage base rate contaminates the measurement itself.

This is why Finding 2 verdict is **Contested 45-60%** — not because data says 7-9B fails, but because *no clean 7-9B factorial exists* that would settle it either way. The research debt is US$2-3 weeks of Exp.3, not an opinion.

---

## C. Production AI Handling (Ollama cold-start, prompt management, CHOKE hallucination, retrieval gating, Dagster model lifecycle)

### C.1 Self-hosted vs hosted cost & cold-start (Ollama qwen3:8B vs GPT-4)

**What we learned (L4 reputable engineering — but convergent, with quantitative specifics):**

| Claim | Source | Level | Detail | Limitation |
|-------|--------|-------|--------|------------|
| **Ollama production topology for 8B+ models:** stateless OpenAI-compatible endpoint behind reverse proxy, multiple Pods pulling models from **shared S3-backed FS via s3fs-fuse**, each Pod single GPU + single Ollama instance, **HPA scaling on GPU memory**, model pre-warmed via **readiness probe calling `/api/generate` with short prompt** — achieves **~85% GPU util at 200 concurrent requests <300ms p95 for Llama-3.1-8B** | https://markaicode.com/architecture/ollama-system-design-architecture-956/ | **L4 (architecture post)** | Not a benchmark paper — but the S3+s3fs+fuse + readiness-probe pattern is the cited fix for the weight-loading cold-start below |
| **Qwen3-8B VRAM/storage:** **Q4_K_M GGUF 5.03 GB on disk**, BF16 16.4 GB; **RTX 4090 24 GB gives enormous KV-cache headroom** — can drive full **131K YaRN-extended context** without offload; Q3_K_M ~14 GB, FP16 needs **64 GB+ system RAM** during load (host holds full model before GPU transfer, KV spill to RAM) | https://smeltcore.com/recipes/qwen3-8b-on-rtx-4090-q4-k-m-gguf-via-ollama-or-llama-cpp/ · https://www.sitepoint.com/qwen3-8-27b-local-gpu-setup-ollama/ · https://llmhardware.io/guides/qwen3-hardware-requirements | **L4 (hardware guide + recipe)** | Consumer 4090; datacenter A100 40/80 GB is ref for multi-user concurrency (see vLLM vs Ollama) |
| **Cold-start quantitative (per 7B on A10G, johal.in 6-month retrospective — most authoritative in this block):**<br>**Ollama 0.3 Q4_K_M: cold 1.2s, p99 210ms (128 tok), 4.2 GB VRAM, 18 req/s/A10G, $0.004/1M tok ($0.12/hr A10G)**<br>**vLLM 0.4 FP16: cold 4.8s, p99 89ms, 14.8 GB VRAM, 47 req/s, $0.002/1M**<br>**vLLM 0.4 Q4_K_M: cold 2.1s, p99 112ms, 5.1 GB VRAM, 39 req/s, $0.003/1M** | https://johal.in/retrospective-building-self-hosted-llm-ollama-03-vllm-04 | **L4 (with table)** | Single 6-month report — but numbers are internally consistent with phase-by-phase bench below |
| **5-phase cold-start decomposition (model-cold-start-bench, RTX 2070 microbench extrapolated to 7B+):** Phase1 disk→CPU RAM, Phase2 CPU→VRAM PCIe, Phase3 CUDA init, Phase4 KV allocation, Phase5 warmup/JIT. For **GPT-2 Large 774M: Phase1 815ms (61%), Phase2 487ms (36%)** — **PCIe transfer fastest-growing with size**; at 7B+ becomes major fraction; 70B FP16 140 GB needs **≥2× H100 + tensor parallel** before first token can be served | https://github.laiyagushi.com/JohnScheuer/model-cold-start-bench · https://dreaming.press/posts/2026-06-27-scale-to-zero-llm-inference-gpu-cold-starts.html | **L4/L4** | RTX 2070 microbench not A10G/H100; ratio applies, absolute scales with model size |
| **vLLM vs Ollama serving (2026-08):** vLLM **~3s vs Ollama ~9s** cold-start in one 2026 test; **A100 40/80 GB is reference for sustained concurrency**; RTX 5090 32GB GDDR7 single-user/light OK; multi-user needs datacenter GPU or vLLM distributed | https://dev.to/apeder/vllm-vs-ollama-production-serving-2026-37kf | **L4** | Single test; recency is strength (2026-08) |
| **Hidden cost of cold starts (DigitalOcean) + Cloud Run AI cold starts (Google):** weight loading GPU memory vs storage>host RAM>PCIe>VRAM pipeline; if weights exceed VRAM swaps to slower RAM — phase 4 stalls; always-on vs serverless cold-start tradeoff | https://www.digitalocean.com/community/tutorials/hidden-cost-cold-starts-serverless-ai-workloads · https://dev.to/googleai/a-guide-to-ai-cold-starts-on-cloud-run-c6d | **L4/L4** | Generic — but model-size→VRAM-fit rule is universal |

**Founder-facing decision: Ollama vs hosted (GPT-4) for schema matching at SalesLens scale**

SalesLens already runs **Ollama qwen3.5:9b on host via `host.docker.internal:11434`** (AGENTS.md notes: "Ollama `qwen3.5:9b` runs on host machine, not in Docker"). At 30s Kafka windows × 2,880 windows/day:

| Deployment | Cold-start per window if spawned per-window | Amortized if daemon with readiness probe | p99 per 128-tok completion | VRAM | $/1M tokens (A10G equiv) |
|------------|---------------------------------------------|-----------------------------------------|----------------------------|------|--------------------------|
| Ollama Q4_K_M qwen3:8B | 1.2s (johal.in) to 9s (2026 test) — **dominates 30s window** | ~0 (standing Pod, 85% util, <300ms p95, HPA on GPU mem) | 210ms (Ollama) vs 89ms (vLLM FP16) | 4.2-5.03 GB | $0.004 (Ollama) / $0.002 (vLLM FP16) |
| Hosted GPT-4 (OpenAI API) | 0 (no local weights) — network + routing p95/p99 | same | ~500ms-2s API-dependent (not in search; inferred generic, do not cite) | 0 local | ~$10-30/1M input+output (market, not in search — do not cite as sourced) |

**Implication:** The "10× cheaper" claim (oss-mapping-wedge.md §6: `tokens×calls` per mapping) is **plausible but only after daemonisation**. Per-window spawn at 1.2-9s each = **96 minutes of cold-start per day** at 30s cadence — erases any scan saving from hybrid plane (§8 critique). Required pattern: **standing Ollama Pod(s) behind reverse proxy + S3 s3fs model pull + readiness-probe warmup** (Markaicode topology) — then Ollama Q4 is cost-competitive; otherwise hosted GPT-4 wins on tail latency despite higher per-token price.

**Model lifecycle note (Startup profile context):** `OllamaConfig` uses `@Profile("!test")` — new features using Ollama should follow same pattern (§ANCHOR ANTI-PATTERNS). Means mock/test slice must not pull the 5GB closure — Dagster test harness (§C.5) must mock the Ollama client, not the model.

---

### C.2 Prompt management — caching, fallback, latency p99

**What we learned (L3/L4 — production engineering playbooks):**

| Claim | Source | Level | Detail | Limitation |
|-------|--------|-------|--------|------------|
| **LLM Latency Engineering: TTFT, Caching, Routing road to real-time agents** — breakpoint rules: eligible prefix must meet model-specific minimum; **changing tools/schemas/images/content before breakpoint invalidates reuse**; must monitor `cached_tokens` + `cache_write_tokens` together; **stable cache key** helps related requests reach matching cache state; high traffic on one key may **reduce** hit rate; **every route needs fallback, timeout, retry, promotion record** | https://contextosai.com/blog/llm-latency-engineering-ttft-caching-routing | **L3/L4 (2026-08-14)** | Guidance — not a benchmark |
| **Cache-hit strategy unified framework:** KV/prefix vs prompt vs semantic caches — decision framework + per-layer hit-rate targets; **exact-match (full response) simple, zero false-hit risk, but single-char miss** → effective for formalized requests (fixed templates, batch jobs) | https://devfloor9.github.io/engineering-playbook/en/docs/agentic-ai-platform/model-serving/inference-optimization/cache-hit-strategy | **L3** | Framework — not schema-matching specific |
| **Resilient LLM-DBMS Pipelines via Event-Driven Fallback Orchestration (IEEE CASCON 2025):** fault-injection prototype evaluates availability/latency/cost/cache-hit/safety under provider outages/quota/latency jitter/schema drift/traffic surges — **vs naive: p95 1403.4→1156.8ms (−18%), p99 1632.5→1335.9ms (−18%), cost halved, availability 96.5%→~100%, blocks 13 unsafe DDL/DML**; tradeoff **−17% quality proxy during fallbacks** | https://doi.org/10.1109/cascon66301.2025.00113 | **L2 (IEEE, fault-injection)** | Quality drop during fallback is real — fallback ≠ free |
| **Authorization-aware cache layers:** distinguish **prompt-prefix cache (stable prefix)**, **embedding cache (per text + model version)**, **full-response cache (per authorized request)**, **semantic cache (lossy, needs threshold + false-hit budget)** — key/partition by every input that can change output or permission (principal/tenant/policy version/data classification/model/tool-schema/temperature/max_tokens) | https://github.com/sirmarkz/staff-engineer-mode/blob/main/specialists/llm-serving-cost-and-latency.md | **L4** | Checklist — not benchmark |
| **GonkaRouter + ContextOS routing:** `CHEAPEST_FIRST` vs `FASTEST_FIRST` (EMA latency) vs **`FALLBACK_CHAIN` (try in order, fall on error/timeout)**; **exact-match caching for deterministic tasks / stable extraction prompts**; **semantic caching requires strict tenant boundaries + similarity thresholds + invalidation**; **caching user-specific/time-sensitive outputs without policy creates privacy risk** | https://gonkarouter.io/blog/how-to-route-requests-between-llms-smoothly · https://github.com/svalench/llm-cache-router | **L4/L3** | Routing taxonomy — applies directly to qwen3:8B → GPT-4 fallback chain |
| **OpenAI prompt-caching docs:** first request writes eligible prefix, later reuses if reaches same machine holding entry not expired; **traffic >15 req/min can overflow routing** — `cached_tokens` signal is per-machine, not global | https://developers.openai.com/api/docs/guides/prompt-caching?prompt-cache-api=responses | **L3 (docs)** | Hosted-specific — but prefix-stability principle transfers to self-hosted |
| **Production prompt as compiled context bundle:** policies + templates + user intent + session history + retrieved evidence + tool schemas + tool outputs → determines **KV cache blocks, retrieval payloads, tool artifacts** and how they scale + **tail-latency tiering** | https://www.solidigm.com/products/technology/anatomy-of-prompt-structure-for-llm-kv-cache.html | **L4** | Architecture — proves why stable template prefix matters for hit rate |

**Applied to schema matching (founder gap — prompt management & fallback):**

| Layer | Pattern | SalesLens analogue |
|-------|---------|-------------------|
| **Prefix cache** | Stable Jinja template prefix (system prompt + canonical schema + type-prefilter rules) pinned before per-source column payload; breakpoint after prefix so `cached_tokens` hit high | Mapping YAML → transform SQL prompts share same canonical prefix; per-source `source_column` block is suffix |
| **Embedding cache** | Deterministic per (text, model version) — cache vector embeddings for retrieval candidate ranking; avoid re-encoding same target field descriptions across 20 sources | RECA-style Jaccard/topic search vs TURL entity embeddings — embedding cache hit amortises retrieval cost |
| **Full-response cache** | Exact prompt → response, TTL per domain (schema cache longer, news shorter); tenant-partitioned | Identical source schema re-ingest (deterministic replay case) — cached mapping reused without LLM call at all |
| **Semantic cache (lossy)** | Similarity threshold + tenant boundaries + invalidation; **requires false-hit budget** (see CHOKE §C.3) — do not use for high-stakes canonical fields without probe | Do not cache `revenue` / `customer_id` mappings semantically — cache only low-stakes formatting fields |
| **Fallback chain** | `FALLBACK_CHAIN`: **qwen3:8B (primary, cheap) → GPT-4 (fallback on error/timeout/low-confidence) → heuristic token-overlap 0.70 (last resort, deterministic)**; hedged requests if p99 budget exceeded; **enforce timeout + retry + promotion record** per ContextOS | SalesLens current "heuristic-first then LLM only if LLM>heuristic" is **inverted** vs resilient pattern — should be primary→fallback with confidence gate, not heuristic-then-maybe-LLM |
| **Circuit breaker** | LLM resilience via circuit breaker + fallback strategies; **validate outputs via schema validation + safe defaults** — reduces harm from partial/incorrect | Mapping JSON schema validation (G-YAML-01) + `state: pending` not `confirmed` until human review |

**p99 implication (quantified where available):**

- CASCON fault-injection: resilient pipeline cuts **p95 −18% (1403→1157ms), p99 −18% (1633→1336ms)** vs naive — proves fallback+cache is not just availability but **tail-latency win**.
- Prompt caching: high hit rate **reduces TTFT** (time-to-first-token) linearly with cached prefix length — requires stable prefix design (not in naive per-source freeform prompt).
- SalesLens scale (30s windows): any LLM call that exceeds **~5s** risks window lag; hedged request at p99 budget (e.g., 2s) → fallback to heuristic keeps pipeline moving at −17% quality tradeoff (CASCON) vs hard failure.

---

### C.3 Hallucination — CHOKE high-certainty failure & calibration

**Core result (L2 — EMNLP Findings'25 peer-reviewed, MIT-affiliated author list; multiple venues):**

| Claim | Source | Level | Detail |
|-------|--------|-------|--------|
| **Definition — CHOKE (Certain Hallucinations Overriding Known Evidence):** model **consistently answers a question correctly** but **seemingly trivial perturbation** (happens in real-world settings) causes **hallucinated response with high certainty** — "particularly concerning in high-stakes domains such as medicine or law, where model certainty is often used as proxy for reliability" | https://arxiv.org/pdf/2502.12964 · https://aclanthology.org/2025.findings-emnlp.792.pdf · https://doi.org/10.18653/v1/2025.findings-emnlp.792 | **L2** |
| **Prevalence:** CHOKE / Certainty Misalignment (CM) examples **9%-43%** across models, found in **both pretrained and instruction-tuned**, with **high consistency across prompts** compared to other hallucinations | https://arxiv.org/html/2510.24222v1 (HACK) · https://arxiv.org/pdf/2502.12964 §4 | **L2** |
| **Robustness to naive mitigation:** CHOKE high-certainty hallucinations **robust to temperature scaling / top-2-gap / semantic entropy** — standard calibration knobs do not fix it | EMNLP Findings 2025 + arXiv 2502.12964 (cited in findings H1) | **L2** |
| **Universal blind spot on knowledge-gap inputs (SelfAware benchmark):** **13 of 15 GPT-4o scorers fall below AUROC 0.5** (worse than random) + **P(True) inverts 0.675 (TriviaQA) → 0.331 (SelfAware)** — output-level uncertainty quantification collapses simultaneously; linear probe on Llama-3-8B last-layer hidden states also fails **AUROC 0.44 CI [0.35,0.53]** — preliminary evidence failure persists at activation level | https://arxiv.org/html/2606.02289 (DECK) | **L2** |
| **Proposed mitigation — HACK CM-Score + linear probe:** new evaluation metric CM-Score; **uncertainty probes** = linear models trained on hidden states to predict numerical uncertainty, extracted from **last token of question across multiple layers**; cost-efficient vs multi-sample + auxiliary model (Farquhar et al.) | https://arxiv.org/html/2510.24222v1 · https://aclanthology.org/2025.emnlp-main.187.pdf | **L2** |
| **Calibrating Verbal Uncertainty as Linear Feature:** trains linear probes on hidden states for SU (semantic uncertainty) — confirms probe direction | https://aclanthology.org/2025.emnlp-main.187.pdf | **L2** |
| **Anchored Confabulation (arXiv 2604.25931):** partial evidence **non-monotonically amplifies** confident hallucination — **k=1 retrieved passage amplifies, k=3 eliminates**; AUC measurement of anchoring, formal routing consequences | https://arxiv.org/html/2604.25931v1 | **L2** |
| **KcMF taxonomy (arXiv 2410.12480) — hallucination / under-matching / over-matching** — related gaps | cited in findings H1 | **L2** |

**Direct implication for SalesLens mapping (founder note: SalesLens "heuristic-first then LLM only if LLM>heuristic via Ollama qwen3.5:9b — unevaluated, inherits heuristic overconfidence, CHOKE high-certainty hallucinations break triage"):**

- Current triage uses **raw LLM confidence** to decide LLM vs heuristic winner. CHOKE proves a model can be **confidently wrong on a trivially perturbed variant** of a mapping it gets right at baseline — so a high-confidence LLM hallucination (e.g., mapping `Revenue ($)` → `profit` with confidence 0.92) will **override** the correct heuristic `→ revenue 0.70`, creating a wrong `confirmed` mapping.
- Naive fixes (temperature, top-2 gap) **do not work** per CHOKE robustness result. What does: **linear uncertainty probe on hidden states** (HACK/EMNLP'25) or **consistency × confidence taxonomy (DECK)** that checks whether high certainty is *calibrated* on knowledge-gap inputs (SelfAware-style probe with abbreviation-heavy retail columns).
- **Anchored Confabulation** predicts the failure mode for retrieval-augmented mapping: adding **k=1 irrelevant retrieved column candidate** *amplifies* confident hallucination (vs k=3 eliminates) — directly explains KG-RAG4SM's "irrelevant knowledge ... performance will be decreased" and why **retrieval-on for ≤15 fields hurts** (H2): one noisy retrieval hit poisons the small candidate pool non-monotonically.

**Actionable handling pattern:**

1. **Never gate LLM>heuristic on raw confidence.** Replace with **calibrated score**: `calibrated = probe(hidden_states)` or at minimum **bidirectional consistency** (Scalable stable matching) — if LLM mapping `a→b` not reciprocated `b→a` with high rank, down-weight regardless of confidence.
2. **Admit `unknown` (TaDA decisiveness-score pattern):** if `|p1−p2| < margin` or probe signals knowledge-gap, return `unknown` → `state: pending` for human, not `confirmed`. Track **decisiveness** (fraction non-unknown) alongside F1.
3. **Partial-evidence guard:** when retrieval on, enforce **k≥3** or abstain — single-retrieval regime is the anchored-confabulation danger zone.

---

### C.4 Retrieval gating & stratification implementation

**Cross-cutting with §B.2 but actionably here:**

| Claim | Source | Level | Implementation |
|-------|--------|-------|----------------|
| "Irrelevant knowledge ... performance will be decreased" — retrieval **needs ranking/pruning**, not always-on | KG-RAG4SM text (search B3) | **L4/L2 mechanistic** | Gate retrieval by **target cardinality**: ≤15 off / ≥50 on / 15-30 tunable (oss-mapping-wedge.md §5) — now grounded in anchored confabulation + KG-RAG4SM admission |
| ReMatch `no-retrieval −6.5%` / `no-description −31%` — description matters more than retrieval table choice | https://arxiv.org/html/2403.01567v2 | **L3** | Canonical field descriptions (Dagster asset docs) are higher ROI than corpus search; keep retrieval off until J/K held-out proves +Δ |
| Schemora vector+lexical retrieval + metadata enrichment → HitRate helps when metadata rich | http://arxiv.org/abs/2507.14376 | **L3** | Retrieval quality × description quality interaction — cannot gate on cardinality alone; need **corpus quality gate** (if target corpus has sparse descriptions, skip retrieval) |

**Gating algorithm (ship as Dagster config switch, not hardcoded):**

```python
# dagster asset config — model-agnostic switches (oss-mapping-wedge.md §5)
use_retrieval = (
    target_cardinality >= 50 or
    (30 <= target_cardinality < 50 and jaccard_coverage_heldout > 0.4)
)  # else False — default off for ≤15 and low-coverage corpora
# J/K are retrieval candidates & context window size, tuned on HELD-OUT not test
```

**Measurement requirement:** Exp.3 interaction p<0.10 before claiming stratification is real — otherwise treat retrieval as uniform (ReMatch ablation suggests uniform help is plausible).

---

### C.5 Dagster model lifecycle — keeping AI handling inside the orchestrator

**What we learned (L3 official docs/discussions/issues):**

| Claim | Source | Level | Detail |
|-------|--------|-------|--------|
| **PipesSubprocessClient pattern:** `@dg.asset` execution function opens **Dagster Pipes session** and invokes subprocess via `PipesSubprocessClient.run` synchronously; returns `PipesClientCompletedInvocation` with typed artifact reporting | https://docs.dagster.io/integrations/external-pipelines/using-dagster-pipes/create-subprocess-asset | **L3 (docs)** | Is the textbook isolation for Sato/TURL Python deps from JVM windowing — but default is **per-invocation spawn** (cold-start multiplied) |
| **Asset checks → blocking semantics:** `blocking=True` on `AssetCheckSpec` — if check fails with `AssetCheckSeverity.ERROR`, **downstream assets won't execute**; multi-asset must enforce that downstream steps in same step do not run after blocking failure; gating applies **only to failed results, not missing** (warning logged) | https://docs.dagster.io/api/dagster/asset-checks | **L3** | Mapping validation (`mapping validate`) must be a **blocking asset check** gating canonical load — exactly the "Soda gate before Postgres write" pattern (Soda blog) |
| **Resource lifecycle for standing models:** override `yield_for_execution` (default calls `setup_for_execution` → yield → `teardown_after_execution`) to keep context open for run duration — useful for **DB connections or file handles — and for a warm model server handle** | https://docs.dagster.io/guides/build/external-resources/managing-resource-state | **L3** | Standing Ollama endpoint handle belongs in a Dagster `ConfigurableResource`, not per-asset spawn |
| **Cold-start economics inside Dagster:** trivial 3 ops: **12s overhead with full imports, 5s stripped**; per-`K8sClient.run` adds cost; **forkserver vs spawn** matters; adaptor discussion: empty-op latency = per-step spawn; **forkserver multiprocessing drastically improves per-process overhead** | https://github.com/dagster-io/dagster/discussions/19666 · https://github.com/dagster-io/dagster/discussions/17899 | **L3** | Proves per-step Python model load cannot be at parity with warm daemon — forkserver/preload is mandatory |
| **Latency step_worker_started → resource_init (issue #25780):** new process re-imports all packages; tune via **`tuna` + `PYTHONPROFILEIMPORTTIME=1`**; confirmed per-step cold-start is **import cost** | https://github.com/dagster-io/dagster/issues/25780 | **L3** | Tooling exists to profile import-time contribution |
| **Forkserver preload broken on 3.12 (#30893):** `preload_modules` worked on 3.11, broken on 3.12 ("subsequent steps as slow as first, as if spawn") — **pin to 3.11 until fix** if using forkserver | https://github.com/dagster-io/dagster/issues/30893 | **L3 (2025-06-25)** | Concrete risk: 3.12 upgrade silently reintroduces cold-start |
| **ECS avoidance via standing gRPC server (#7332):** `DefaultRunLauncher` + standing gRPC server avoids ECS per-run cold-start; custom wrapper can choose | https://github.com/dagster-io/dagster/discussions/7332 | **L3** | Same pattern as Markaicode Ollama standing Pods — convergent architecture |
| **Asset check automation:** `AutomationCondition.on_cron` executes asset check **once per cron tick after upstream updated** — allows slower cadence than asset itself (useful when mapping quality check is slow) | https://docs.dagster.io/guides/automate/declarative-automation/automating-asset-checks | **L3** | Mapping AI quality check can run at lower frequency than canonical ingest at 30s — avoids 2,880 checks/day |
| **PythonScriptComponent subprocess via Pipes:** `PythonScriptComponent` runs Python scripts in subprocess via Dagster Pipes — alternative encapsulation for Sato/TURL scripts | https://github.com/dagster-io/dagster/blob/master/docs/docs/guides/build/components/building-pipelines-with-components/python-script-component-tutorial.md | **L3** | Lower ceremony than manual PipesSubprocessClient for research-track models |

**Dagster model lifecycle for SalesLens (proposed — aligns Oss mapping wedge § Distribution as Dagster asset/component):**

| Layer | Implementation | Cold-start handling |
|-------|----------------|---------------------|
| **Ollama qwen3:8B** | **Standing `OllamaResource` (`ConfigurableResource`)** — holds `httpx.AsyncClient` to `host.docker.internal:11434` (or K8s Pod via S3 s3fs in prod) — `setup_for_execution` health-checks `/api/tags`, `teardown` no-op | Readiness-probe pre-warm on K8s; no per-asset model load |
| **Sato/Doduo (research track)** | `PipesSubprocessClient`-invoked script via `PythonScriptComponent` **only on demand** (not per-window); behind feature flag + fallback to regex | Batch invocation, not streaming; avoids per-window spawn; `8 tokens/col` sampling keeps sequence within 512 |
| **Soda column contracts** | `SodaScanComponent` pattern: `checks_paths` + `configuration.yml` + dataset→`AssetKey`; mapped as **blocking asset checks** | Same Dagster resource for DuckDB `:memory:` ephemeral |
| **Retrieval (RECA/TURL corpus)** | Only if corpus exists; embedding cache behind `CachedResource` | Offline `compute_jaccard.py` pre-processing; not per-window |

**Testing pattern:** asset-check testing doc shows **two jobs** (asset vs check) + **sensor on failure email** (https://docs.dagster.io/guides/test/asset-checks). Mapping harness must ship same: `job_canonical_load` (blocking check = mapping validate) vs `job_mapping_quality` (nightly calibration `on_cron`, not per-window).

---

## D. Kill Criteria & Deferral Decision (when to defer Sato/TURL to research track vs ship regex fallback)

### D.1 Why this decision exists (founder flag + critique verdict)

- Founder flagged the whole LLM handling gap alongside G-MOAT-01 — previous agent `bg_b1211d14` spun on it.
- Critique §4 verdict: **P0 — blocks build (if claimed as moat in MVP); P2 if explicitly deferred** — justification: "As a research moat it is legitimate long-term, but as an MVP stub it is a trap. A naive stub will be slower, more brittle, and less accurate on dirty data than the regex baseline it replaces — exactly opposite of a moat."
- Findings H2 (Contested 45-60%) + H3 (Strongly falsified 78-88% for ≥5× Python) together imply: do not ship a moat that depends on the falsified plane winning *and* on unproven 7-9B>GPT-4 transfer.
- This section makes the deferral/promotion **mechanical**, not judgmental.

---

### D.2 Default posture: DEFER (ship regex + statistical fallback)

**MVP type inference (P0 closable without learned model):**

```
Profiling chain (SalesLens current):
  INTEGER → DECIMAL → BOOLEAN → DATE → DATETIME → EMAIL → PHONE
  → CURRENCY_AMOUNT → CATEGORY[unique<20] → FREE_TEXT
  via `allMatch` regex on sample (conservative, known to be formally ill-posed per Sato Fig.1 but operative)

Statistical augmentation (ships in MVP, no deps):
  + null-rate + distinct-count + top-K + character distribution histograms
  + per-column uniqueness ratio → CATEGORY refinement for low-cardinality codes
  + locale-aware TRY_CAST guard (complements D: currency parse)

No Sato/TURL/RECA on the hot path. Registry stays heuristic-first.
LLM remains conditional: "only if LLM confidence > heuristic AND calibrated" (see §C.3).
```

**Why this is the right default:**

- Ships in 2-3 months solo-dev (§2 wedge) — no 6-12 week research program on critical path.
- Dirty-CSV failure mode where learned model is *worse* (`__EMPTY_3` / `NMFMT` / locale) is correctly handled by conservative regex + statistical fallback.
- No 500 MB–2 GB embedding closure in Dagster user-code, no GPU requirement, no per-window cold-start.
- Proven OOM-free at 180 cols (regex is O(rows×cols), not O(rows×cols×1588) features).

---

### D.3 Promotion gate — when learned inference graduates from research track to MVP

**Three conjunctive conditions must ALL pass on a single held-out benchmark.** Report cards must be checked into `/.omo/plans/research/` before wiring.

#### Gate L1 — Dirty-CRM hold-out F1

| Requirement | Threshold | Measurement | Why 2pp |
|-------------|-----------|-------------|---------|
| Corpus | Curated **dirty retail/CRM wide tables** — ≥20 tables, ≥5 with ≥100 cols (inc. ≥1 with 180 cols), abbreviation-heavy headers, `col_14`/`__EMPTY_3` present, locale formats, high-card null GROUP BYs, compound tokens | New corpus (not VizNet/WebTables) — must be **private / not web-crawled** or ITD-rephrased (§B.3) to avoid 52-57% leakage inflation | — |
| Ground truth | DBpedia-78 or domain-extended types with **human-verified labels**; FK≡PK equivalence noted per LLMATCH | Via `mapping_manifest.json`-style annotation | — |
| Split | **J/K held-out** for retrieval (if RECA/TURL) + train/test held-out — **no test-set tuning** (ConTAM §B.3) | — | — |
| Metric | **Support-weighted F1** (primary) + macro F1 (reported) on the dirty corpus — same choice as Sherlock/Sato to be comparable | Sato 0.925 vs Sherlock 0.89 is the baseline delta | — |
| Delta | **Learned F1 > (regex + statistical fallback) by ≥2 percentage points** on the **same held-out dirty corpus** | 5 runs, CI non-overlapping (or p<0.10 paired test) | 2pp is the **minimum meaningful** on wide dirty — Sato's clean uplift is ~3.6pp; dirty uplift <2pp is not worth infra cost |
| Runs | **5 repeats**, mean ± SD + **per-table variance** — wide table outlier (180-col) must be reported separately, not averaged away | Follows TaDA/LLMATCH 5-run pattern | — |

#### Gate L2 — Latency & memory envelope

| Requirement | Threshold | Measurement |
|-------------|-----------|-------------|
| Inference p99 | **<500 ms per table** end-to-end **including feature extraction** (not POST-feature CRF-only) on **CPU warm** (standing daemon, not per-request spawn) | Measured on 180-col worst-case table, 50k rows, via Dagster `PipesSubprocessClient` warm path + `tuna` profiling |
| Cold path isolation | Per-window spawn **not required** — model behind standing `OllamaResource` / Pipes daemon; **cold-start amortized** per §C.1 Markaicode topology | Demonstrate `setup_for_execution` warm vs per-asset spawn Δ |
| Memory | **OOM-free at 180 cols** on **8 GB container limit** with `threads=1` fallback (issues GH #17090/#11334 pattern if needed) | Peak RSS logged per run; any OOM = fail |
| Dependencies | Closure ≤ **500 MB** additional on top of Dagster image (quantized Q4) or explicitly justified GPU VRAM budget | No 2 GB embedding surprise |

#### Gate L3 — Causally attributable to model, not to prefilter

| Requirement | How to prove |
|-------------|--------------|
| Ablation | Learned vs regex+statistical **with identical type prefilter** (N→k) if prefilter is used — prefilter gain cannot be attributed to Sato/TURL (Scalable §3: prefilter >50% token cut is independent) |
| Qualitative wins | Report **which types** improved (expect: abbreviation-heavy where context matters, e.g., `rev` disambiguated by table topic) vs which degraded (expect: `__EMPTY_3` still degraded) — proves transfer is not uniform illusion |

**All three gates green → promote.** Any single gate red → stay deferred. No partial promotion (e.g., "run Sato only on narrow tables") without a second stratified gate — adds complexity a solo dev cannot operate.

---

### D.4 LLM 7-9B locality kill gate (H2 hedge — Exp.3 factorial)

This is the model-scale analogue of the Sato gate — required by oss-mapping-wedge.md §5/Exp.3 and Finding 2 "would be confirmed falsified by" clause.

**Experiment 3 (from plan §5):** qwen3:8b vs GPT-4 × **vanilla vs bidirectional+aggregation+prefilter** × **retrieval off/on (J/K held-out)**, on **MIMIC/Synthea + ≥1 retail/CRM dirty tabular**, with `accuracy@1 / HitRate@3 / F1`, **5 runs**, `tokens×calls`, **3 cardinality strata (≤15 / 16-49 / ≥50)**.

| Kill condition | Threshold | Action |
|----------------|-----------|--------|
| **7-9B+technique Δ≤0 vs GPT-4 single-pass at matched harness** (same J/K held-out, same corpus, 5-run CI overlapping or p>0.10) | Any of F1 or HitRate@1 — if GPT-4 ≥ 7-9B+technique, locality fails | **Kill hosted-vs-local claim** — switch default to **hosted GPT-4** with same bidirectional+aggregation harness; keep qwen3:8B only as **fallback in FALLBACK_CHAIN**, not primary |
| **Retrieval interaction p>0.10** (no stratification) | Interaction test across 3 strata fails significance | **Kill retrieval stratification** — remove `target_cardinality` gating, decide retrieval uniformly (off by default per ReMatch ablation showing `no-description −31%` matters more than retrieval) |
| **Inflation-adjusted accuracy collapses after ITD / held-out rephrasing** | Applying ITD (Findings-EMNLP'24) or rephrased private hold-out drops 7-9B accuracy by >10pp while GPT-4 holds | **Invalidate the run** — rebuild corpus as private; do not ship claim on leaked benchmark |

**Survival condition (all required):**

- 7-9B + bidirectional+aggregation+prefilter **beats GPT-4 single-pass by ≥10pp accuracy@1** on same splits (MIMIC/Synthea **and** retail/CRM dirty), 5 runs, CI non-overlapping.
- AND retrieval ablation shows **≤15 fields Δ≤−3pp (hurts), ≥50 fields Δ≥+5pp (helps)**, interaction **p<0.10**, cost-per-mapping lower for 7-9B stack.

If these hold, 7-9B+technique survives as primary with stratification gating — per Finding 2 corroboration clause.

---

### D.5 Model lifecycle governance (Dagster — per founding concern on production handling)

**Who owns the model, and when does it load?**

| Concern | Decision | Why |
|---------|----------|-----|
| Registry mutability | `REGISTRY` stays `CopyOnWriteArrayList` + `promote/demote` endpoints — **do not wire learned model as mutation** until Gates L1-L3 green | Mutable static registry is already the wedge's audit risk — adding learned promotion without gate compounds |
| Ollama process | `@Profile("!test")` OllamaConfig — **Kafka auto-startup `auto-startup: false` in tests that don't need Kafka** (ANTI-PATTERN note) applies analogously: **Dagster + Ollama resources mocked in unit tests** (not in `@WebMvcTest` slice) | Existing test style: `@ExtendWith(MockitoExtension.class) + @Mock/@InjectMocks` — keep model deps out of fast suite |
| Flyway | Learned-model metadata (corpus hash, training run ID, F1 card) in **new `V15__` migration**, never edit V1-V14 (ANTI-PATTERN: "Never edit a Flyway migration once applied") | If research track promotes, ledger + lineage need corpus versioning |
| Quality checkers | All 6 checkers remain registered in `QualityEngineService.checkers` — learned inference does not bypass quality | Anti-pattern: "All 6 quality checkers must be registered when adding new dimensions" — type inference feeds profiling, not quality bypass |
| Git | Mapping YAML `version:` pin + `mapping_manifest.json` content hash in replay ledger (G-YAML-01 pattern + G-LEDGER-01 `is_unchanged` hash) — **model version pin alongside data hash** | Without this, old rules + new model recompile differently; replay promise fails |

---

### D.6 Timeline & effort — why deferral saves the wedge

| Path | Effort | Calendar | Risk |
|------|--------|----------|------|
| **DEFER (ship regex+statistical)** | Already done (SalesLens `ProfilingService` regex chain + quality checkers) — add statistical fallback + `tokens×calls` harness instrumentation | 1 week to harden + Exp.3 harness scaffolding in parallel | No ML research on critical path; wedge demo not OOM-prone |
| **PROMOTE (Sato/TURL/RECA)** | Corpus curation 1-2 wks + feature pipeline 1 wk + training/finetune on dirty 2-3 wks + profiling at 180-col 1 wk + Gates L1-L3 formal report 1 wk | **6-10 weeks** before eligible to wire | 3-4 weeks firefighting per wide-table customer if shipped naive (critique §4) — exceeds solo-dev capacity at 30s streaming cadence |
| **Exp.3 LLM factorial** | 2-3 weeks, parallel with bake-off (plan §6 Exp.2+3) — does not block mapping compiler or ledger (§1-3 P0s) | Same window as defer | Required either way — even deferred moat needs harness to prove fallback is cheaper |

**Decision:** **DEFER learned inference to research track; EXPENSE Exp.3 as a parallel fact-finding mission, not a launch blocker.** Revisit promotion only after wedge has 2-3 paying evaluators generating real dirty-CRM corpora (labels from human `pending→confirmed` flow become the finetune dataset — PAYGO feedback ordering, not WebTables).

---

### D.7 What "research track" means concretely (not a parking lot)

| Deliverable | Where it lives | Who runs it | When it runs |
|-------------|----------------|-------------|--------------|
| Dirty-CRM corpus (20+ tables, wide + abbrev + locale) | `/.omo/plans/research/datasets/dirty-crm-corpus/` (private, not web-published — leakage control) | Founder/evaluator labelling via PAYGO queue | Weeks 1-2 of wedge bake-off (parallel) |
| Feature extraction benchmark (1,588 feats ×180 cols) + `tuna` profile | Same, as notebook | Solo dev, Dagster Pipes batch (not per-window) | Week 2-3 |
| Sato(Sato-pretrained) zero-shot vs regex baseline on dirty corpus | Same, gated report | Solo dev, CPU warm | Week 3-4 |
| Doduo `8 tokens/col` sampling variant + finetuned-on-dirty ablation if zero-shot fails | Same | Solo dev, optional HuggingFace finetune | Week 5-6 conditional |
| Gates L1-L3 report card | `G-MOAT-01-DIRTY-BENCHMARK.md` | Solo dev | Week 7-8 — promotes or formally defers with evidence |

**Success is measured, failure is documented, neither blocks the YAML→SQL compiler or deterministic replay — the wedge's actual moat until learned inference earns its promotion.**

---

## Appendix — Screening Log (per-block hits & exclusions)

### Block A — Learned type inference

| Query | Top hits (screened) | Included | Excluded (why) |
|-------|---------------------|----------|----------------|
| A1 web_search 10 hits | VLDB'20 PDF (zhang), Sato GH megagonlabs/sato, sherlock.media.mit.edu, KDD PDF, arXiv 1905.10688, Megagon blog, sato GH mirror (tabbydoc) — 7 included | Sato architecture/CRF/training cost/latency; Sherlock features/dependencies/failure modes | Filtered generic "semantic detection" without table context |
| A2 academic 10 hits | RECA VLDB16 pdf (sun), TURL VLDB14 pdf (deng), TURL GH sunlab-osu, arXiv 2006.14806, ACM doi, ysunbp RECA GH, SIGMODRecord, Google Research TURL, TabEmb ACL'26 | TURL corpus size/encoder/epochs/visibility/MER; RECA Jaccard/corpus dependency/F1; TabEmb quadratic edges | Excluded non-tabular entity linking hits |
| A3 academic 10 hits | Sato VLDB20 §5.4 training/latency (cagataydemiralp copy), doi, megagonlabs/sato GH, ar5iv html, tabbydoc mirror | Training 81/367s, inference +1.4s/6.4K tables (0.2ms/table, 0.8ms avg) | Deduplicated repeated Sato abstract hits |
| Snowball A — dirty/abbrev | KDD Sherlock §2 failure `country NMFMT/INTCA/XPLZS`, SAP example, Sherlock GH, DL ACM doi | Abbrev transfer gap | No dirty-retail benchmark exists — negative result logged |
| Snowball A — Doduo | Doduo ICDE'22 doi, Sato arXiv 1911, megagonlabs/doduo GH, Megagon publication, SportsTables GI.de, ACL long 757 | 8 tokens/col, 512 token limit, wide-table splitting | Deduplicated Sato GH |

### Block B — LLM eval harness

| Query | Top hits | Included | Excluded |
|-------|----------|----------|----------|
| B1 web_search 10 hits | ReMatch arXiv 2403 HitRate@K, OAEI-LLM CEUR, Schemora 2507 HitRate@5/3, OAEI-LLM-T 2503 TBox, Valentine GH, LLMATCH 2507 F1, Schema Matching Exp Study 2407 F1/decisiveness, Valentine VLDB, ResearchGate Valentine review | ReMatch HitRate@K vs F1 m:n limit; OAEI-LLM hallucination taxonomy; Schemora SOTA; LLMATCH 135-cols; TaDA scope comparison; Valentine L1 framework | Generic LLM eval not schema-matching |
| B2 academic 10 hits | ConTAM 2411 n-gram large-scale, GEM 2026 55-study, blog benchmark contamination test-vs-val, NLP Eval findings-emnlp 722, ITD 532 (22.9%/19%), NAACL 482 TS-Guessing 52-57%, Recheck Naive 291, Rethinking 2311 Rephrased 8-18% HumanEval overlap, NAACL html 2311 | TS-Guessing 52/57, ConTAM larger than reported, GEM health overlap inflates, ITD 22.9/19, rephrased 8-18%, Reliability Gap OLMo2 detection failure | Generic benchmark leakage not MIMIC-specific already captured |
| B3 web_search 10 hits | LLM Latency TTFT/cache/routing (ContextOS 2026-08), Resilient LLM-DBMS (IEEE), Cache-hit strategy (DevFloor9), llm-serving-cost-and-latency (sirmarkz), GonkaRouter caching, Model Fallbacks (beek), Solidigm prompt anatomy, Resilient circuit breaker (2025-12) | TTFT/prefix cache, IEEE fault-injection p95/p99 −18% + cost halved, cache layers, FALLBACK_CHAIN, semantic-cache risk | Generic API latency without schema-matching retrieval framing |
| Snowball — Scalable | Scalable 2505 (issues 1-3, aggregation, Def 3.1, 77.44 vs 62.20), technical report Def 3.1 Gale-Shapley, prompt-matcher CSP 2408 (1-1/e NP-hard), alphaxiv overview | Stable matching Def, sampling/aggregation, bidirectional, prefilter N→k >50%, CSP budget-hard | Generic RAG surveys without Gale-Shapley |
| Snowball — TS-Guessing | NAACL 482 52% ChatGPT / 57% GPT-4, arXiv 2311 TS-Guessing html, openreview leakage pdf | 52/57 upper bound contaminated → ~100% EM | Duplicate NAACL variants deduped |

### Block C — Production handling

| Query | Top hits | Included | Excluded |
|-------|----------|----------|----------|
| C1 web_search 10 hits | Ollama system design K8s S3 s3fs (Markaicode 2026-05 85% util 300ms p95), Qwen3-8B 4090 Q4 5.03GB 131K, Qwen3 HW reqs LLMhardware 5GB@Q4/16.4GB BF16, Cloud Run cold-start, DigitalOcean hidden cost, johal.in Ollama vs vLLM retrospective (1.2s vs 4.8s cold, 210ms vs 89ms p99, 4.2 vs 14.8GB VRAM, $0.004 vs $0.002/1M), scale-to-zero 140GB for 70B, model-cold-start-bench 5 phases (774M 61%/36%), vLLM vs Ollama 2026-08 (3s vs 9s), Qwen3.8-27B 24GB rec | Q4 sizes, 5-phase cold-start, PCIe-growing, 70B 2×H100, Ollama standing topology | Generic LLM VRAM guides without Qwen3 GGUF pin |
| C2 academic 10 hits | CHOKE arXiv 2502 pdf definition, Findings-EMNLP 792, doi 792, duplicate pdf, HACK 2510 CM 9-43%, arXiv 2502 html, duplicate, Calibrating Verbal Uncertainty 187 linear probe, Anchored Confabulation 2604 k=1 amplifies, DECK 2606 13/15 scorers <0.5 | CHOKE definition/prevalence/robustness, HACK 9-43% + CM-Score, DECK universal blind spot AUROC 0.44, Anchored k=1 amplifies k=3 eliminates, probe mitigation | Generic hallucination surveys without CHOKE |
| Snowball C — Dagster | Pipes subprocess asset (L3), asset-checks blocking (§C.5), managing resource state (L3), testing asset checks (§C.5), pipes reference (L3), asset_check_decorator.py (L3), issue 12707 lifecycle (§C.5), automating asset checks (§C.5), PythonScriptComponent (§C.5), Dagster Asset Checks blog | PipesSubprocessClient, blocking semantics, resource yield_for_execution, forkserver costs, standing gRPC | Generic DAG orchestration not model-lifecycle |

---

## References (abridged — full rows in SOURCE-TABLE-GAPS.md G-MOAT-01)

> Levels reflect distance to primary source. ⚠️ = pre-2022 stale, architecture only. Every quantitative claim above traces to a row below.

| # | Title | URL | Level | Key Claim |
|---|-------|-----|-------|-----------|
| A-1 | Sherlock KDD'19 | https://sherlock.media.mit.edu/ · https://arxiv.org/abs/1905.10688 | L2 ⚠️ | 686,765 cols × 1,588 feats × 78 DBpedia types; F1 0.89 support-weighted |
| A-2 | sherlock-project | https://github.com/mitmedialab/sherlock-project | L3 | ~500MB closure + preprocessing pipeline |
| A-3 | Sato VLDB'20 | https://www.vldb.org/pvldb/vol13/p1835-zhang.pdf · https://doi.org/10.14778/3407790.3407793 | L2 | Hybrid topic+LDA+CRF, +3.6pp over Sherlock, 26K tables, 0.2ms POST-feature / 0.8ms avg on 64c/512GB |
| A-4 | Sato training/latency §5.4 | https://cagataydemiralp.io/projects/table-understanding/Sato-VLDB20.pdf · https://ar5iv.labs.arxiv.org/html/1911.06311 | L2 | 81s/367s training; +1.4s/6.4K tables CRF overhead |
| A-5 | Sato GH | https://github.com/megagonlabs/sato | L3 | Pretrained on VizNet — dirty transfer zero-shot |
| A-6 | TURL VLDB'21 | https://www.vldb.org/pvldb/vol14/p307-deng.pdf · https://arxiv.org/abs/2006.14806 | L2 | 570K tables (avg 13×2), TinyBERT N=4 d=312 k=12, visibility matrix, MER, 80 epochs |
| A-7 | TURL GH | https://github.com/sunlab-osu/TURL | L3 | split 570171/5036/4964; Docker xdeng/transformers |
| A-8 | RECA VLDB'23 | https://vldb.org/pvldb/vol16/p1319-sun.pdf · https://doi.org/10.14778/3583140.3583149 | L2 | Inter-table Jaccard/topic corpus, F1 0.853/0.937 on WebTables, short sequences/wide-table, but corpus-required |
| A-9 | RECA GH | https://github.com/ysunbp/RECA-paper | L3 | compute_jaccard + pre-process — no corpus = no signal |
| A-10 | Doduo ICDE'22 | https://doi.org/10.1145/3514221.3517906 | L2 | BERT, 8 tokens/col beats SOTA, 512 token ceiling, sampling mitigates wide |
| A-11 | doduo GH | https://github.com/megagonlabs/doduo | L3 | megagonlabs/doduo toolbox, AnnotatedDataFrame |
| A-12 | TabEmb ACL'26 | https://aclanthology.org/2026.acl-long.757.pdf | L2 | +4.9/+5.2/+9.2 over Doduo but quadratic inter-col edges expensive on wide |
| A-13 | SportsTables | https://dl.gi.de/bitstreams/5ae1849e-6819-48e9-aaa3-ba6b2da0940f/download | L2 | Wide-table is open problem — prior benchmarks narrow |
| B-1 | Valentine/ICDE21 | http://disi.unitn.it/~pavel/OM/articles/Koutras_ICDE21.pdf · https://github.com/delftdata/valentine | L1/L3 | L1 framework 7 matchers, 4 scenarios — no LLM native |
| B-2 | ReMatch 2403 | https://arxiv.org/html/2403.01567v2 | L3 | Accuracy@K ≡ F1 at 1:1/m:1 via argmax; m:n punts — need own harness |
| B-3 | OAEI-LLM | https://ceur-ws.org/Vol-3953/361.pdf | L3 | 7 datasets, hallucination taxonomy, 10 LLMs leaderboard |
| B-4 | Schemora 2507 | http://arxiv.org/abs/2507.14376 | L3 | Metadata+retrieval SOTA MIMIC HitRate@5 +7.49 / @3 +3.75 |
| B-5 | LLMATCH 2507 | https://arxiv.org/html/2507.10897 · https://arxiv.org/html/2507.10897v1 | L3 | SchemaNet 135cols/14tabs 9× prior, Def 3.1 O(target) calls, FK≡PK equiv, 512→ fragmentation |
| B-6 | TaDA 2024 / Exp Study 2407 | https://tabular-data-analysis.github.io/tada2024/papers/TaDA.8.pdf · https://arxiv.org/html/2407.11852 | L2 | 1-to-N dominates 1-to-1, GPT-4>3.5 all scopes, decisiveness-score + 5 repeats |
| B-7 | Scalable 2505 + report | https://arxiv.org/html/2505.24716v1 · https://research.engr.oregonstate.edu/idea/.../scalable_schema_mapping-technical_report.pdf | L2 | Def 3.1 stable matching (Gale-Shapley), sampling/aggregation, bidirectional, prefilter N→k >50% |
| B-8 | Prompt-Matcher CSP 2408 | https://arxiv.org/html/2408.14507v1 | L2 | CSP NP-hard (1−1/e) approx, budget/k, `budget > cost(all) & >3×mean` degenerate else |
| B-9 | KG-RAG4SM 2501 | (search B3 head-to-head) https://arxiv.org/abs/2501.08686 inferred | L3 | GPT-4o-mini+KG beats 8B 35.89/69.20 — contradicts H2 small hurts |
| B-10 | NAACL TS-Guessing 2311 | https://aclanthology.org/2024.naacl-long.482 · https://doi.org/10.48550/arxiv.2311.09783 | L2 | 52% ChatGPT / 57% GPT-4 EM on MMLU missing-option — 100% when fully contaminated |
| B-11 | ConTAM 2411 | https://arxiv.org/html/2411.03923 | L2 | 13 benches ×7 models: contamination >> reported |
| B-12 | GEM 2026 | https://aclanthology.org/2026.gem-main.50.pdf | L2 | 55-study taxonomy: health overlap inflates |
| B-13 | ITD 2024 Findings-EMNLP | https://aclanthology.org/2024.findings-emnlp.532.pdf | L2 | Detect+rewrite leaked → −22.9% GSM8K / −19% MMLU; Phi-3 −6.7 / Mistral −3.6 |
| B-14 | Rethinking 2311 Rephrased | https://doi.org/10.48550/arxiv.2311.04850 | L2 | 8-18% HumanEval overlap in RedPajama/StarCoder; synthetic contamination |
| B-15 | Reliability Gap 2606 | https://doi.org/10.48550/arxiv.2606.03305 | L2 | OLMo2 GSM8K-train inclusion: detectors can fail — need procedural held-out |
| B-16 | Beyond Scale 2607 | (arXiv 2607.24688) | L2 | 1,215-run factorial: larger not consistently win; technique≥scale but non-monotonic |
| C-1 | Ollama prod design K8s S3 | https://markaicode.com/architecture/ollama-system-design-architecture-956/ | L4 | S3+s3fs+fuse + readiness probe + HPA on GPU mem → 85% util 300ms p95 |
| C-2 | Qwen3-8B 4090 Q4 | https://smeltcore.com/recipes/qwen3-8b-on-rtx-4090-q4-k-m-gguf-via-ollama-or-llama-cpp/ | L4 | 5.03GB Q4 / 16.4GB BF16, 24GB KV headroom 131K YaRN |
| C-3 | johal.in retrospective | https://johal.in/retrospective-building-self-hosted-llm-ollama-03-vllm-04 | L4 | 1.2s vs 4.8s cold, p99 210ms vs 89ms, 4.2 vs 14.8GB VRAM, $0.004 vs $0.002/1M |
| C-4 | model-cold-start-bench | https://github.laiyagushi.com/JohnScheuer/model-cold-start-bench | L4 | 5 phases: 774M 61%/36% PCIe-growing; 70B 140GB 2×H100 |
| C-5 | CHOKE Findings-EMNLP 792 | https://aclanthology.org/2025.findings-emnlp.792.pdf · https://arxiv.org/pdf/2502.12964 | L2 | 9-43% CHOKE, robust to temp/top2/entropy, vital for triage |
| C-6 | HACK 2510 | https://arxiv.org/html/2510.24222v1 | L2 | CM 9-43%, CM-Score, consistency across prompts |
| C-7 | DECK 2606 | https://arxiv.org/html/2606.02289 | L2 | 13/15 GPT-4o scorers <0.5 AUROC; P(True) 0.675→0.331; probe fails 0.44 |
| C-8 | Anchored 2604 | https://arxiv.org/html/2604.25931v1 | L2 | k=1 amplifies, k=3 eliminates — predicts small-retrieval poisoning |
| C-9 | Calibrating Verbal Unc. 187 | https://aclanthology.org/2025.emnlp-main.187.pdf | L2 | Linear probe on hidden states for SU |
| C-10 | CASCON IEEE 2025 | https://doi.org/10.1109/cascon66301.2025.00113 | L2 | p95 −18%, p99 −18%, cost halved, avail 96.5→100%, but −17% quality on fallback |
| C-11 | Dagster Pipes + Asset Checks | https://docs.dagster.io/integrations/.../create-subprocess-asset · https://docs.dagster.io/api/dagster/asset-checks | L3 | PipesSubprocessClient spawn per-invocation; blocking=True downstream gate |
| C-12 | Dagster resources / lifecycle | https://docs.dagster.io/guides/build/external-resources/managing-resource-state | L3 | yield_for_execution keeps warm handle; forkserver matters |

---

*All URLs live-fetched 2026-08-30 via SearxNG/Exa. Rephrased paraphrase only where PDF extraction was truncated; no URL fabricated. Stale pre-2022 flagged ⚠️.*

*Next: wire gates into oss-mapping-wedge.md §5/§6; SOURCE-TABLE-GAPS.md § G-MOAT-01 appended.*

