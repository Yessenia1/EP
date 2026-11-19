import json
import statistics
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8085"
USUARIOS = int(sys.argv[2]) if len(sys.argv) > 2 else 50
VENTAS_POR_USUARIO = int(sys.argv[3]) if len(sys.argv) > 3 else 1
MODO = sys.argv[4] if len(sys.argv) > 4 else "mismo-lote"


def llamar(metodo, ruta, cuerpo=None, token=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    inicio = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            cuerpo_resp = json.loads(r.read() or b"{}")
            codigo = r.status
    except urllib.error.HTTPError as e:
        cuerpo_resp = json.loads(e.read() or b"{}")
        codigo = e.code
    except Exception:
        cuerpo_resp, codigo = {}, 0
    return codigo, (time.perf_counter() - inicio) * 1000, cuerpo_resp


def percentil(valores, p):
    ordenados = sorted(valores)
    k = max(0, min(len(ordenados) - 1, int(round(p / 100 * len(ordenados) + 0.5)) - 1))
    return ordenados[k]


_, _, login = llamar("POST", "/api/auth/login", {"login": "admin-articulos", "password": "admin123"})
token = login["data"]["token"]
llamar("POST", "/api/caja-chica/cajas", {"nombre": "Caja carga", "idSucursal": 1, "idPuntoVenta": 1, "saldoInicial": 100}, token)
catalogos = [1] if MODO == "mismo-lote" else [1, 2, 3, 4, 5, 6]
lotes = []
for c in catalogos:
    _, _, fefo = llamar("GET", f"/api/productos/catalogo/{c}/sucursal/1/fefo", token=token)
    if fefo.get("data"):
        lotes.append((c, fefo["data"][0]))
lote = lotes[0][1]
print(f"Modo: {MODO} | lotes FEFO usados: " + ", ".join(f"{l['nroLote']} (stock {l['stock']})" for _, l in lotes))


def venta_de(i):
    c, l = lotes[i % len(lotes)]
    return {"tipoDoc": "03", "estado": "VIGENTE", "metodoPago": "EFECTIVO", "nombrePac": "CLIENTE VARIOS", "nroDni": "00000000", "tipoDni": "1",
            "detalles": [{"idCatalogo": str(c), "idArticulo": str(l["idProducto"]), "glosa": "PRODUCTO", "cantidad": 1,
                          "precioUnitario": 8.0, "total": 8.0, "unidadMedida": "UND"}]}


def usuario(i):
    return [llamar("POST", "/api/ventas", venta_de(i), token)[:2] for _ in range(VENTAS_POR_USUARIO)]


inicio = time.perf_counter()
with ThreadPoolExecutor(max_workers=USUARIOS) as ex:
    resultados = [r for lista in ex.map(usuario, range(USUARIOS)) for r in lista]
duracion = time.perf_counter() - inicio

tiempos = [t for _, t in resultados]
ok = sum(1 for c, _ in resultados if c in (200, 201))
print(f"Usuarios concurrentes: {USUARIOS} | ventas: {len(resultados)} | exitosas: {ok} | errores: {len(resultados) - ok}")
print(f"Duración total: {duracion:.2f} s | rendimiento: {len(resultados) / duracion:.1f} ventas/s")
print(f"Latencia (ms): mín {min(tiempos):.0f} | media {statistics.mean(tiempos):.0f} | p50 {percentil(tiempos, 50):.0f} | "
      f"p90 {percentil(tiempos, 90):.0f} | p95 {percentil(tiempos, 95):.0f} | p99 {percentil(tiempos, 99):.0f} | máx {max(tiempos):.0f}")
p95 = percentil(tiempos, 95)
print(f"RNF-14 (p95 < 2000 ms con {USUARIOS} usuarios): {'CUMPLE' if p95 < 2000 and ok == len(resultados) else 'NO CUMPLE'}")
_, _, despues = llamar("GET", "/api/productos/catalogo/1/sucursal/1/fefo", token=token)
print(f"Stock del lote tras la prueba: {despues['data'][0]['nroLote']} = {despues['data'][0]['stock']}")
