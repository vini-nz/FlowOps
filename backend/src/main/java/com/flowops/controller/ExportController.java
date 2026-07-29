package com.flowops.controller;

import com.flowops.entity.User;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Exportação das listas em CSV (V2.9).
 * <p>
 * Sem restrição adicional por papel: a exportação devolve exatamente os
 * mesmos dados que a listagem correspondente, que já é aberta a qualquer
 * usuário autenticado da empresa. Restringir só aqui seria teatro — quem
 * quisesse os dados bastaria percorrer as páginas da tela.
 */
@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/work-orders")
    public ResponseEntity<byte[]> workOrders(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) WorkOrderStatus status) {
        byte[] csv = exportService.exportWorkOrders(user.getCompany().getId(), status);
        return csvResponse(csv, "ordens-de-servico");
    }

    @GetMapping("/clients")
    public ResponseEntity<byte[]> clients(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String search) {
        byte[] csv = exportService.exportClients(user.getCompany().getId(), search);
        return csvResponse(csv, "clientes");
    }

    private ResponseEntity<byte[]> csvResponse(byte[] csv, String prefix) {
        String fileName = "%s-%s.csv".formatted(prefix, LocalDate.now());

        return ResponseEntity.ok()
                // charset=UTF-8 no Content-Type junto com o BOM que o CsvWriter
                // escreve: os dois sinalizam a codificacao, e o Excel usa o BOM.
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(csv);
    }
}
