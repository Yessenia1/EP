# =============================================================================
# Pipeline CI/CD local del ERP «Articulos» (equivalente a .github/workflows/ci-cd.yml)
# Uso:  $env:CI_DB_PASSWORD = '<clave de postgres local>'; .\ci\pipeline.ps1 [-ForzarFalloDespliegue]
# Etapas: 1 pruebas backend · 2 análisis estático · 3 mensajeria · 4 frontend · 5 secretos
#         6 imágenes versionadas · 7 despliegue · 8 pruebas de humo (rollback automático si 7/8 fallan)
# =============================================================================
param([switch]$ForzarFalloDespliegue)
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$reportes = Join-Path $raiz "ci\reportes"
New-Item -ItemType Directory -Force $reportes | Out-Null
$log = Join-Path $reportes "pipeline_$ts.log"
$sha = (git rev-parse --short HEAD).Trim()
if (git status --porcelain) { $sha = "$sha-$ts" }
$archivoVersion = Join-Path $raiz "ci\version-desplegada.txt"
$versionAnterior = if (Test-Path $archivoVersion) { (Get-Content $archivoVersion).Trim() } else { "latest" }
$resultados = @()

function Log($m) { $l = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $m; Write-Host $l; Add-Content $log $l }
function Etapa($nombre, [scriptblock]$accion) {
    Log "=== INICIO $nombre"
    $t = [Diagnostics.Stopwatch]::StartNew()
    try {
        & $accion 2>&1 | ForEach-Object { Add-Content $log "    $_" }
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw "código de salida $LASTEXITCODE" }
        $t.Stop(); Log ("=== OK     {0} ({1:N1} s)" -f $nombre, $t.Elapsed.TotalSeconds)
        $script:resultados += [pscustomobject]@{ Etapa = $nombre; Resultado = "OK"; Segundos = [math]::Round($t.Elapsed.TotalSeconds, 1) }
    } catch {
        $t.Stop(); Log ("=== FALLO  {0}: {1}" -f $nombre, $_)
        $script:resultados += [pscustomobject]@{ Etapa = $nombre; Resultado = "FALLO"; Segundos = [math]::Round($t.Elapsed.TotalSeconds, 1) }
        throw
    }
}

function Esperar-Saludable {
    for ($i = 0; $i -lt 40; $i++) {
        $b = docker inspect -f '{{.State.Health.Status}}' salud-total-erp-backend-1 2>$null
        $f = docker inspect -f '{{.State.Health.Status}}' salud-total-erp-frontend-1 2>$null
        if ($b -eq "healthy" -and $f -eq "healthy") { return }
        Start-Sleep -Seconds 5
    }
    throw "los contenedores no quedaron healthy (backend=$b, frontend=$f)"
}

