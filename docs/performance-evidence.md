# Performance Evidence (Requirements 2.1.03, 3.1.1, 3.1.2)

Date: `<YYYY-MM-DD>`  
Tested build/commit: `<git-sha>`  
Environment: `<local/docker/vm + cpu/ram>`

## 1. Requirement mapping
- `2.1.03` Database load expectations
- `3.1.1` Performance UI search (`<= 10s`)
- `3.1.2` Performance UI report rendering (`<= 10s`)

## 2. Data volume snapshot (Req 2.1.03 context)
Source: `perf/sql/req-2.1.03-volume-snapshot.sql`

| Metric | Measured value |
|---|---|
| customers (accounts) | `<value>` |
| projects (total) | `<value>` |
| sites (total) | `<value>` |
| servers (total) | `<value>` |
| clients / working positions (total) | `<value>` |
| installed software (total) | `<value>` |
| service contracts (total) | `<value>` |
| service contracts per project (max) | `<value>` |
| components per project (max) | `<value>` |

Volume artifact location: `artifacts/perf/<timestamp>/volume-snapshot.<csv|png|pdf>`

## 3. Load test profiles and commands
Runner: `perf/run-k6.ps1`  
Script: `perf/k6/search-and-reports.js`

Command used:

```powershell
powershell -ExecutionPolicy Bypass -File perf/run-k6.ps1 -Profile all -BaseUrl "http://localhost:8080" -BackendUser "lifex" -BackendPassword "12345"
```

## 4. Results
| Profile | Search p95 (ms) | Search error rate | Reports p95 (ms) | Reports error rate | Pass/Fail |
|---|---:|---:|---:|---:|---|
| warmup | `<value>` | `<value>` | `<value>` | `<value>` | `<pass/fail>` |
| normal | `<value>` | `<value>` | `<value>` | `<value>` | `<pass/fail>` |
| peak | `<value>` | `<value>` | `<value>` | `<value>` | `<pass/fail>` |

Raw artifacts:
- `artifacts/perf/<timestamp>/warmup-summary.json`
- `artifacts/perf/<timestamp>/normal-summary.json`
- `artifacts/perf/<timestamp>/peak-summary.json`
- `artifacts/perf/<timestamp>/warmup-console.log`
- `artifacts/perf/<timestamp>/normal-console.log`
- `artifacts/perf/<timestamp>/peak-console.log`

## 5. Conclusion
- Req `3.1.1` (UI search <= 10s): `<PASS/FAIL>`
- Req `3.1.2` (UI report rendering <= 10s): `<PASS/FAIL>`
- Req `2.1.03` load expectation evidence status: `<PASS/CONDITIONAL/FAIL>` with comments.

Comments:
- `<notes>`
