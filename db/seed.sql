-- ============================================================================
-- FlowOps — Seed de demonstração (Sprint 1)
-- Roda depois do flowops_ddl.sql. Cria uma empresa, um usuário de cada role
-- e uma WorkOrder de exemplo, o suficiente para testar login e o Dashboard.
-- ============================================================================

INSERT INTO companies (name, slug, email, plan)
VALUES ('Marcenaria Exemplo', 'marcenaria-exemplo', 'contato@marcenariaexemplo.com.br', 'FREE');

-- Senha para todos os usuários de demonstração: FlowOps@123
-- Hash bcrypt gerado e validado (custo 10) — ver README para instruções de regeneração.
INSERT INTO users (company_id, name, email, password_hash, role)
VALUES
    (1, 'Admin Demonstração', 'admin@flowops.dev', '$2b$10$2ETkFJSEa.tD/jwydJAz2OH6kpOF.Eaft8VQNDGZouy7dfY0377eu', 'ADMIN_EMPRESA'),
    (1, 'Operador Demonstração', 'operador@flowops.dev', '$2b$10$2ETkFJSEa.tD/jwydJAz2OH6kpOF.Eaft8VQNDGZouy7dfY0377eu', 'OPERADOR'),
    (1, 'Técnico Demonstração', 'tecnico@flowops.dev', '$2b$10$2ETkFJSEa.tD/jwydJAz2OH6kpOF.Eaft8VQNDGZouy7dfY0377eu', 'TECNICO');

INSERT INTO clients (company_id, name, email, phone)
VALUES (1, 'Cliente Demonstração', 'cliente@exemplo.com', '(44) 99999-0000');

INSERT INTO workflow_templates (company_id, name, is_default)
VALUES (1, 'Padrão Marcenaria', true);

INSERT INTO workflow_steps (workflow_template_id, step_order, title)
VALUES
    (1, 1, 'Produção'),
    (1, 2, 'Acabamento'),
    (1, 3, 'Instalação');

INSERT INTO work_orders (company_id, client_id, workflow_template_id, title, description, status, priority, created_by_id, assigned_to_id)
VALUES
    (1, 1, 1, 'Armário planejado - sala', 'Armário sob medida para sala de estar', 'EM_EXECUCAO', 'NORMAL', 1, 3),
    (1, 1, 1, 'Mesa de jantar', 'Mesa de jantar 6 lugares em madeira maciça', 'SOLICITACAO_RECEBIDA', 'ALTA', 1, NULL);

INSERT INTO work_order_steps (work_order_id, workflow_step_id, step_order, title, status, assigned_to_id)
VALUES
    (1, 1, 1, 'Produção', 'CONCLUIDA', 3),
    (1, 2, 2, 'Acabamento', 'EM_ANDAMENTO', 3),
    (1, 3, 3, 'Instalação', 'PENDENTE', NULL);
