$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $projectDirectory '.env'
if (Test-Path -LiteralPath $destination) { throw '.env already exists; it was not modified.' }
function New-Secret { return [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant() }
$lines = @(
 'DATABASE_PASSWORD=' + (New-Secret)
 'LAB_API_KEY=' + (New-Secret)
 'LAB_INBOX_TOKEN=' + (New-Secret)
 'LAB_ALLOWED_ORIGINS=http://demo:8090'
 'WEB_PORT=8088'
)
[IO.File]::WriteAllLines($destination, $lines, [Text.UTF8Encoding]::new($false))
Write-Output 'Created .env with random local secrets. Open it locally to obtain LAB_API_KEY.'
