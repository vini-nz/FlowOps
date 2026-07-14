package com.flowops.service;

import com.flowops.entity.Budget;
import com.flowops.entity.BudgetItem;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Gera o PDF do orcamento (V2.2 - Backlog Detalhado, item 2). Isolado do
 * BudgetService de proposito: montagem de documento e uma responsabilidade
 * bem diferente de regra de negocio, e OpenPDF (com.lowagie.text) nao deveria
 * vazar para o resto da camada de servico.
 */
@Service
public class BudgetPdfService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    public byte[] generate(Budget budget, List<BudgetItem> items) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font companyFont = new Font(Font.HELVETICA, 10, Font.NORMAL, java.awt.Color.GRAY);
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, java.awt.Color.WHITE);

            document.add(new Paragraph(budget.getCompany().getName(), companyFont));
            document.add(new Paragraph("Orçamento", titleFont));
            document.add(new Paragraph(" "));

            document.add(labeledLine("Cliente:", budget.getWorkOrder().getClient().getName(), labelFont, normalFont));
            document.add(labeledLine("Ordem de Serviço:", budget.getWorkOrder().getTitle(), labelFont, normalFont));
            document.add(labeledLine("Status:", statusLabel(budget), labelFont, normalFont));
            document.add(labeledLine("Emitido em:", DATE_FORMAT.format(budget.getCreatedAt()), labelFont, normalFont));
            if (budget.getDecidedAt() != null) {
                document.add(labeledLine(
                        budget.getStatus().name().equals("APROVADO") ? "Aprovado em:" : "Recusado em:",
                        "%s por %s".formatted(DATE_FORMAT.format(budget.getDecidedAt()), budget.getDecidedBy().getName()),
                        labelFont, normalFont));
            }
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[]{4f, 1f, 1.3f, 1.3f});
            table.setWidthPercentage(100);

            addHeaderCell(table, "Descrição", headerFont);
            addHeaderCell(table, "Qtd.", headerFont);
            addHeaderCell(table, "Valor unit.", headerFont);
            addHeaderCell(table, "Subtotal", headerFont);

            for (BudgetItem item : items) {
                table.addCell(new PdfPCell(new com.lowagie.text.Phrase(item.getDescription(), normalFont)));
                table.addCell(numericCell(item.getQuantity().stripTrailingZeros().toPlainString(), normalFont));
                table.addCell(numericCell(currency(item.getUnitPrice()), normalFont));
                table.addCell(numericCell(currency(item.getSubtotal()), normalFont));
            }
            document.add(table);
            document.add(new Paragraph(" "));

            Paragraph total = new Paragraph("Total: " + currency(budget.getTotalAmount()), titleFont);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar PDF do orçamento", e);
        } finally {
            document.close();
        }

        return out.toByteArray();
    }

    private String statusLabel(Budget budget) {
        return switch (budget.getStatus()) {
            case RASCUNHO -> "Rascunho";
            case APROVADO -> "Aprovado";
            case RECUSADO -> "Recusado";
        };
    }

    private String currency(BigDecimal value) {
        return "R$ " + String.format(Locale.of("pt", "BR"), "%,.2f", value);
    }

    private Paragraph labeledLine(String label, String value, Font labelFont, Font normalFont) {
        Paragraph p = new Paragraph();
        p.add(new com.lowagie.text.Chunk(label + " ", labelFont));
        p.add(new com.lowagie.text.Chunk(value, normalFont));
        return p;
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new com.lowagie.text.Phrase(text, font));
        cell.setBackgroundColor(new java.awt.Color(30, 41, 59));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private PdfPCell numericCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new com.lowagie.text.Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setPadding(5);
        return cell;
    }
}
