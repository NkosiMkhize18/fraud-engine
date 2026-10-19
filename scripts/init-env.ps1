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

$out = foreach ($line in Get-Content .env.example) {
    if ($line -match '^([A-Z_]+)=$') { "$($matches[1])=$(New-Secret)" } else { $line }
}
[IO.File]::WriteAllLines((Join-Path (Get-Location) '.env'), $out)

Write-Host 'Created .env with generated values. Start the stack with: docker compose --profile app up --build -d --wait'
