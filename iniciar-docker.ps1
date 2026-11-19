# Inicia Docker Desktop evitando el error "rename ... .sock.stale: El sistema no tiene acceso al archivo".
# En esta build de Windows los sockets AF_UNIX que deja Docker no se pueden renombrar ni borrar,
# por eso se apartan las carpetas de sockets antes de cada arranque. No toca imagenes ni volumenes.
Get-Process | Where-Object { $_.ProcessName -like "Docker Desktop" -or $_.ProcessName -like "com.docker*" } |
    ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 3
wsl --shutdown
$ts = Get-Date -Format "yyyyMMddHHmmss"
foreach ($d in @("$env:LOCALAPPDATA\Docker\run", "$env:LOCALAPPDATA\docker-secrets-engine")) {
    if (Test-Path $d) {
        Rename-Item $d ("$d" + "_old_$ts")
        New-Item -ItemType Directory $d | Out-Null
    }
}
Start-Process "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 5
    docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Host "Docker listo"; exit 0 }
}
Write-Host "Docker no respondio en 200 s; revisa %LOCALAPPDATA%\Docker\log\host\com.docker.backend.exe.log"
exit 1
