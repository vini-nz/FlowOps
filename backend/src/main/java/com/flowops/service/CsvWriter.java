package com.flowops.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Monta CSV que o Excel em português abre corretamente (V2.9).
 * <p>
 * Um CSV "cru" (vírgula, UTF-8 sem BOM, ponto decimal) até é um CSV válido,
 * mas para o usuário final brasileiro ele chega quebrado de três formas:
 * <ul>
 *   <li><b>BOM UTF-8</b> — sem ele o Excel assume a codificação local e
 *       "Instalação" vira "InstalaÃ§Ã£o".</li>
 *   <li><b>Separador ponto-e-vírgula</b> — o Excel pt-BR usa a vírgula como
 *       separador decimal, então espera {@code ;} entre colunas. Com vírgula,
 *       a planilha inteira cai numa coluna só.</li>
 *   <li><b>Vírgula decimal</b> — {@code 1234.56} não é reconhecido como
 *       número, e a coluna deixa de somar.</li>
 * </ul>
 * Nada disso aparece em teste automatizado de conteúdo; só abrindo o arquivo.
 */
public final class CsvWriter {

    private static final char SEPARATOR = ';';
    private static final String LINE_END = "\r\n"; // CRLF: o que o Excel espera
    private static final String BOM = "﻿";

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StringBuilder content = new StringBuilder(BOM);

    public CsvWriter(List<String> headers) {
        row(headers);
    }

    public void row(List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                content.append(SEPARATOR);
            }
            content.append(escape(values.get(i)));
        }
        content.append(LINE_END);
    }

    public byte[] toBytes() {
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---- formatadores usados por quem monta as linhas ----

    public static String text(String value) {
        return value != null ? value : "";
    }

    public static String dateTime(OffsetDateTime value) {
        return value != null ? DATE_TIME.format(value) : "";
    }

    public static String date(LocalDate value) {
        return value != null ? DATE.format(value) : "";
    }

    /** Vírgula decimal, senão o Excel pt-BR trata o valor como texto. */
    public static String decimal(BigDecimal value) {
        return value != null ? value.toPlainString().replace('.', ',') : "";
    }

    public static String bool(boolean value) {
        return value ? "Sim" : "Não";
    }

    /**
     * Regra do RFC 4180 adaptada ao separador: só entra entre aspas o campo
     * que contém separador, aspas ou quebra de linha — e aspas internas são
     * duplicadas. Sem isso, uma observação com ponto-e-vírgula no meio
     * deslocaria todas as colunas seguintes daquela linha.
     */
    private String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuotes = value.indexOf(SEPARATOR) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;

        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
