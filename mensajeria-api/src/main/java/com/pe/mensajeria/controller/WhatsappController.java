package com.pe.mensajeria.controller;

import com.pe.mensajeria.dto.WhatsAppTemplateRequest;
import com.pe.mensajeria.dto.WhatsappRequest;
import com.pe.mensajeria.service.WhatsappService;
import com.pe.mensajeria.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mensajes/whatsapp")
@RequiredArgsConstructor
public class WhatsappController {

    private final WhatsappService whatsappService;

    @PostMapping("/enviar")
    public ResponseEntity<ApiResponse<String>> enviarWhatsapp(@RequestBody WhatsappRequest request) {
        try {
            if (request.getTemplateName() != null && !request.getTemplateName().isBlank()) {
                whatsappService.enviarPlantilla(request.getNumero(), request.getTemplateName(), request.getLanguageCode(), request.getTemplateParams());
            } else {
                whatsappService.enviarMensaje(request.getNumero(), request.getMensaje());
            }
            return ResponseEntity.ok(ApiResponse.success("Mensaje de WhatsApp enviado correctamente", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Error al enviar el mensaje por WhatsApp: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/enviar-con-adjunto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> enviarConAdjunto(
            @RequestParam("numero") String numero,
            @RequestParam("templateName") String templateName,
            @RequestParam(value = "templateParams", required = false) List<String> templateParams,
            @RequestParam(value = "languageCode", defaultValue = "es_PE") String languageCode,
            @RequestParam("file") MultipartFile file) {
        try {
            String nombre = file.getOriginalFilename() != null ? file.getOriginalFilename() : "comprobante.pdf";
            String resultado = whatsappService.enviarPlantillaConDocumento(numero, templateName, languageCode, templateParams, file.getBytes(), nombre);
            return ResponseEntity.ok(ApiResponse.success("Plantilla con documento enviada correctamente", resultado));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Error al enviar la plantilla con documento: " + e.getMessage()));
        }
    }

    @PostMapping("/send-template")
    public ResponseEntity<ApiResponse<String>> sendTemplate(@RequestBody WhatsAppTemplateRequest request) {
        try {
            String result = whatsappService.sendTemplate(request);
            return ResponseEntity.ok(ApiResponse.success("Plantilla de WhatsApp enviada correctamente", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Error al enviar la plantilla: " + e.getMessage()));
        }
    }
}
