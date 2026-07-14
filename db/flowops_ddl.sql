-- ============================================================================
-- FlowOps — DDL PostgreSQL (Etapa 2.2)
-- Escopo: tabelas do MVP (Auth, Clientes, WorkOrders, Etapas, Dashboard)
-- SGBD: PostgreSQL 15+
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- necessário para gen_random_uuid()

-- ----------------------------------------------------------------------------
-- Função utilitária: atualiza updated_at automaticamente em qualquer UPDATE
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- DOMÍNIO: IDENTITY
-- ============================================================================

CREATE TABLE companies (
    id            BIGSERIAL PRIMARY KEY,
    uuid          UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name          VARCHAR(150) NOT NULL,
    slug          VARCHAR(150) NOT NULL UNIQUE,
    cnpj          VARCHAR(18) UNIQUE,
    email         VARCHAR(150),
    phone         VARCHAR(20),
    logo_url      VARCHAR(500),
    plan          VARCHAR(20) NOT NULL DEFAULT 'FREE'
                  CHECK (plan IN ('FREE', 'BASIC', 'PRO')),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_companies_updated_at
    BEFORE UPDATE ON companies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    name            VARCHAR(150) NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(30) NOT NULL
                    CHECK (role IN ('ADMIN_EMPRESA', 'OPERADOR', 'TECNICO')),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_company_id ON users(company_id);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- DOMÍNIO: CRM
-- ============================================================================

CREATE TABLE clients (
    id            BIGSERIAL PRIMARY KEY,
    uuid          UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(150),
    phone         VARCHAR(20),
    document      VARCHAR(20),
    notes         TEXT,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ  -- soft delete
);

CREATE INDEX idx_clients_company_id ON clients(company_id);
CREATE INDEX idx_clients_company_active ON clients(company_id) WHERE deleted_at IS NULL;

-- Documento (CPF/CNPJ) e opcional, mas quando informado nao pode se repetir
-- dentro da mesma empresa. Parcial porque document costuma vir NULL no
-- cadastro inicial (nem toda empresa exige documento na hora).
CREATE UNIQUE INDEX uq_clients_company_document
    ON clients(company_id, document) WHERE document IS NOT NULL AND deleted_at IS NULL;

CREATE TRIGGER trg_clients_updated_at
    BEFORE UPDATE ON clients
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- DOMÍNIO: OPERAÇÕES (núcleo do sistema)
-- ============================================================================

CREATE TABLE workflow_templates (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    name          VARCHAR(100) NOT NULL,
    is_default    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_templates_company_id ON workflow_templates(company_id);

CREATE TRIGGER trg_workflow_templates_updated_at
    BEFORE UPDATE ON workflow_templates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- Definição das etapas-padrão de um template (molde reutilizável)
-- ----------------------------------------------------------------------------

CREATE TABLE workflow_steps (
    id                      BIGSERIAL PRIMARY KEY,
    workflow_template_id    BIGINT NOT NULL REFERENCES workflow_templates(id) ON DELETE CASCADE,
    step_order              INT NOT NULL,
    title                   VARCHAR(100) NOT NULL,

    CONSTRAINT uq_workflow_step_order UNIQUE (workflow_template_id, step_order)
);

CREATE INDEX idx_workflow_steps_template_id ON workflow_steps(workflow_template_id);

-- ----------------------------------------------------------------------------
-- WorkOrder — aggregate root do domínio (D-02)
-- ----------------------------------------------------------------------------

CREATE TABLE work_orders (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    company_id              BIGINT NOT NULL REFERENCES companies(id),
    client_id               BIGINT NOT NULL REFERENCES clients(id),
    workflow_template_id    BIGINT REFERENCES workflow_templates(id),

    title                   VARCHAR(150) NOT NULL,
    description             TEXT,

    status                  VARCHAR(30) NOT NULL DEFAULT 'SOLICITACAO_RECEBIDA'
                            CHECK (status IN (
                                'SOLICITACAO_RECEBIDA',
                                'ORCAMENTO_GERADO',
                                'AGUARDANDO_APROVACAO',
                                'APROVADO',
                                'RECUSADO',
                                'EM_EXECUCAO',
                                'ENTREGUE',
                                'FINALIZADO'
                            )),

    priority                VARCHAR(10) NOT NULL DEFAULT 'NORMAL'
                            CHECK (priority IN ('BAIXA', 'NORMAL', 'ALTA', 'URGENTE')),

    assigned_to_id          BIGINT REFERENCES users(id),
    created_by_id           BIGINT NOT NULL REFERENCES users(id),

    scheduled_start         DATE,
    scheduled_end           DATE,
    actual_start            TIMESTAMPTZ,
    actual_end              TIMESTAMPTZ,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ  -- soft delete
);

CREATE INDEX idx_work_orders_company_id ON work_orders(company_id);
CREATE INDEX idx_work_orders_client_id ON work_orders(client_id);
CREATE INDEX idx_work_orders_status ON work_orders(status);
CREATE INDEX idx_work_orders_assigned_to ON work_orders(assigned_to_id);
-- Índice parcial: acelera exatamente a query mais comum do Dashboard
CREATE INDEX idx_work_orders_company_status_active
    ON work_orders(company_id, status) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_work_orders_updated_at
    BEFORE UPDATE ON work_orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- Etapas de execução de uma WorkOrder específica (instância, não molde)
-- ----------------------------------------------------------------------------

CREATE TABLE work_order_steps (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    work_order_id       BIGINT NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    workflow_step_id    BIGINT REFERENCES workflow_steps(id),

    step_order          INT NOT NULL,
    title                VARCHAR(100) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                         CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'BLOQUEADA')),

    assigned_to_id       BIGINT REFERENCES users(id),
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    notes                TEXT,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_work_order_step_order UNIQUE (work_order_id, step_order)
);

