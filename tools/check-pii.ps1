# PII gate: fail if tracked files contain real student identifiers or person names.
# ASCII ONLY. PS 5.1 parses a BOM-less .ps1 as ANSI, so any CJK literal here would
# break the parser -- Chinese allowlist entries live in tools\pii-allowlist.json.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\check-pii.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\check-pii.ps1 -SelfTest
# Exit: 0 = clean, 1 = findings (do not commit) / selftest failure
#
# Why: docs/README.md says "no real student id, name, token, password", and that rule
# was broken once (2026-11-15: real names and ids had been pasted into docs and tests).
# Redact with the synthetic shapes below: 2026000001 / 13000000000 / synthetic name.

param([switch]$SelfTest)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$allow = [System.IO.File]::ReadAllText(
    (Join-Path $root 'tools\pii-allowlist.json'),
    [System.Text.Encoding]::UTF8
) | ConvertFrom-Json

$allowValues = @($allow.values)
$allowNames = @($allow.names)
$syntheticPrefix = [string]$allow.name_prefix_synthetic

$patterns = [ordered]@{}
$patterns['student_id'] = '(?<!\d)20\d{8}(?!\d)'
$patterns['phone'] = '(?<!\d)1[3-9]\d{9}(?!\d)'
$patterns['id_card'] = '(?<!\d)\d{17}[\dXx](?!\d)'
$patterns['secret'] = '(?:Bearer\s+[A-Za-z0-9._\-]{20,}|"token"\s*:\s*"[A-Za-z0-9._\-]{16,}")'
$patterns['person_name'] = '"(?:name|user_name|userName|studentName|realName|contactName|applyUserName|signName)"\s*:\s*"([\u4e00-\u9fa5]{2,4})"'

function Test-Line([string]$line) {
    $found = New-Object System.Collections.Generic.List[string]
    foreach ($kind in $patterns.Keys) {
        foreach ($m in [regex]::Matches($line, $patterns[$kind])) {
            if ($kind -eq 'person_name') {
                $value = $m.Groups[1].Value
                if ($syntheticPrefix -ne '' -and $value.StartsWith($syntheticPrefix)) { continue }
                if ($allowNames -contains $value) { continue }
                $found.Add('person_name')
                continue
            }
            $value = $m.Value
            $allowed = $false
            foreach ($a in $allowValues) {
                if ($value -eq $a -or $value.Contains($a)) { $allowed = $true; break }
            }
            if (-not $allowed) { $found.Add($kind) }
        }
    }
    return $found
}

if ($SelfTest) {
    $okName = ''; if ($allowNames.Count -gt 0) { $okName = [string]$allowNames[0] }
    # Fixtures are concatenated so this file itself does not contain a matching literal.
    $fid = '2026' + '777777'
    $fphone = '195' + '77778888'
    $fcard = '11010119900307' + '9999'
    $cases = @(
        @{ line = ('put("code", "' + $fid + '")'); expect = 'student_id' },
        @{ line = ('val mobile = "' + $fphone + '"'); expect = 'phone' },
        @{ line = ('val id = "' + $fcard + '"'); expect = 'id_card' },
        @{ line = '{"user_name":"NotAllowed"}'; expect = '' },
        @{ line = 'put("code", "2026000001")'; expect = '' },
        @{ line = 'val mobile = "13000000000"'; expect = '' }
    )
    if ($okName -ne '') {
        $cases += @{ line = '{"name":"' + $okName + '"}'; expect = '' }
    }
    $failed = 0
    foreach ($c in $cases) {
        $got = Test-Line $c.line
        $ok = if ($c.expect -eq '') { $got.Count -eq 0 } else { ($got -join ',') -match [regex]::Escape($c.expect) }
        if (-not $ok) {
            $failed++
            Write-Host ("SELFTEST FAIL: {0} -> [{1}]" -f $c.line, ($got -join ','))
        }
    }
    if ($failed -gt 0) { Write-Host 'selftest failed'; exit 1 }
    Write-Host 'selftest passed: id / phone / id-card detected, synthetic values ignored'
    exit 0
}

Push-Location $root
try {
    # --others --exclude-standard: also check files that are about to be added (untracked but not ignored)
    $files = & git ls-files --cached --others --exclude-standard
} finally {
    Pop-Location
}

$binaryExt = '\.(png|jpg|jpeg|webp|gif|ico|jar|zip|apk|jks|keystore|so|ttf|otf|woff2?)$'
$findings = New-Object System.Collections.Generic.List[string]
$scanned = 0

foreach ($file in $files) {
    if ($file -match $binaryExt) { continue }
    $path = Join-Path $root $file
    if (-not (Test-Path -LiteralPath $path)) { continue }
    $scanned++
    $text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $lines = $text -split "`r?`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        foreach ($hit in (Test-Line $lines[$i])) {
            $findings.Add(('{0}:{1} [{2}]' -f $file, ($i + 1), $hit))
        }
    }
}

Write-Host ("scanned {0} tracked text files" -f $scanned)
if ($findings.Count -gt 0) {
    Write-Host ("FOUND {0} potential PII hit(s):" -f $findings.Count)
    foreach ($f in $findings) { Write-Host ('  ' + $f) }
    Write-Host 'Redact them before committing (synthetic id / phone / name).'
    exit 1
}
Write-Host 'clean: no student id / phone / id-card / token / person name found'
exit 0
