package com.flowops.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O que este teste protege não é o formato CSV em si, mas as três coisas que
 * fazem o arquivo abrir certo no Excel em português (V2.9). Todas são
 * invisíveis numa inspeção casual do conteúdo e só aparecem ao abrir o
 * arquivo — daí valer um teste.
 */
class CsvWriterTest {

    @Test
    void startsWithUtf8BomSoExcelDoesNotMangleAccents() {
        CsvWriter csv = new CsvWriter(List.of("Título"));
        csv.row(List.of("Instalação"));

        byte[] bytes = csv.toBytes();

        // EF BB BF = BOM UTF-8. Sem ele o Excel le como codificacao local e
        // "Instalação" vira "InstalaÃ§Ã£o".
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("Instalação");
    }

    @Test
    void usesSemicolonSeparatorExpectedByPtBrExcel() {
        CsvWriter csv = new CsvWriter(List.of("A", "B", "C"));
        csv.row(List.of("1", "2", "3"));

        String content = new String(csv.toBytes(), StandardCharsets.UTF_8);

        // Com virgula, o Excel pt-BR joga a planilha inteira numa coluna so.
        assertThat(content).contains("A;B;C").contains("1;2;3");
    }

    @Test
    void decimalUsesCommaSoExcelTreatsItAsNumber() {
        assertThat(CsvWriter.decimal(new BigDecimal("1234.56"))).isEqualTo("1234,56");
        assertThat(CsvWriter.decimal(null)).isEmpty();
    }

    @Test
    void quotesFieldsContainingTheSeparator() {
        CsvWriter csv = new CsvWriter(List.of("Observação"));
        csv.row(List.of("Entregar; conferir medidas"));

        String content = new String(csv.toBytes(), StandardCharsets.UTF_8);

        // Sem aspas, o ponto-e-virgula no meio do texto deslocaria todas as
        // colunas seguintes daquela linha.
        assertThat(content).contains("\"Entregar; conferir medidas\"");
    }

    @Test
    void doublesInternalQuotes() {
        CsvWriter csv = new CsvWriter(List.of("Item"));
        csv.row(List.of("Porta 30\" reforçada"));

        assertThat(new String(csv.toBytes(), StandardCharsets.UTF_8))
                .contains("\"Porta 30\"\" reforçada\"");
    }

    @Test
    void quotesFieldsWithLineBreaks() {
        CsvWriter csv = new CsvWriter(List.of("Notas"));
        csv.row(List.of("linha 1\nlinha 2"));

        String content = new String(csv.toBytes(), StandardCharsets.UTF_8);

        assertThat(content).contains("\"linha 1\nlinha 2\"");
    }

    @Test
    void formatsDatesInBrazilianOrder() {
        assertThat(CsvWriter.date(LocalDate.of(2026, 7, 28))).isEqualTo("28/07/2026");
        assertThat(CsvWriter.date(null)).isEmpty();
    }

    @Test
    void nullTextBecomesEmptyCellNotTheWordNull() {
        assertThat(CsvWriter.text(null)).isEmpty();

        CsvWriter csv = new CsvWriter(List.of("A", "B"));
        csv.row(List.of(CsvWriter.text(null), "valor"));

        assertThat(new String(csv.toBytes(), StandardCharsets.UTF_8))
                .contains(";valor")
                .doesNotContain("null");
    }

    @Test
    void usesCrlfLineEndings() {
        CsvWriter csv = new CsvWriter(List.of("A"));
        csv.row(List.of("1"));

        assertThat(new String(csv.toBytes(), StandardCharsets.UTF_8)).contains("A\r\n1\r\n");
    }
}
