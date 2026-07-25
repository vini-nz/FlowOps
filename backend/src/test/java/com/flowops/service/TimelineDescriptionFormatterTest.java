package com.flowops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O contrato de domain_events.payload por tipo de evento esta documentado em
 * docs/architecture.md; estes testes sao a validacao executavel desse
 * contrato - se um Service mudar o formato do payload sem atualizar o
 * formatter, quebra aqui e nao silenciosamente na tela do usuario.
 */
class TimelineDescriptionFormatterTest {

    private final TimelineDescriptionFormatter formatter =
            new TimelineDescriptionFormatter(new ObjectMapper());

    @Test
    void describesWorkOrderCreated() {
        assertThat(formatter.describe("WORKORDER_CRIADA", null))
                .isEqualTo("Ordem de serviço criada");
    }

    @Test
    void describesStatusChangeWithHumanizedEnums() {
        String description = formatter.describe(
                "STATUS_ALTERADO", "{\"de\":\"SOLICITACAO_RECEBIDA\",\"para\":\"ORCAMENTO_GERADO\"}");

        // Acentuacao identica a dos badges da tela (STATUS_LABELS no frontend)
        assertThat(description).isEqualTo("Status alterado de Solicitação recebida para Orçamento gerado");
    }

    @Test
    void unmappedStatusFallsBackToGenericLabelInsteadOfRawEnum() {
        String description = formatter.describe(
                "STATUS_ALTERADO", "{\"de\":\"APROVADO\",\"para\":\"STATUS_FUTURO_QUALQUER\"}");

        assertThat(description).isEqualTo("Status alterado de Aprovado para Status futuro qualquer");
    }

    @Test
    void describesAssigneeAssignedAndRemoved() {
        assertThat(formatter.describe("RESPONSAVEL_ATRIBUIDO", "{\"assignedTo\":\"Maria\"}"))
                .isEqualTo("Responsável atribuído: Maria");
        assertThat(formatter.describe("RESPONSAVEL_ATRIBUIDO", "{\"assignedTo\":null}"))
                .isEqualTo("Responsável removido");
    }

    @Test
    void describesStepEvents() {
        assertThat(formatter.describe("ETAPA_STATUS_ALTERADA",
                "{\"etapa\":\"Produção\",\"de\":\"PENDENTE\",\"para\":\"CONCLUIDA\"}"))
                .isEqualTo("Etapa \"Produção\": Pendente → Concluída");

        assertThat(formatter.describe("ETAPA_OBSERVACAO_REGISTRADA", "{\"etapa\":\"Acabamento\"}"))
                .isEqualTo("Observação registrada na etapa \"Acabamento\"");
    }

    @Test
    void describesBudgetEvents() {
        assertThat(formatter.describe("ORCAMENTO_CRIADO", null)).isEqualTo("Orçamento criado");
        assertThat(formatter.describe("ITEM_ADICIONADO", "{\"description\":\"Hora tecnica\",\"subtotal\":300.00}"))
                .isEqualTo("Item adicionado ao orçamento: Hora tecnica");
        assertThat(formatter.describe("ITEM_REMOVIDO", "{\"description\":\"Material extra\"}"))
                .isEqualTo("Item removido do orçamento: Material extra");
        assertThat(formatter.describe("ORCAMENTO_APROVADO", null)).isEqualTo("Orçamento aprovado");
        assertThat(formatter.describe("ORCAMENTO_RECUSADO", null)).isEqualTo("Orçamento recusado");
    }

    @Test
    void unknownEventTypeFallsBackToRawType() {
        assertThat(formatter.describe("EVENTO_DE_UMA_VERSAO_FUTURA", "{\"algo\":1}"))
                .isEqualTo("EVENTO_DE_UMA_VERSAO_FUTURA");
    }

    @Test
    void malformedPayloadDoesNotBreakTimeline() {
        // Evento antigo/corrompido nao pode derrubar a tela inteira - cai no
        // texto generico do tipo, sem excecao.
        assertThat(formatter.describe("STATUS_ALTERADO", "isso nao e json"))
                .isEqualTo("Status alterado");
        assertThat(formatter.describe("ITEM_ADICIONADO", "{quebrado"))
                .isEqualTo("Item adicionado ao orçamento");
    }
}
