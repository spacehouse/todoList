[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$ProjectRoot,

    [Parameter(Mandatory = $false)]
    [string]$Branch,

    [Parameter(Mandatory = $false)]
    [string]$Date,

    [Parameter(Mandatory = $false)]
    [string[]]$Stages = @("ruleset", "pr-checks", "artifact", "release-precheck")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

if ([string]::IsNullOrWhiteSpace($Date)) {
    $Date = (Get-Date).ToString("yyyyMMdd")
}

if ($Date -notmatch "^\d{8}$") {
    throw "Invalid date format: $Date. Expected yyyyMMdd."
}

if ([string]::IsNullOrWhiteSpace($Branch)) {
    try {
        $gitBranch = git -C $ProjectRoot rev-parse --abbrev-ref HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($gitBranch)) {
            $Branch = "$gitBranch".Trim()
        }
    } catch {
        $Branch = ""
    }
}

if ([string]::IsNullOrWhiteSpace($Branch)) {
    $Branch = "main"
}

$branchNormalized = ($Branch -replace "[^0-9A-Za-z._-]", "-").Trim("-")
if ([string]::IsNullOrWhiteSpace($branchNormalized)) {
    $branchNormalized = "main"
}

$year = $Date.Substring(0, 4)
$screenshotsRoot = Join-Path $ProjectRoot "artifacts\governance\screenshots"
$yearRoot = Join-Path $screenshotsRoot $year
$dayRoot = Join-Path $yearRoot $Date
$branchRoot = Join-Path $dayRoot $branchNormalized

foreach ($path in @($screenshotsRoot, $yearRoot, $dayRoot, $branchRoot)) {
    New-Item -ItemType Directory -Path $path -Force | Out-Null
}

$stagePaths = New-Object System.Collections.Generic.List[string]
foreach ($stage in $Stages) {
    if ([string]::IsNullOrWhiteSpace($stage)) {
        continue
    }
    $stageDir = Join-Path $branchRoot $stage.Trim()
    New-Item -ItemType Directory -Path $stageDir -Force | Out-Null
    $stagePaths.Add($stageDir)
}

Write-Host "[screenshot-archive] projectRoot=$ProjectRoot"
Write-Host "[screenshot-archive] date=$Date"
Write-Host "[screenshot-archive] branch=$branchNormalized"
Write-Host "[screenshot-archive] archiveRoot=$branchRoot"
Write-Host "[screenshot-archive] stages=$($stagePaths -join ';')"
exit 0
