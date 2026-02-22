# Performance and Load Evidence

This folder contains reproducible artifacts to prove:
- `2.1.03` Database load expectations (via volume snapshot + run conditions)
- `3.1.1` UI search response time <= 10 seconds
- `3.1.2` UI report rendering response time <= 10 seconds

## Files
- `perf/k6/search-and-reports.js`  
  k6 load test for `/search` and `/api/reports/data`.
- `perf/run-k6.ps1`  
  Runner with profiles: `warmup`, `normal`, `peak`.
- `perf/sql/req-2.1.03-volume-snapshot.sql`  
  SQL snapshot of current volume and per-project distribution.

## Prerequisites
- Running application (same setup as delivery target, ideally Dockerized).
- k6 installed and available in `PATH`.
- Backend credentials:
  - user: `lifex`
  - password: `12345`

## 1) Capture volume snapshot (Req 2.1.03 context)
Run `perf/sql/req-2.1.03-volume-snapshot.sql` in H2 console (`/h2-console`) and save results in:
- `artifacts/perf/<timestamp>/volume-snapshot.csv` (or screenshot export)

This captures the data volume used during the performance run.

## 2) Run k6 profiles
From repo root:

```powershell
powershell -ExecutionPolicy Bypass -File perf/run-k6.ps1 -Profile all -BaseUrl "http://localhost:8080" -BackendUser "lifex" -BackendPassword "12345"
```

Optional single profile:

```powershell
powershell -ExecutionPolicy Bypass -File perf/run-k6.ps1 -Profile normal
```

Artifacts are written under:
- `artifacts/perf/<timestamp>/warmup-summary.json`
- `artifacts/perf/<timestamp>/normal-summary.json`
- `artifacts/perf/<timestamp>/peak-summary.json`
- matching `*-console.log` files

## 3) Acceptance criteria for Req 3.1.1 and 3.1.2
k6 thresholds are defined in `perf/k6/search-and-reports.js`:
- Search: `p(95) < 10000ms`, `error rate < 1%`
- Reports: `p(95) < 10000ms`, `error rate < 1%`

If a threshold fails, k6 exits with non-zero code and the run is not accepted.

## 4) Evidence package for submission
Copy into the final submission:
- `artifacts/perf/<timestamp>/...` (all summaries + logs)
- volume snapshot export from SQL run
- completed `docs/performance-evidence.md`
