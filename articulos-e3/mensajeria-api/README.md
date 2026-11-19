# Mensajería API 🚀

Microservicio encargado de centralizar y gestionar las notificaciones salientes de la clínica, incluyendo:
1. **Envío de Correos Electrónicos (Email)** con adjuntos (PDFs).
2. **Envío de Mensajes de WhatsApp** usando la API oficial de Meta (Plantillas/Templates).

---

## 🛠 Configuración Inicial

Antes de levantar el proyecto, asegúrate de actualizar el archivo `src/main/resources/application.properties` con tus credenciales reales:

- **WhatsApp API:** `whatsapp.api.url` y `whatsapp.api.token`.
- **Email (Gmail):** `spring.mail.username` y `spring.mail.password` (Usa una Contraseña de Aplicación de Google).
- **Seguridad:** Verifica que el archivo `public.pem` se encuentre en `src/main/resources/certs/` para que valide correctamente los tokens JWT.

---

## 📱 WhatsApp: Envío de Plantillas (Templates)

El endpoint para enviar plantillas pre-aprobadas de WhatsApp recibe un JSON dinámico, adaptándose a cualquier plantilla de Meta sin necesidad de modificar el código en Java. (Soporta múltiples variables de texto y botones rápidos por defecto de Meta).

### **Endpoint:**
`POST /api/v1/mensajes/whatsapp/send-template`

### **Headers requeridos:**
- `Content-Type`: `application/json`
- `Authorization`: `Bearer <Tu_Token_JWT_Valido>`

---

### 1️⃣ Plantilla: Laboratorio 🩸
Útil para notificar al paciente que sus resultados están listos.

**JSON Payload:**
```json
{
  "to": "51999123456",
  "templateName": "plantilla_de_laboratorio",
  "languageCode": "es_PE",
  "variables": [
    "Juan Pérez",
    "004589",
    "https://tuclinica.com/resultados/123"
  ]
}
```
* **Variable 1:** Nombre del paciente.
* **Variable 2:** Número de orden.
* **Variable 3:** Link para descargar/ver los resultados.

---

### 2️⃣ Plantilla: CDI / Imágenes 🩻
Útil para notificar informes de Ecografías, Rayos X, Tomografías, etc.

**JSON Payload:**
```json
{
  "to": "51999123456",
  "templateName": "plantilla_de_cdi",
  "languageCode": "es_PE",
  "variables": [
    "María Gómez",
    "Ecografía Abdominal",
    "Carlos Mendoza",
    "https://tuclinica.com/imagenes/456"
  ]
}
```
* **Variable 1:** Nombre del paciente.
* **Variable 2:** Tipo de examen.
* **Variable 3:** Nombre del Médico Radiólogo.
* **Variable 4:** Link al visor de imágenes / portal web.

---

### 3️⃣ Plantilla: Rehabilitación 🏃‍♂️
Útil como recordatorio de asistencia y confirmación de turnos. (Los botones interactivos "Sí"/"No" son insertados automáticamente por Meta).

**JSON Payload:**
```json
{
  "to": "51999123456",
  "templateName": "plantilla_de_rehabilitacion",
  "languageCode": "es_PE",
  "variables": [
    "Luis Torres",
    "3",
    "10",
    "15 de Noviembre",
    "04:00 PM",
    "Lic. Ana Silva"
  ]
}
```
* **Variable 1:** Nombre del paciente.
* **Variable 2:** Número de sesión actual.
* **Variable 3:** Total de sesiones del paquete.
* **Variable 4:** Fecha programada.
* **Variable 5:** Hora programada.
* **Variable 6:** Nombre del Terapeuta.
