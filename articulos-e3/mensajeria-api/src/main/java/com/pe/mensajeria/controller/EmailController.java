package com.pe.mensajeria.controller;

import com.pe.mensajeria.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/email")
@CrossOrigin("*")
public class EmailController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/send-with-attachment")
    public ResponseEntity<String> sendEmailWithAttachment(
            @RequestParam("to") String to,
            @RequestParam("subject") String subject,
            @RequestParam("body") String body,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            emailService.sendEmailWithAttachment(to, subject, body, file);
            return ResponseEntity.ok("{\"message\": \"Email enviado correctamente a " + to + "\", \"success\": true}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"message\": \"Error al enviar email: " + e.getMessage() + "\", \"success\": false}");
        }
    }
}
