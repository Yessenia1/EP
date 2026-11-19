# ERP «Articulos» — Salud Total SAC (Entregable 3)

Sistema web de inventario por lotes, ventas, compras, caja y clínica para la cadena de farmacias Salud Total SAC (Juliaca y Puno).

| Componente | Tecnología | Puerto |
|---|---|---|
| `articulos-frontend` | Angular 20 + Tailwind 4, servido por Nginx | 8090 (Docker) / 4200 (desarrollo) |
| `articulos-backend` | Spring Boot 3.5.9, Java 21, JPA, Flyway, JWT RSA, WebSocket | 8085 |
| `mensajeria-api` | Spring Boot 3.2.4, Java 17 (correo SMTP y WhatsApp Cloud API) | 8087 |
| `db` | PostgreSQL 17 | 5433 (Docker) |

## Novedades del Entregable 3

- **FEFO obligatorio en la venta** (`LoteFefoService`): rechaza lotes vencidos y exige vender primero el lote que vence antes. `GET /api/productos/catalogo/{idCatalogo}/sucursal/{idSucursal}/fefo`.
- **Reabastecimiento automático** (módulo `reabastecimiento`): cada salida de stock publica `StockDisminuidoEvent`; tras el commit se evalúa el punto de reorden y se crea una orden `PENDIENTE` notificada por el Outbox. El comprador la aprueba, rechaza o convierte en orden de compra. Pantalla: *Compra → Reabastecimiento*.
- **Flyway activo**: `V1__esquema_base.sql` (esquema real) y `V2__reabastecimiento_e_integridad.sql` (tabla nueva, índices FEFO y del kardex, CHECK de stock y login único).
- **mensajeria-api**: nuevo `POST /api/v1/mensajes/whatsapp/enviar-con-adjunto` y soporte de plantillas en `/enviar`.
- **Configuración por variables de entorno**: sin contraseñas en los `.properties`.

## Ejecución con Docker (recomendado)

```bash
cp .env.example .env          # completar contraseñas
mkdir -p secrets/jwt          # copiar private.pem y public.pem
docker compose up -d --build
```

- Aplicación: http://localhost:8090 (usuario de prueba `admin-articulos`)
- API y Swagger: http://localhost:8085/swagger-ui.html
- Salud: http://localhost:8085/actuator/health

La base se crea vacía con Flyway; `db/init/01-rol-aplicacion.sh` crea el rol `st_app` (sin superusuario) como dueño de la base. Para cargar datos de demostración:

```bash
docker exec -i salud-total-erp-db-1 psql -U st_app -d clinica_db < pruebas/datos/datos_demo.sql
```

En Windows Insider, si Docker Desktop falla con `rename ... .sock.stale`, iniciarlo con `iniciar-docker.ps1`.

## Ejecución local (desarrollo)

```bash
# Backend (PostgreSQL local en 5432, base clinica_db)
cd articulos-backend
DB_PASSWORD=<clave> ./mvnw spring-boot:run

# Frontend
cd articulos-frontend
npm ci && npx ng serve

# Mensajería
cd mensajeria-api
MAIL_USERNAME=<correo> MAIL_PASSWORD=<clave-app> ./mvnw spring-boot:run
```

## Variables de entorno principales

| Variable | Uso | Valor por defecto |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local`, `docker` o `supabase` | `local` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión a PostgreSQL | local: `clinica_db` / `postgres` |
| `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH` | Llaves RSA (`classpath:` o `file:`) | `classpath:certs/*.pem` |
| `VENTAS_FEFO_ESTRICTO` | Exigir FEFO en la venta | `true` |
| `MENSAJERIA_API_URL` | URL de mensajeria-api | `http://localhost:8087` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos | `http://localhost:4200,http://localhost:4205` |
| `MAIL_*`, `WHATSAPP_*` | Credenciales de mensajería | vacías |

## Pruebas

```bash
postman collection run pruebas/postman/E3_SaludTotal.postman_collection.json
```

19 peticiones y 41 aserciones: autenticación, FEFO, venta de lote vencido, reabastecimiento (evaluación, aprobación, rechazo, conversión) y validaciones 400/401/409.
