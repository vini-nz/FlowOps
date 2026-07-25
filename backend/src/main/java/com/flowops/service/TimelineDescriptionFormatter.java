package com.flowops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Traduz (eventType, payload) de domain_events em uma frase legivel.
 * <p>
 * Unico ponto do sistema que interpreta o payload: o contrato por tipo de
 * evento esta documentado em docs/architecture.md, e manter a leitura
 * concentrada aqui evita que cada consumidor (Timeline hoje, Notificacoes
 * depois) reimplemente sua propria versao e divirjam com o tempo.
 * <p>
 * Um tipo de evento desconhecido, ou um payload que nao tem o formato
 * esperado, cai no fallback (o proprio eventType) em vez de quebrar a
 * Timeline inteira - evento de auditoria antigo nunca deve derrubar a tela.
 */
@Component
@RequiredArgsConstructor
public class TimelineDescriptionFormatter {

    private static final Logger log = LoggerFactory.getLogger(TimelineDescriptionFormatter.class);

    private final ObjectMapper objectMapper;

    public String describe(String eventType, String payload) {
        JsonNode node = parse(payload);

        return switch (eventType) {
            case "WORKORDER_CRIADA" -> "Ordem de serviço criada";
            case "STATUS_ALTERADO" -> node == null
                    ? "Status alterado"
                    : "Status alterado de %s para %s".formatted(
                            humanize(node.path("de").asText()), humanize(node.path("para").asText()));
            case "RESPONSAVEL_ATRIBUIDO" -> {
                String assignedTo = node != null ? node.path("assignedTo").asText(null) : null;
                yield StringUtils.hasText(assignedTo)
                        ? "Responsável atribuído: " + assignedTo
                        : "Responsável removido";
            }
            case "ETAPA_STATUS_ALTERADA" -> node == null
                    ? "Etapa atualizada"
                    : "Etapa \"%s\": %s → %s".formatted(
                            node.path("etapa").asText(), humanize(node.path("de").asText()),
                            humanize(node.path("para").asText()));
            case "ETAPA_OBSERVACAO_REGISTRADA" -> node == null
                    ? "Observação registrada em uma etapa"
                    : "Observação registrada na etapa \"%s\"".formatted(node.path("etapa").asText());
            case "ORCAMENTO_CRIADO" -> "Orçamento criado";
            case "ITEM_ADICIONADO" -> node == null
                    ? "Item adicionado ao orçamento"
                    : "Item adicionado ao orçamento: %s".formatted(node.path("description").asText());
            case "ITEM_REMOVIDO" -> node == null
                    ? "Item removido do orçamento"
                    : "Item removido do orçamento: %s".formatted(node.path("description").asText());
            case "ORCAMENTO_APROVADO" -> "Orçamento aprovado";
            case "ORCAMENTO_RECUSADO" -> "Orçamento recusado";
            default -> eventType;
        };
    }

    private JsonNode parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Payload de domain_event nao pode ser lido como JSON: {}", payload, e);
            return null;
        }
    }

    // Enums do dominio nao tem acento (SOLICITACAO_RECEBIDA), mas o texto
    // mostrado ao usuario precisa ter - e precisa bater com os mesmos rotulos
    // que os badges da tela ja exibem, senao a Timeline diz "Solicitacao
    // recebida" ao lado de um badge escrito "Solicitação recebida".
    private static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry("SOLICITACAO_RECEBIDA", "Solicitação recebida"),
            Map.entry("ORCAMENTO_GERADO", "Orçamento gerado"),
            Map.entry("AGUARDANDO_APROVACAO", "Aguardando aprovação"),
            Map.entry("APROVADO", "Aprovado"),
            Map.entry("RECUSADO", "Recusado"),
            Map.entry("EM_EXECUCAO", "Em execução"),
            Map.entry("ENTREGUE", "Entregue"),
            Map.entry("FINALIZADO", "Finalizado"),
            Map.entry("PENDENTE", "Pendente"),
            Map.entry("EM_ANDAMENTO", "Em andamento"),
            Map.entry("CONCLUIDA", "Concluída"),
            Map.entry("BLOQUEADA", "Bloqueada"));

    private String humanize(String enumValue) {
        if (!StringUtils.hasText(enumValue)) {
            return "";
        }
        // Fallback para um enum novo ainda nao mapeado: melhor "Algum status"
        // do que o valor cru em caixa alta no meio da frase.
        return STATUS_LABELS.getOrDefault(enumValue, defaultLabel(enumValue));
    }

    private String defaultLabel(String enumValue) {
        String lower = enumValue.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