Log "Pipeline $ts · versión candidata $sha · versión en producción $versionAnterior"
try {
    Etapa "1. Backend: pruebas unitarias e integración + cobertura" {
        Push-Location articulos-backend
        $env:DB_URL = "jdbc:postgresql://localhost:5432/clinica_db_e3"; $env:DB_USER = "postgres"; $env:DB_PASSWORD = $env:CI_DB_PASSWORD
        .\mvnw.cmd -B -q verify
        $c = Import-Csv target\site\jacoco\jacoco.csv
        $lc = ($c | Measure-Object LINE_COVERED -Sum).Sum; $lm = ($c | Measure-Object LINE_MISSED -Sum).Sum
        $n = ($c | Where-Object { $_.PACKAGE -like "*reabastecimiento*" -or $_.CLASS -eq "LoteFefoService" })
        $nc = ($n | Measure-Object LINE_COVERED -Sum).Sum; $nm = ($n | Measure-Object LINE_MISSED -Sum).Sum
        "Pruebas: " + ((Select-String -Path target\surefire-reports\*.txt -Pattern "Tests run").Line -join " | ")
        "Cobertura total: {0:N1} % · código E3: {1:N1} %" -f (100 * $lc / ($lc + $lm)), (100 * $nc / ($nc + $nm))
        if ((100 * $nc / ($nc + $nm)) -lt 60) { throw "cobertura del código nuevo menor a 60 %" }
        Pop-Location
    }
    Etapa "2. Análisis estático (PMD + CPD)" {
        Push-Location articulos-backend
        .\mvnw.cmd -B -q pmd:pmd pmd:cpd
        $v = ([xml](Get-Content target\pmd.xml)).pmd.file.violation
        "Violaciones PMD: $(@($v).Count)"
        $v | Group-Object rule | Sort-Object Count -Descending | Select-Object -First 8 | ForEach-Object { "  {0,-35} {1}" -f $_.Name, $_.Count }
        "Duplicados CPD (>=100 tokens): $(@(([xml](Get-Content target\cpd.xml)).'pmd-cpd'.duplication).Count)"
        Pop-Location
    }
    Etapa "3. mensajeria-api: compilar y empaquetar" {
        Push-Location mensajeria-api; .\mvnw.cmd -B -q -DskipTests package; Pop-Location
    }
    Etapa "4. Frontend: build de producción" {
        Push-Location articulos-frontend; npx ng build --configuration production; Pop-Location
    }
    Etapa "5. Escaneo de secretos" {
        $permitidos = Get-Content ci\secretos-permitidos.txt | Where-Object { $_ -and -not $_.StartsWith("#") } | ForEach-Object { ($_ -split "\s+")[0] }
        $patron = '(?i)(password|passwd|secret|token)\s*[=:]\s*["'']?[A-Za-z0-9#@!$%^&*_\-]{8,}|-----BEGIN (RSA |EC )?PRIVATE KEY-----'
        $hallazgos = git ls-files --cached --others --exclude-standard | Where-Object { $_ -notmatch '\.(png|jpg|ico|woff2?|ttf)$' -and $_ -notmatch '^pruebas/postman/resultado' } |
            ForEach-Object { Select-String -Path $_ -Pattern $patron -ErrorAction SilentlyContinue } |
            Where-Object { $_.Line -notmatch '\$\{' -and $_.Line -notmatch 'cambiar' }
        $nuevos = $hallazgos | Where-Object { $permitidos -notcontains $_.Path.Replace($raiz + '\', '').Replace('\', '/') }
        $hallazgos | ForEach-Object { "  {0}:{1}" -f $_.Path.Replace($raiz + '\', ''), $_.LineNumber }
        "Hallazgos: $(@($hallazgos).Count) · no permitidos: $(@($nuevos).Count)"
        if (@($nuevos).Count -gt 0) { throw "se encontraron secretos no registrados" }
    }
    Etapa "6. Construir imágenes versionadas ($sha)" {
        $env:APP_VERSION = $sha
        docker compose build --quiet backend mensajeria frontend
        docker images --format "{{.Repository}}:{{.Tag}} {{.Size}}" | Select-String "salud-total-erp-.*:$sha"
    }
    try {
        Etapa "7. Desplegar $sha" {
            $env:APP_VERSION = $sha
            docker compose up -d --no-build
            if ($ForzarFalloDespliegue) { docker compose stop backend; throw "fallo simulado del despliegue" }
            Esperar-Saludable
            docker compose ps --format "{{.Service}} {{.Image}} {{.Status}}"
        }
        Etapa "8. Pruebas de humo (health + Postman)" {
            (Invoke-RestMethod http://localhost:8085/actuator/health).status
            postman collection run pruebas\postman\E3_SaludTotal.postman_collection.json --reporters cli 2>&1 | Select-String "requests|assertions|failed" | ForEach-Object { $_.Line }
            if ($LASTEXITCODE -ne 0) { throw "fallaron las pruebas de humo" }
        }
        Set-Content $archivoVersion $sha
        Log "Despliegue confirmado: $sha"
    } catch {
        Log "ROLLBACK automático a $versionAnterior"
        Etapa "R. Rollback a $versionAnterior" { & (Join-Path $PSScriptRoot "rollback.ps1") -Version $versionAnterior }
        throw "despliegue revertido a $versionAnterior"
    }
    $estado = "EXITOSO"
} catch {
    $estado = "FALLIDO: $_"
}
Log "Resultado del pipeline: $estado"
$resultados | Format-Table -AutoSize | Out-String | ForEach-Object { Write-Host $_; Add-Content $log $_ }
if ($estado -ne "EXITOSO") { exit 1 }
