# Vuelve a la versión indicada de las imágenes sin reconstruir.
# Las migraciones Flyway son aditivas (nuevas tablas, índices y restricciones compatibles), por lo que
# la versión anterior del backend sigue funcionando sobre el esquema migrado.
param([Parameter(Mandatory = $true)][string]$Version)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
$env:APP_VERSION = $Version
docker compose up -d --no-build backend mensajeria frontend
for ($i = 0; $i -lt 40; $i++) {
    if ((docker inspect -f '{{.State.Health.Status}}' salud-total-erp-backend-1) -eq "healthy") { break }
    Start-Sleep -Seconds 5
}
docker compose ps --format "{{.Service}} {{.Image}} {{.Status}}"
"health: " + (Invoke-RestMethod http://localhost:8085/actuator/health).status
Set-Content (Join-Path $PSScriptRoot "version-desplegada.txt") $Version
