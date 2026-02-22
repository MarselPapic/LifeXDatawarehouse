param(
    [ValidateSet("warmup", "normal", "peak", "all")]
    [string]$Profile = "all",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$BackendUser = "lifex",
    [string]$BackendPassword = "12345",
    [string]$ReportPreset = "next90",
    [string]$OutputRoot = "artifacts/perf"
)

$ErrorActionPreference = "Stop"

$k6 = Get-Command k6 -ErrorAction SilentlyContinue
if (-not $k6) {
    throw "k6 is not installed or not on PATH. Install k6 first: https://grafana.com/docs/k6/latest/set-up/install-k6/"
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$testScript = Join-Path $scriptDir "k6/search-and-reports.js"

if (-not (Test-Path $testScript)) {
    throw "Missing k6 test script: $testScript"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runRoot = Join-Path $OutputRoot $timestamp
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null

$profiles = @{
    warmup = @{
        SearchVus = 5
        ReportVus = 2
        SearchDuration = "2m"
        ReportDuration = "2m"
    }
    normal = @{
        SearchVus = 20
        ReportVus = 10
        SearchDuration = "10m"
        ReportDuration = "10m"
    }
    peak = @{
        SearchVus = 40
        ReportVus = 20
        SearchDuration = "10m"
        ReportDuration = "10m"
    }
}

function Invoke-Profile([string]$Name, [hashtable]$Cfg) {
    $summaryFile = Join-Path $runRoot "$Name-summary.json"
    $consoleFile = Join-Path $runRoot "$Name-console.log"

    Write-Host "Running profile '$Name'..."
    Write-Host "  search: vus=$($Cfg.SearchVus), duration=$($Cfg.SearchDuration)"
    Write-Host "  reports: vus=$($Cfg.ReportVus), duration=$($Cfg.ReportDuration)"

    & k6 run $testScript `
        --summary-export $summaryFile `
        -e BASE_URL=$BaseUrl `
        -e BACKEND_USER=$BackendUser `
        -e BACKEND_PASSWORD=$BackendPassword `
        -e REPORT_PRESET=$ReportPreset `
        -e SEARCH_VUS=$($Cfg.SearchVus) `
        -e SEARCH_DURATION=$($Cfg.SearchDuration) `
        -e REPORT_VUS=$($Cfg.ReportVus) `
        -e REPORT_DURATION=$($Cfg.ReportDuration) 2>&1 | Tee-Object -FilePath $consoleFile

    if ($LASTEXITCODE -ne 0) {
        throw "k6 profile '$Name' failed. See: $consoleFile"
    }

    Write-Host "  summary: $summaryFile"
    Write-Host "  log:     $consoleFile"
}

$selected = if ($Profile -eq "all") { @("warmup", "normal", "peak") } else { @($Profile) }
foreach ($name in $selected) {
    Invoke-Profile -Name $name -Cfg $profiles[$name]
}

Write-Host ""
Write-Host "Done. Artifacts written to: $runRoot"
