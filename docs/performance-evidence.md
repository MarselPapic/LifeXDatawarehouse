# Performance Evidence (Requirements 2.1.03, 3.1.1, 3.1.2)

Date: `2026-02-22`  
Tested build/commit: `3b72456`  
Environment: `Docker (Windows host), app image lifexdw:perf, k6 via grafana/k6, Docker engine: 16 vCPU / 15.22 GiB RAM`

## 1. Requirement mapping
- `2.1.03` Database load expectations
- `3.1.1` Performance UI search (`<= 10s`)
- `3.1.2` Performance UI report rendering (`<= 10s`)

## 2. Data volume snapshot (Req 2.1.03 context)
Source: `perf/sql/req-2.1.03-volume-snapshot.sql`

| Metric | Measured value |
|---|---|
| customers (accounts) | `30` |
| projects (total) | `38` |
| sites (total) | `55` |
| servers (total) | `28` |
| clients / working positions (total) | `40` |
| installed software (total) | `55` |
| service contracts (total) | `28` |
| service contracts per project (max) | `1` |
| components per project (max) | `12` |

Volume artifact location: `artifacts/perf/20260222-162541-docker/volume-snapshot.{json,csv}`

## 3. Load test profiles and commands
Runner: `grafana/k6` container  
Script: `perf/k6/search-and-reports.js`

Command used:

```powershell
docker build -t lifexdw:perf .
docker network create lifex-perf
docker run -d --name lifexdw-perf --network lifex-perf -p 8080:8080 lifexdw:perf
docker run --rm --network lifex-perf -v "${PWD}:/src" -w /src grafana/k6 run perf/k6/search-and-reports.js --env BASE_URL=http://lifexdw-perf:8080 --env BACKEND_USER=lifex --env BACKEND_PASSWORD=12345 --env PROFILE=warmup --summary-export artifacts/perf/20260222-162541-docker/warmup-summary.json
docker run --rm --network lifex-perf -v "${PWD}:/src" -w /src grafana/k6 run perf/k6/search-and-reports.js --env BASE_URL=http://lifexdw-perf:8080 --env BACKEND_USER=lifex --env BACKEND_PASSWORD=12345 --env PROFILE=normal --summary-export artifacts/perf/20260222-162541-docker/normal-summary.json
docker run --rm --network lifex-perf -v "${PWD}:/src" -w /src grafana/k6 run perf/k6/search-and-reports.js --env BASE_URL=http://lifexdw-perf:8080 --env BACKEND_USER=lifex --env BACKEND_PASSWORD=12345 --env PROFILE=peak --summary-export artifacts/perf/20260222-162541-docker/peak-summary.json
```

## 4. Results
| Profile | Search p95 (ms) | Search error rate | Reports p95 (ms) | Reports error rate | Pass/Fail |
|---|---:|---:|---:|---:|---|
| warmup | `59.07` | `0.00%` | `55.93` | `0.00%` | `PASS` |
| normal | `73.15` | `0.00%` | `71.26` | `0.00%` | `PASS` |
| peak | `268.79` | `0.00%` | `257.24` | `0.00%` | `PASS` |

Raw artifacts:
- `artifacts/perf/20260222-162541-docker/warmup-summary.json`
- `artifacts/perf/20260222-162541-docker/normal-summary.json`
- `artifacts/perf/20260222-162541-docker/peak-summary.json`
- `artifacts/perf/20260222-162541-docker/warmup-console.log`
- `artifacts/perf/20260222-162541-docker/normal-console.log`
- `artifacts/perf/20260222-162541-docker/peak-console.log`

## 5. Conclusion
- Req `3.1.1` (UI search <= 10s): `PASS`
- Req `3.1.2` (UI report rendering <= 10s): `PASS`
- Req `2.1.03` load expectation evidence status: `CONDITIONAL` with comments.

Comments:
- Die 10s-SLA fuer Search und Reports ist in allen Profilen deutlich unterschritten.
- Die Volumenwerte sind gegen den aktuellen Demo-Seed (`docs/data-volume-plan.md`) erhoben und dokumentiert.
- Falls fuer die Abnahme explizit ein hoeheres Lastziel (z. B. 200/200/1000) gefordert wird, muss ein zusaetzlicher Seed + erneuter Lauf erfolgen.
