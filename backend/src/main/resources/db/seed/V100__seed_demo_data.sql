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

-- Este molde é apenas um exemplo de marcenaria: desde a V2.5 o Admin Empresa
-- pode renomear, reordenar, remover e criar suas próprias etapas e checklists
-- pela tela de Workflow — nada aqui é fixo no sistema.
INSERT INTO workflow_steps (workflow_template_id, step_order, title)
VALUES
    (1, 1, 'Produção'),
    (1, 2, 'Acabamento'),
    (1, 3, 'Instalação');

INSERT INTO workflow_step_checklist_items (workflow_step_id, item_order, description)
VALUES
    (1, 1, 'Conferir medidas com o projeto'),
    (1, 2, 'Separar material necessário'),
    (2, 1, 'Lixar superfícies'),
    (2, 2, 'Aplicar acabamento'),
    (3, 1, 'Conferir nivelamento'),
    (3, 2, 'Entregar manual de conservação ao cliente');

INSERT INTO work_orders (company_id, client_id, workflow_template_id, title, description, status, priority, created_by_id, assigned_to_id)
VALUES
    (1, 1, 1, 'Armário planejado - sala', 'Armário sob medida para sala de estar', 'EM_EXECUCAO', 'NORMAL', 1, 3),
    (1, 1, 1, 'Mesa de jantar', 'Mesa de jantar 6 lugares em madeira maciça', 'SOLICITACAO_RECEBIDA', 'ALTA', 1, NULL);

INSERT INTO work_order_steps (work_order_id, workflow_step_id, step_order, title, status, assigned_to_id)
VALUES
    (1, 1, 1, 'Produção', 'CONCLUIDA', 3),
    (1, 2, 2, 'Acabamento', 'EM_ANDAMENTO', 3),
    (1, 3, 3, 'Instalação', 'PENDENTE', NULL);

-- Cópia do checklist do molde para esta OS (é o que WorkOrderService faz na
-- criação). A etapa concluída já vem com os itens marcados.
INSERT INTO work_order_step_checklist_items
    (work_order_step_id, workflow_checklist_item_id, item_order, description, is_done, done_at, done_by_id)
VALUES
    (1, 1, 1, 'Conferir medidas com o projeto', true, now(), 3),
    (1, 2, 2, 'Separar material necessário', true, now(), 3),
    (2, 3, 1, 'Lixar superfícies', true, now(), 3),
    (2, 4, 2, 'Aplicar acabamento', false, NULL, NULL),
    (3, 5, 1, 'Conferir nivelamento', false, NULL, NULL),
    (3, 6, 2, 'Entregar manual de conservação ao cliente', false, NULL, NULL);
