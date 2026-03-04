<#
.SYNOPSIS
Parse COMMAND_RESULT_TRIPLET logs and compare triplets against a baseline JSON.

.DESCRIPTION
Extracts `code`, `messageKey`, and `sideEffects` from log lines that contain
`COMMAND_RESULT_TRIPLET`, then compares with baseline data.
Default mode is in-order comparison; use -IgnoreOrder for multiset comparison.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$LogPath,

    [Parameter(Mandatory = $true)]
    [string]$BaselinePath,

    [Parameter(Mandatory = $false)]
    [string]$ReportPath,

    [Parameter(Mandatory = $false)]
    [switch]$IgnoreOrder
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Normalize-SideEffects {
    param(
        [AllowNull()]
        [object[]]$SideEffects
    )

    $normalized = New-Object System.Collections.Generic.List[string]
    if ($null -ne $SideEffects) {
        foreach ($effect in $SideEffects) {
            if ($null -eq $effect) {
                continue
            }
            $value = "$effect".Trim().ToUpperInvariant()
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                $normalized.Add($value)
            }
        }
    }

    if ($normalized.Count -eq 0) {
        return @("NONE")
    }

    $unique = @($normalized | Sort-Object -Unique)
    if ($unique.Count -gt 1) {
        $unique = @($unique | Where-Object { $_ -ne "NONE" })
    }
    if ($unique.Count -eq 0) {
        return @("NONE")
    }
    return @($unique)
}

function Parse-TripletsFromLog {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Log file not found: $Path"
    }

    $pattern = "COMMAND_RESULT_TRIPLET.*?code=(?<code>-?\d+)\s+messageKey=(?<messageKey>\S+)\s+sideEffects=\[(?<sideEffects>[^\]]*)\]"
    $result = New-Object System.Collections.Generic.List[object]
    $lineNumber = 0

    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $lineNumber++
        $match = [regex]::Match($line, $pattern)
        if (-not $match.Success) {
            continue
        }

        $rawEffects = $match.Groups["sideEffects"].Value
        $effects = @()
        if (-not [string]::IsNullOrWhiteSpace($rawEffects)) {
            $effects = $rawEffects.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
        }

        $result.Add([PSCustomObject]@{
                index       = $result.Count + 1
                lineNumber  = $lineNumber
                code        = [int]$match.Groups["code"].Value
                messageKey  = $match.Groups["messageKey"].Value
                sideEffects = Normalize-SideEffects -SideEffects $effects
            })
    }

    return $result.ToArray()
}

function Load-BaselineTriplets {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Baseline file not found: $Path"
    }

    $jsonRaw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    $jsonData = $jsonRaw | ConvertFrom-Json

    $entries = @()
    if ($jsonData -is [System.Array]) {
        $entries = @($jsonData)
    } elseif ($null -ne $jsonData.triplets) {
        $entries = @($jsonData.triplets)
    } else {
        throw "Invalid baseline JSON. Expected array or object with property 'triplets'."
    }

    $baseline = New-Object System.Collections.Generic.List[object]
    foreach ($entry in $entries) {
        if ($null -eq $entry) {
            continue
        }
        $baseline.Add([PSCustomObject]@{
                index       = $baseline.Count + 1
                code        = [int]$entry.code
                messageKey  = [string]$entry.messageKey
                sideEffects = Normalize-SideEffects -SideEffects @($entry.sideEffects)
            })
    }

    return $baseline.ToArray()
}

function New-TripletSignature {
    param([Parameter(Mandatory = $true)][pscustomobject]$Triplet)
    return "{0}|{1}|[{2}]" -f $Triplet.code, $Triplet.messageKey, ($Triplet.sideEffects -join ",")
}

function Compare-TripletsInOrder {
    param([object[]]$Baseline, [object[]]$Actual)

    $baselineItems = @($Baseline)
    $actualItems = @($Actual)
    $diffs = New-Object System.Collections.Generic.List[object]
    $maxLength = [Math]::Max($baselineItems.Length, $actualItems.Length)

    for ($i = 0; $i -lt $maxLength; $i++) {
        $expected = if ($i -lt $baselineItems.Length) { $baselineItems[$i] } else { $null }
        $actual = if ($i -lt $actualItems.Length) { $actualItems[$i] } else { $null }
        $position = $i + 1

        if ($null -eq $expected) {
            $diffs.Add([PSCustomObject]@{
                    type     = "EXTRA"
                    position = $position
                    field    = "triplet"
                    baseline = "<missing>"
                    actual   = (New-TripletSignature -Triplet $actual)
                })
            continue
        }

        if ($null -eq $actual) {
            $diffs.Add([PSCustomObject]@{
                    type     = "MISSING"
                    position = $position
                    field    = "triplet"
                    baseline = (New-TripletSignature -Triplet $expected)
                    actual   = "<missing>"
                })
            continue
        }

        if ($expected.code -ne $actual.code) {
            $diffs.Add([PSCustomObject]@{
                    type     = "MISMATCH"
                    position = $position
                    field    = "code"
                    baseline = [string]$expected.code
                    actual   = [string]$actual.code
                })
        }

        if ($expected.messageKey -ne $actual.messageKey) {
            $diffs.Add([PSCustomObject]@{
                    type     = "MISMATCH"
                    position = $position
                    field    = "messageKey"
                    baseline = $expected.messageKey
                    actual   = $actual.messageKey
                })
        }

        $expectedEffects = $expected.sideEffects -join ","
        $actualEffects = $actual.sideEffects -join ","
        if ($expectedEffects -ne $actualEffects) {
            $diffs.Add([PSCustomObject]@{
                    type     = "MISMATCH"
                    position = $position
                    field    = "sideEffects"
                    baseline = "[{0}]" -f $expectedEffects
                    actual   = "[{0}]" -f $actualEffects
                })
        }
    }

    return $diffs.ToArray()
}

