package com.pe.mensajeria.dto;

import lombok.Data;

import java.util.List;

@Data
public class WhatsappRequest {
    private String numero;
    private String mensaje;
    private String templateName;
    private String languageCode = "es_PE";
    private List<String> templateParams;
}
