# Creates .env from .env.example, filling every empty value with a random secret.
# Leaves an existing .env untouched, so it's safe to run more than once.
$ErrorActionPreference = 'Stop'

Set-Location (Join-Path $PSScriptRoot '..')

if (Test-Path .env) {
    Write-Host '.env already exists; leaving it unchanged.'
    exit 0
}

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
function New-Secret {
    $bytes = New-Object byte[] 16
    $rng.GetBytes($bytes)
    -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

# WriteAllLines writes UTF-8 without a BOM, which Docker Compose needs to read the first variable name.
$out = foreach ($line in Get-Content .env.example) {
    if ($line -match '^([A-Z_]+)=$') { "$($matches[1])=$(New-Secret)" } else { $line }
}
[IO.File]::WriteAllLines((Join-Path (Get-Location) '.env'), $out)

Write-Host 'Created .env with generated values. Start the stack with: docker compose --profile app up --build -d --wait'
