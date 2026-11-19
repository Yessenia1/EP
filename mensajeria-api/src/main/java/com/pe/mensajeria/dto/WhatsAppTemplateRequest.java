package com.pe.mensajeria.dto;

import java.util.List;

public class WhatsAppTemplateRequest {
    private String to;
    private String templateName;
    private String languageCode = "es_PE";
    private List<String> variables;

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public List<String> getVariables() {
        return variables;
    }

    public void setVariables(List<String> variables) {
        this.variables = variables;
    }
}
