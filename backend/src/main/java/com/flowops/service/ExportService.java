package com.flowops.service;

import com.flowops.entity.Budget;
import com.flowops.entity.Client;
import com.flowops.entity.WorkOrder;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.repository.BudgetRepository;
import com.flowops.repository.ClientRepository;
import com.flowops.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exportação das listas principais em CSV (V2.9 — item "Exportação CSV/Excel
 * simples" do Épico Experiência, antecipado para a V2).
 * <p>
 * Reaproveita as mesmas consultas da tela, com os mesmos filtros: uma
 * exportação que ignorasse o filtro aplicado devolveria uma planilha que não
 * corresponde ao que o usuário está vendo.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    /**
     * Teto de linhas por exportação. Exportação é síncrona e monta o arquivo
     * em memória; sem limite, uma empresa grande derrubaria o backend com um
     * clique. Se o volume real passar disso, o caminho é geração assíncrona
     * (fila) — item já previsto na V4.
     */
    private static final int MAX_ROWS = 5000;

    private final WorkOrderRepository workOrderRepository;
    private final ClientRepository clientRepository;
    private final BudgetRepository budgetRepository;

    @Transactional(readOnly = true)
    public byte[] exportWorkOrders(Long companyId, WorkOrderStatus status) {
        var pageable = PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<WorkOrder> page = status != null
                ? workOrderRepository.findByCompanyIdAndStatusAndDeletedAtIsNull(companyId, status, pageable)
                : workOrderRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable);

        List<WorkOrder> workOrders = page.getContent();
        Map<Long, Budget> budgetsByWorkOrder = loadBudgets(workOrders);

        CsvWriter csv = new CsvWriter(List.of(
                "Título", "Cliente", "Status", "Prioridade", "Responsável", "Criada por",
                "Início previsto", "Fim previsto", "Valor do orçamento", "Situação do orçamento",
                "Criada em"));

        for (WorkOrder wo : workOrders) {
            Budget budget = budgetsByWorkOrder.get(wo.getId());
            csv.row(List.of(
                    CsvWriter.text(wo.getTitle()),
                    CsvWriter.text(wo.getClient().getName()),
                    label(wo.getStatus()),
                    CsvWriter.text(wo.getPriority().name()),
                    CsvWriter.text(wo.getAssignedTo() != null ? wo.getAssignedTo().getName() : null),
                    CsvWriter.text(wo.getCreatedBy().getName()),
                    CsvWriter.date(wo.getScheduledStart()),
                    CsvWriter.date(wo.getScheduledEnd()),
                    CsvWriter.decimal(budget != null ? budget.getTotalAmount() : BigDecimal.ZERO),
                    CsvWriter.text(budget != null ? budget.getStatus().name() : "SEM ORÇAMENTO"),
                    CsvWriter.dateTime(wo.getCreatedAt())));
        }

        return csv.toBytes();
    }

    @Transactional(readOnly = true)
    public byte[] exportClients(Long companyId, String search) {
        var pageable = PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.ASC, "name"));

        Page<Client> page = StringUtils.hasText(search)
                ? clientRepository.findByCompanyIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                        companyId, search, pageable)
                : clientRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable);

        CsvWriter csv = new CsvWriter(List.of(
                "Nome", "E-mail", "Telefone", "Documento", "Ativo", "Observações", "Cadastrado em"));

        for (Client client : page.getContent()) {
            csv.row(List.of(
                    CsvWriter.text(client.getName()),
                    CsvWriter.text(client.getEmail()),
                    CsvWriter.text(client.getPhone()),
                    CsvWriter.text(client.getDocument()),
                    CsvWriter.bool(client.isActive()),
                    CsvWriter.text(client.getNotes()),
                    CsvWriter.dateTime(client.getCreatedAt())));
        }

        return csv.toBytes();
    }

    // Uma query para o lote inteiro em vez de uma por WorkOrder.
    private Map<Long, Budget> loadBudgets(List<WorkOrder> workOrders) {
        if (workOrders.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = workOrders.stream().map(WorkOrder::getId).toList();
        return budgetRepository.findByWorkOrderIdIn(ids).stream()
                .collect(Collectors.toMap(b -> b.getWorkOrder().getId(), Function.identity()));
    }

    // Mesmo rotulo que a tela mostra: uma planilha escrita em SOLICITACAO_RECEBIDA
    // obrigaria o usuario a traduzir mentalmente o que ele ja le em portugues.
    private String label(WorkOrderStatus status) {
        return switch (status) {
            case SOLICITACAO_RECEBIDA -> "Solicitação recebida";
            case ORCAMENTO_GERADO -> "Orçamento gerado";
            case AGUARDANDO_APROVACAO -> "Aguardando aprovação";
            case APROVADO -> "Aprovado";
            case RECUSADO -> "Recusado";
            case EM_EXECUCAO -> "Em execução";
            case ENTREGUE -> "Entregue";
            case FINALIZADO -> "Finalizado";
        };
    }
}