CREATE INDEX idx_work_order_steps_work_order_id ON work_order_steps(work_order_id);
CREATE INDEX idx_work_order_steps_status ON work_order_steps(status);

CREATE TRIGGER trg_work_order_steps_updated_at
    BEFORE UPDATE ON work_order_steps
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- DOMÍNIO: COMERCIAL (Catálogo e Orçamentos) — V2.1
-- ============================================================================

CREATE TABLE catalog_items (
    id            BIGSERIAL PRIMARY KEY,
    uuid          UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    name          VARCHAR(150) NOT NULL,
    description   TEXT,
    unit_price    NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    unit          VARCHAR(20) NOT NULL DEFAULT 'UN',
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_catalog_items_company_id ON catalog_items(company_id);
CREATE INDEX idx_catalog_items_company_active ON catalog_items(company_id) WHERE is_active = TRUE;

CREATE TRIGGER trg_catalog_items_updated_at
    BEFORE UPDATE ON catalog_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- Orçamento — um por WorkOrder (ver ADR-0002: versionamento descartado para
-- o MVP). Nasce em RASCUNHO ao ser criado a partir de uma WorkOrder em
-- SOLICITACAO_RECEBIDA; itens só podem ser adicionados/removidos nesse
-- estado. APROVADO/RECUSADO refletem a decisão registrada internamente pelo
-- Operador (aprovação pública pelo Cliente é escopo do Portal, V3).
-- ----------------------------------------------------------------------------

CREATE TABLE budgets (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    work_order_id   BIGINT NOT NULL UNIQUE REFERENCES work_orders(id),

    status          VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO'
                    CHECK (status IN ('RASCUNHO', 'APROVADO', 'RECUSADO')),
    total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),

    created_by_id   BIGINT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_budgets_company_id ON budgets(company_id);

CREATE TRIGGER trg_budgets_updated_at
    BEFORE UPDATE ON budgets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ----------------------------------------------------------------------------
-- Itens do orçamento. catalog_item_id é referência opcional (item pode ser
-- avulso); description/unit_price são sempre gravados como snapshot no
-- momento da adição, para que alterações futuras no catálogo não afetem
-- orçamentos já criados.
-- ----------------------------------------------------------------------------

CREATE TABLE budget_items (
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    budget_id         BIGINT NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    catalog_item_id   BIGINT REFERENCES catalog_items(id) ON DELETE SET NULL,

    description       VARCHAR(150) NOT NULL,
    quantity          NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
    unit_price        NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    subtotal          NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_budget_items_budget_id ON budget_items(budget_id);

-- ============================================================================
-- DOMÍNIO: AUDITORIA E TIMELINE
-- ============================================================================

CREATE TABLE domain_events (
    id              BIGSERIAL PRIMARY KEY,
    work_order_id   BIGINT NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    event_type      VARCHAR(50) NOT NULL,
    actor_id        BIGINT REFERENCES users(id),
    payload         JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_domain_events_work_order_id ON domain_events(work_order_id, occurred_at);

-- ============================================================================
-- Comentários de documentação (aparecem em qualquer client de banco)
-- ============================================================================

COMMENT ON TABLE  work_orders IS 'Aggregate root do domínio FlowOps (decisão D-02). Representa qualquer trabalho executado sob demanda.';
COMMENT ON COLUMN work_orders.status IS 'State machine: transições validadas na camada Service do Spring Boot, nunca via UPDATE direto.';
COMMENT ON TABLE  domain_events IS 'Timeline de eventos de negócio de uma WorkOrder, usada para histórico e auditoria.';
COMMENT ON TABLE  companies IS 'Tenant lógico (D-06). Todas as tabelas operacionais referenciam company_id para isolamento de dados.';
COMMENT ON TABLE  budgets IS 'Orçamento comercial de uma WorkOrder (V2.1). Um por WorkOrder — ver ADR-0002.';

-- ============================================================================
-- Fim do script — V2.1 (Orçamentos e Catálogo) concluída
-- Fora deste escopo (documentado em Roadmap e Entrega, não implementado ainda):
-- requests, payments, evidences, notifications
-- ============================================================================