function Compare-TripletsIgnoreOrder {
    param([object[]]$Baseline, [object[]]$Actual)

    $diffs = New-Object System.Collections.Generic.List[object]
    $baselineCountMap = @{}
    $actualCountMap = @{}

    foreach ($triplet in $Baseline) {
        $signature = New-TripletSignature -Triplet $triplet
        if (-not $baselineCountMap.ContainsKey($signature)) {
            $baselineCountMap[$signature] = 0
        }
        $baselineCountMap[$signature]++
    }

    foreach ($triplet in $Actual) {
        $signature = New-TripletSignature -Triplet $triplet
        if (-not $actualCountMap.ContainsKey($signature)) {
            $actualCountMap[$signature] = 0
        }
        $actualCountMap[$signature]++
    }

    $allSignatures = @($baselineCountMap.Keys + $actualCountMap.Keys | Sort-Object -Unique)
    foreach ($signature in $allSignatures) {
        $expectedCount = if ($baselineCountMap.ContainsKey($signature)) { $baselineCountMap[$signature] } else { 0 }
        $actualCount = if ($actualCountMap.ContainsKey($signature)) { $actualCountMap[$signature] } else { 0 }
        if ($expectedCount -ne $actualCount) {
            $diffs.Add([PSCustomObject]@{
                    type     = "COUNT_MISMATCH"
                    position = "-"
                    field    = "tripletCount"
                    baseline = ("{0} x {1}" -f $expectedCount, $signature)
                    actual   = ("{0} x {1}" -f $actualCount, $signature)
                })
        }
    }

    return $diffs.ToArray()
}

try {
    $actualTriplets = @(Parse-TripletsFromLog -Path $LogPath)
    $baselineTriplets = @(Load-BaselineTriplets -Path $BaselinePath)

    if ($IgnoreOrder.IsPresent) {
        $diffs = @(Compare-TripletsIgnoreOrder -Baseline $baselineTriplets -Actual $actualTriplets)
    } else {
        $diffs = @(Compare-TripletsInOrder -Baseline $baselineTriplets -Actual $actualTriplets)
    }

    $actualCount = @($actualTriplets).Length
    $baselineCount = @($baselineTriplets).Length
    $diffCount = @($diffs).Length

    Write-Host "=== COMMAND_RESULT_TRIPLET Compare ==="
    Write-Host ("actual count: {0}" -f $actualCount)
    Write-Host ("baseline count: {0}" -f $baselineCount)
    Write-Host ("mode: {0}" -f ($(if ($IgnoreOrder.IsPresent) { "ignore-order" } else { "in-order" })))

    $report = [PSCustomObject]@{
        generatedAt   = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        logPath       = (Resolve-Path -LiteralPath $LogPath).Path
        baselinePath  = (Resolve-Path -LiteralPath $BaselinePath).Path
        compareMode   = $(if ($IgnoreOrder.IsPresent) { "ignore-order" } else { "in-order" })
        actualCount   = $actualCount
        baselineCount = $baselineCount
        diffCount     = $diffCount
        diffs         = @($diffs)
    }

    if (-not [string]::IsNullOrWhiteSpace($ReportPath)) {
        $reportDir = Split-Path -Path $ReportPath -Parent
        if (-not [string]::IsNullOrWhiteSpace($reportDir) -and -not (Test-Path -LiteralPath $reportDir)) {
            New-Item -Path $reportDir -ItemType Directory | Out-Null
        }
        $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
        Write-Host ("report: {0}" -f (Resolve-Path -LiteralPath $ReportPath).Path)
    }

    if ($diffCount -eq 0) {
        Write-Host "PASS"
        exit 0
    }

    Write-Warning ("FAIL with {0} differences." -f $diffCount)
    $diffs | Format-Table -AutoSize | Out-String | Write-Host
    exit 1
} catch {
    Write-Error ("Compare failed: {0}`n{1}" -f $_.Exception.Message, $_.ScriptStackTrace)
    exit 2
}
