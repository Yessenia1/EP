package com.pe.mensajeria.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pe.mensajeria.dto.WhatsAppTemplateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WhatsappService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${whatsapp.api.url}")
    private String apiUrl;

    @Value("${whatsapp.api.token}")
    private String apiToken;

    public void enviarMensaje(String numeroDestino, String textoMensaje) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", numeroDestino);
        body.put("type", "text");
        body.put("text", Map.of("preview_url", false, "body", textoMensaje));
        publicar(body);
        log.info("Mensaje de texto enviado a {}", numeroDestino);
    }

    public String sendTemplate(WhatsAppTemplateRequest request) {
        return publicar(plantilla(request.getTo(), request.getTemplateName(), request.getLanguageCode(), request.getVariables(), null));
    }

    public String enviarPlantilla(String numero, String templateName, String languageCode, List<String> parametros) {
        return publicar(plantilla(numero, templateName, languageCode, parametros, null));
    }

    public String enviarPlantillaConDocumento(String numero, String templateName, String languageCode, List<String> parametros,
                                              byte[] contenido, String nombreArchivo) {
        String mediaId = subirDocumento(contenido, nombreArchivo);
        Map<String, Object> documento = Map.of("type", "document", "document", Map.of("id", mediaId, "filename", nombreArchivo));
        String respuesta = publicar(plantilla(numero, templateName, languageCode, parametros, documento));
        log.info("Plantilla {} con documento {} enviada a {}", templateName, nombreArchivo, numero);
        return respuesta;
    }

    private String subirDocumento(byte[] contenido, String nombreArchivo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(apiToken);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("messaging_product", "whatsapp");
        form.add("type", "application/pdf");
        form.add("file", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return nombreArchivo;
            }
        });
        try {
            String respuesta = restTemplate.postForObject(apiUrl.replace("/messages", "/media"), new HttpEntity<>(form, headers), String.class);
            JsonNode json = objectMapper.readTree(respuesta);
            return json.get("id").asText();
        } catch (Exception e) {
            throw new RuntimeException("Error al subir el documento a WhatsApp: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> plantilla(String numero, String nombre, String idioma, List<String> parametros, Map<String, Object> documento) {
        Map<String, Object> template = new HashMap<>();
        template.put("name", nombre);
        template.put("language", Map.of("code", idioma != null ? idioma : "es_PE"));
        List<Map<String, Object>> components = new ArrayList<>();
        if (documento != null) {
            components.add(Map.of("type", "header", "parameters", List.of(documento)));
        }
        if (parametros != null && !parametros.isEmpty()) {
            List<Map<String, Object>> valores = new ArrayList<>();
            for (String valor : parametros) {
                valores.add(Map.of("type", "text", "text", valor));
            }
            components.add(Map.of("type", "body", "parameters", valores));
        }
        if (!components.isEmpty()) {
            template.put("components", components);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", numero);
        body.put("type", "template");
        body.put("template", template);
        return body;
    }

    private String publicar(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);
        try {
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error al enviar WhatsApp API Meta: " + e.getMessage(), e);
        }
    }
}
