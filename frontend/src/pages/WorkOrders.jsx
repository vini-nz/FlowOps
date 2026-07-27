import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api.js'

const emptyForm = {
  clientUuid: '', title: '', description: '', priority: 'NORMAL',
  scheduledStart: '', scheduledEnd: '', assignedToUuid: ''
}

const STATUS_LABELS = {
  SOLICITACAO_RECEBIDA: 'Solicitação recebida',
  ORCAMENTO_GERADO: 'Orçamento gerado',
  AGUARDANDO_APROVACAO: 'Aguardando aprovação',
  APROVADO: 'Aprovado',
  RECUSADO: 'Recusado',
  EM_EXECUCAO: 'Em execução',
  ENTREGUE: 'Entregue',
  FINALIZADO: 'Finalizado'
}

const STATUS_COLORS = {
  SOLICITACAO_RECEBIDA: 'bg-gray-100 text-gray-700',
  ORCAMENTO_GERADO: 'bg-blue-100 text-blue-700',
  AGUARDANDO_APROVACAO: 'bg-amber-100 text-amber-700',
  APROVADO: 'bg-emerald-100 text-emerald-700',
  RECUSADO: 'bg-red-100 text-red-700',
  EM_EXECUCAO: 'bg-indigo-100 text-indigo-700',
  ENTREGUE: 'bg-teal-100 text-teal-700',
  FINALIZADO: 'bg-flowops-900 text-white'
}

// Espelha WorkOrderStatusTransitions.MANUAL (V2.4 / ADR-0003): so as
// transicoes que o usuario dispara diretamente. A fase comercial saiu daqui
// de proposito - ORCAMENTO_GERADO/APROVADO/RECUSADO sao consequencia de
// acoes no orcamento, nao botoes. A validacao de verdade acontece no
// backend; se as duas divergirem, o backend responde 409 com o motivo.
const NEXT_STATUSES = {
  SOLICITACAO_RECEBIDA: [],
  ORCAMENTO_GERADO: [],
  AGUARDANDO_APROVACAO: [],
  APROVADO: ['EM_EXECUCAO'],
  EM_EXECUCAO: ['ENTREGUE'],
  ENTREGUE: ['FINALIZADO'],
  RECUSADO: [],
  FINALIZADO: []
}

// Por que nao ha botao de avanco neste status - evita que a tela pareca
// travada sem explicacao agora que a fase comercial nao e mais manual.
const STATUS_HINTS = {
  SOLICITACAO_RECEBIDA: 'Crie o orçamento para avançar',
  ORCAMENTO_GERADO: 'Registre a aprovação ou recusa do orçamento para avançar',
  AGUARDANDO_APROVACAO: 'Registre a aprovação ou recusa do orçamento para avançar'
}

const STEP_STATUS_LABELS = {
  PENDENTE: 'Pendente',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  BLOQUEADA: 'Bloqueada'
}

const STEP_STATUS_COLORS = {
  PENDENTE: 'bg-gray-100 text-gray-700',
  EM_ANDAMENTO: 'bg-indigo-100 text-indigo-700',
  CONCLUIDA: 'bg-emerald-100 text-emerald-700',
  BLOQUEADA: 'bg-red-100 text-red-700'
}

// Espelha StepStatusTransitions.java (Sprint 4) - mesma ressalva do
// NEXT_STATUSES acima: quem valida de verdade e o backend.
const STEP_NEXT_STATUSES = {
  PENDENTE: ['EM_ANDAMENTO', 'BLOQUEADA'],
  EM_ANDAMENTO: ['CONCLUIDA', 'BLOQUEADA'],
  BLOQUEADA: ['PENDENTE', 'EM_ANDAMENTO'],
  CONCLUIDA: []
}

const BUDGET_STATUS_LABELS = {
  RASCUNHO: 'Rascunho',
  APROVADO: 'Aprovado',
  RECUSADO: 'Recusado'
}

const BUDGET_STATUS_COLORS = {
  RASCUNHO: 'bg-gray-100 text-gray-700',
  APROVADO: 'bg-emerald-100 text-emerald-700',
  RECUSADO: 'bg-red-100 text-red-700'
}

const emptyItemDraft = { catalogItemUuid: '', description: '', quantity: '1', unitPrice: '' }

function formatCurrency(value) {
  return Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatDateTime(isoDateTime) {
  if (!isoDateTime) return ''
  return new Date(isoDateTime).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

function formatFileSize(bytes) {
  if (!bytes && bytes !== 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function pendingChecklistCount(step) {
  return (step.checklistItems || []).filter((item) => !item.done).length
}

// Espelha assertWorkOrderIsExecutable no backend (V2.4 / ADR-0003).
function isExecutable(workOrder) {
  return workOrder.status === 'APROVADO' || workOrder.status === 'EM_EXECUCAO'
}

// Por que as etapas estao somente-leitura. Depende de o trabalho ainda nao
// ter comecado ou ja ter terminado - dizer "espere a aprovacao do orcamento"
// numa WorkOrder ja finalizada seria simplesmente falso.
function stepsLockedReason(workOrder) {
  switch (workOrder.status) {
    case 'SOLICITACAO_RECEBIDA':
    case 'ORCAMENTO_GERADO':
    case 'AGUARDANDO_APROVACAO':
      return 'As etapas só podem ser trabalhadas depois que o orçamento for aprovado.'
    case 'RECUSADO':
      return 'Orçamento recusado — esta Ordem de Serviço não será executada.'
    case 'ENTREGUE':
    case 'FINALIZADO':
      return 'Ordem de Serviço concluída — as etapas ficam apenas como histórico.'
    default:
      return null
  }
}

export default function WorkOrders() {
  const [workOrders, setWorkOrders] = useState([])
  const [clients, setClients] = useState([])
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  const [form, setForm] = useState(emptyForm)
  const [showForm, setShowForm] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  const [expandedUuid, setExpandedUuid] = useState(null)
  const [stepsByWorkOrder, setStepsByWorkOrder] = useState({})
  const [stepsLoading, setStepsLoading] = useState(false)
  const [notesDraft, setNotesDraft] = useState({})

  const [catalogItems, setCatalogItems] = useState([])
  const [budgetExpandedUuid, setBudgetExpandedUuid] = useState(null)
  const [budgetsByWorkOrder, setBudgetsByWorkOrder] = useState({})
  const [budgetLoading, setBudgetLoading] = useState(false)
  const [itemDraft, setItemDraft] = useState(emptyItemDraft)
  const [budgetActionError, setBudgetActionError] = useState('')

  const [timelineExpandedUuid, setTimelineExpandedUuid] = useState(null)
  const [timelineByWorkOrder, setTimelineByWorkOrder] = useState({})
  const [timelineLoading, setTimelineLoading] = useState(false)

  const [checklistDraft, setChecklistDraft] = useState({})
  const [evidencesByStep, setEvidencesByStep] = useState({})
  const [uploadingStep, setUploadingStep] = useState(null)

  function loadWorkOrders() {
    setLoading(true)
    const params = { page, size: 10, sort: 'createdAt,desc' }
    if (statusFilter) params.status = statusFilter

    api.get('/work-orders', { params })
      .then((response) => {
        setWorkOrders(response.data.content)
        setTotalPages(response.data.totalPages)
        setTotalElements(response.data.totalElements)
      })
      .catch(() => setError('Não foi possível carregar as WorkOrders.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadWorkOrders()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter])

  useEffect(() => {
    api.get('/clients', { params: { size: 100, sort: 'name,asc' } })
      .then((response) => setClients(response.data.content))
      .catch(() => {})

    api.get('/users')
      .then((response) => setUsers(response.data))
      .catch(() => {})

    api.get('/catalog-items', { params: { size: 200, sort: 'name,asc' } })
      .then((response) => setCatalogItems(response.data.content))
      .catch(() => {})
  }, [])

  function openCreateForm() {
    setForm(emptyForm)
    setFormError('')
    setShowForm(true)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSaving(true)
    setFormError('')

    try {
      const payload = {
        ...form,
        scheduledStart: form.scheduledStart || null,
        scheduledEnd: form.scheduledEnd || null,
        assignedToUuid: form.assignedToUuid || null
      }
      await api.post('/work-orders', payload)
      setShowForm(false)
      loadWorkOrders()
    } catch (err) {
      setFormError(err.response?.data?.message || 'Não foi possível criar a WorkOrder.')
    } finally {
      setSaving(false)
    }
  }

  async function handleStatusChange(workOrder, newStatus) {
    try {
      await api.patch(`/work-orders/${workOrder.uuid}/status`, { status: newStatus })
      loadWorkOrders()
    } catch (err) {
      setError(err.response?.data?.message || 'Não foi possível alterar o status.')
    }
  }

  async function handleAssign(workOrder, assignedToUuid) {
    try {
      await api.patch(`/work-orders/${workOrder.uuid}/assign`, { assignedToUuid: assignedToUuid || null })
      loadWorkOrders()
    } catch {
      setError('Não foi possível atribuir o responsável.')
    }
  }

  function toggleSteps(workOrder) {
    if (expandedUuid === workOrder.uuid) {
      setExpandedUuid(null)
      return
    }
    setExpandedUuid(workOrder.uuid)
    if (!stepsByWorkOrder[workOrder.uuid]) {
      loadSteps(workOrder.uuid)
    }
  }

  function loadSteps(workOrderUuid) {
    setStepsLoading(true)
    api.get(`/work-orders/${workOrderUuid}/steps`)
      .then((response) => {
        setStepsByWorkOrder((prev) => ({ ...prev, [workOrderUuid]: response.data }))
        const drafts = {}
        response.data.forEach((step) => { drafts[step.uuid] = step.notes || '' })
        setNotesDraft((prev) => ({ ...prev, ...drafts }))
        response.data.forEach((step) => loadEvidences(workOrderUuid, step.uuid))
      })
      .catch(() => setError('Não foi possível carregar as etapas.'))
      .finally(() => setStepsLoading(false))
  }

  function loadEvidences(workOrderUuid, stepUuid) {
    api.get(`/work-orders/${workOrderUuid}/steps/${stepUuid}/evidences`)
      .then((response) => setEvidencesByStep((prev) => ({ ...prev, [stepUuid]: response.data })))
      .catch(() => {})
  }

  async function handleEvidenceUpload(workOrderUuid, step, file) {
    if (!file) return
    setUploadingStep(step.uuid)
    setError('')

    try {
      // 1. O backend valida tipo/tamanho/permissao e devolve a URL assinada.
      const { data } = await api.post(
        `/work-orders/${workOrderUuid}/steps/${step.uuid}/evidences/upload-url`,
        { fileName: file.name, contentType: file.type, sizeBytes: file.size }
      )

      // 2. Envio direto ao storage com fetch puro, NAO com o axios da api:
      //    o interceptor dela anexa o header Authorization, que nao faz parte
      //    da assinatura da URL e faria o storage recusar o PUT.
      const upload = await fetch(data.uploadUrl, {
        method: 'PUT',
        body: file,
        headers: { 'Content-Type': file.type }
      })
      if (!upload.ok) {
        throw new Error(`storage respondeu ${upload.status}`)
      }

      // 3. So agora a evidencia passa a existir para o sistema.
      await api.post(
        `/work-orders/${workOrderUuid}/steps/${step.uuid}/evidences/${data.evidenceUuid}/confirm`
      )
      loadEvidences(workOrderUuid, step.uuid)
    } catch (err) {
      setError(err.response?.data?.message || 'Não foi possível enviar o arquivo.')
    } finally {
      setUploadingStep(null)
    }
  }

  async function handleEvidenceDownload(workOrderUuid, step, evidence) {
    try {
      const { data } = await api.get(
        `/work-orders/${workOrderUuid}/steps/${step.uuid}/evidences/${evidence.uuid}/download-url`
      )
      window.open(data.url, '_blank', 'noopener')
    } catch {
      setError('Não foi possível abrir o arquivo.')
    }
  }

  async function handleEvidenceDelete(workOrderUuid, step, evidence) {
    if (!confirm(`Remover "${evidence.fileName}"?`)) return
    try {
      await api.delete(`/work-orders/${workOrderUuid}/steps/${step.uuid}/evidences/${evidence.uuid}`)
      loadEvidences(workOrderUuid, step.uuid)
    } catch (err) {
      setError(err.response?.data?.message || 'Não foi possível remover o arquivo.')
    }
  }

  async function handleStepStatusChange(workOrderUuid, step, newStatus) {
    try {
      await api.patch(`/work-orders/${workOrderUuid}/steps/${step.uuid}/status`, { status: newStatus })
      loadSteps(workOrderUuid)
    } catch (err) {
      setError(err.response?.data?.message || 'Não foi possível alterar o status da etapa.')
    }
  }

  async function handleStepNotesSave(workOrderUuid, step) {
    try {
      // Self-transition (status inalterado) e permitida em StepStatusTransitions
      // exatamente para este caso: registrar observacao sem avancar a etapa.
      await api.patch(`/work-orders/${workOrderUuid}/steps/${step.uuid}/status`, {
        status: step.status,
        notes: notesDraft[step.uuid] ?? ''
      })
      loadSteps(workOrderUuid)
    } catch (err) {
      setError(err.response?.data?.message || 'Não foi possível salvar a observação.')
    }
  }

  async function handleChecklistToggle(workOrderUuid, step, item) {
    try {
      await api.patch(
        `/work-orders/${workOrderUuid}/steps/${step.uuid}/checklist/${item.uuid}`,
        { done: !item.done }
      )
      loadSteps(workOrderUuid)
    } catch (err) {
      setError(err.response?.data?.message || 'Não foi possível atualizar o item de checklist.')
    }
  }

  async function handleChecklistAdd(workOrderUuid, step) {
    const key = step.uuid
    const description = (checklistDraft[key] || '').trim()
    if (!description) return

    try {
      await api.post(`/work-orders/${workOrderUuid}/steps/${step.uuid}/checklist`, { description })
      setChecklistDraft((prev) => ({ ...prev, [key]: '' }))
      loadSteps(workOrderUuid)
    } catch (err) {
      setError(err.response?.data?.message || 'Não foi possível adicionar o item.')
    }
  }

  async function handleChecklistRemove(workOrderUuid, step, item) {
    try {
      await api.delete(`/work-orders/${workOrderUuid}/steps/${step.uuid}/checklist/${item.uuid}`)
      loadSteps(workOrderUuid)
    } catch (err) {
      setError(err.response?.data?.message || 'Não foi possível remover o item.')
    }
  }

  function toggleBudget(workOrder) {
    if (budgetExpandedUuid === workOrder.uuid) {
      setBudgetExpandedUuid(null)
      return
    }
    setBudgetExpandedUuid(workOrder.uuid)
    setBudgetActionError('')
    setItemDraft(emptyItemDraft)
    if (!(workOrder.uuid in budgetsByWorkOrder)) {
      loadBudget(workOrder.uuid)
    }
  }

  function loadBudget(workOrderUuid) {
    setBudgetLoading(true)
    api.get(`/work-orders/${workOrderUuid}/budget`)
      .then((response) => {
        setBudgetsByWorkOrder((prev) => ({ ...prev, [workOrderUuid]: response.data }))
      })
      .catch((err) => {
        // 404 significa "ainda nao existe orcamento para esta WorkOrder" -
        // nao e um erro, e o estado normal antes do primeiro POST.
        if (err.response?.status === 404) {
          setBudgetsByWorkOrder((prev) => ({ ...prev, [workOrderUuid]: null }))
        } else {
          setError('Não foi possível carregar o orçamento.')
        }
      })
      .finally(() => setBudgetLoading(false))
  }

  async function handleCreateBudget(workOrderUuid) {
    setBudgetActionError('')
    try {
      await api.post(`/work-orders/${workOrderUuid}/budget`)
      loadBudget(workOrderUuid)
      loadWorkOrders()
    } catch (err) {
      setBudgetActionError(err.response?.data?.message || 'Não foi possível criar o orçamento.')
    }
  }

  async function handleAddItem(workOrderUuid) {
    setBudgetActionError('')
    try {
      const payload = {
        catalogItemUuid: itemDraft.catalogItemUuid || null,
        description: itemDraft.description || null,
        quantity: Number(itemDraft.quantity),
        unitPrice: itemDraft.unitPrice !== '' ? Number(itemDraft.unitPrice) : null
      }
      await api.post(`/work-orders/${workOrderUuid}/budget/items`, payload)
      setItemDraft(emptyItemDraft)
      loadBudget(workOrderUuid)
    } catch (err) {
      setBudgetActionError(err.response?.data?.message || 'Não foi possível adicionar o item.')
    }
  }

  async function handleRemoveItem(workOrderUuid, itemUuid) {
    setBudgetActionError('')
    try {
      await api.delete(`/work-orders/${workOrderUuid}/budget/items/${itemUuid}`)
      loadBudget(workOrderUuid)
    } catch (err) {
      setBudgetActionError(err.response?.data?.message || 'Não foi possível remover o item.')
    }
  }

  async function handleBudgetDecision(workOrderUuid, status) {
    setBudgetActionError('')
    try {
      await api.patch(`/work-orders/${workOrderUuid}/budget/status`, { status })
      loadBudget(workOrderUuid)
      loadWorkOrders()
    } catch (err) {
      setBudgetActionError(err.response?.data?.message || 'Não foi possível registrar a decisão.')
    }
  }

  function toggleTimeline(workOrder) {
    if (timelineExpandedUuid === workOrder.uuid) {
      setTimelineExpandedUuid(null)
      return
    }
    setTimelineExpandedUuid(workOrder.uuid)
    // Recarrega sempre: a timeline muda a cada acao feita na propria tela
    // (status, orcamento, etapa), entao cache aqui mostraria historico velho.
    loadTimeline(workOrder.uuid)
  }

  function loadTimeline(workOrderUuid) {
    setTimelineLoading(true)
    api.get(`/work-orders/${workOrderUuid}/timeline`)
      .then((response) => {
        setTimelineByWorkOrder((prev) => ({ ...prev, [workOrderUuid]: response.data }))
      })
      .catch(() => setError('Não foi possível carregar a timeline.'))
      .finally(() => setTimelineLoading(false))
  }

  async function handleDownloadPdf(workOrderUuid) {
    setBudgetActionError('')
    try {
      const response = await api.get(`/work-orders/${workOrderUuid}/budget/pdf`, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `orcamento-${workOrderUuid}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      setBudgetActionError('Não foi possível baixar o PDF do orçamento.')
    }
  }

  function selectCatalogItem(catalogItemUuid) {
    const catalogItem = catalogItems.find((c) => c.uuid === catalogItemUuid)
    setItemDraft((prev) => ({
      ...prev,
      catalogItemUuid,
      description: catalogItem ? catalogItem.name : prev.description,
      unitPrice: catalogItem ? String(catalogItem.unitPrice) : prev.unitPrice
    }))
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-medium text-flowops-900">FlowOps</h1>
          <nav className="mt-1 flex gap-4 text-sm">
            <Link to="/dashboard" className="text-gray-500 hover:text-flowops-700">Dashboard</Link>
            <Link to="/clients" className="text-gray-500 hover:text-flowops-700">Clientes</Link>
            <span className="font-medium text-flowops-700">WorkOrders</span>
            <Link to="/catalog" className="text-gray-500 hover:text-flowops-700">Catálogo</Link>
            <Link to="/workflow" className="text-gray-500 hover:text-flowops-700">Workflow</Link>
          </nav>
        </div>
        <button
          onClick={openCreateForm}
          className="rounded bg-flowops-600 px-4 py-2 text-sm font-medium text-white hover:bg-flowops-700"
        >
          Nova WorkOrder
        </button>
      </header>

      <main className="p-6">
        {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

        <div className="mb-4">
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}
            className="rounded border border-gray-300 px-3 py-1.5 text-sm focus:border-flowops-600 focus:outline-none"
          >
            <option value="">Todos os status</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="mb-6 max-w-lg rounded-lg border border-gray-200 bg-white p-5"
          >
            <h2 className="mb-4 text-base font-medium text-gray-900">Nova WorkOrder</h2>

            <label className="mb-1 block text-sm text-gray-700">Cliente *</label>
            <select
              required
              value={form.clientUuid}
              onChange={(e) => setForm({ ...form, clientUuid: e.target.value })}
              className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
            >
              <option value="">Selecione um cliente</option>
              {clients.map((c) => (
                <option key={c.uuid} value={c.uuid}>{c.name}</option>
              ))}
            </select>

            <label className="mb-1 block text-sm text-gray-700">Título *</label>
            <input
              required
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
            />

            <label className="mb-1 block text-sm text-gray-700">Descrição</label>
            <textarea
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              rows={2}
              className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
            />

            <div className="mb-3 grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-sm text-gray-700">Prioridade</label>
                <select
                  value={form.priority}
                  onChange={(e) => setForm({ ...form, priority: e.target.value })}
                  className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                >
                  <option value="BAIXA">Baixa</option>
                  <option value="NORMAL">Normal</option>
                  <option value="ALTA">Alta</option>
                  <option value="URGENTE">Urgente</option>
                </select>
              </div>
              <div>
                <label className="mb-1 block text-sm text-gray-700">Responsável</label>
                <select
                  value={form.assignedToUuid}
                  onChange={(e) => setForm({ ...form, assignedToUuid: e.target.value })}
                  className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                >
                  <option value="">Sem responsável</option>
                  {users.map((u) => (
                    <option key={u.uuid} value={u.uuid}>{u.name}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="mb-3 grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-sm text-gray-700">Início previsto</label>
                <input
                  type="date"
                  value={form.scheduledStart}
                  onChange={(e) => setForm({ ...form, scheduledStart: e.target.value })}
                  className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm text-gray-700">Fim previsto</label>
                <input
                  type="date"
                  value={form.scheduledEnd}
                  onChange={(e) => setForm({ ...form, scheduledEnd: e.target.value })}
                  className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                />
              </div>
            </div>

            {formError && (
              <p className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{formError}</p>
            )}

            <div className="flex gap-2">
              <button
                type="submit"
                disabled={saving}
                className="rounded bg-flowops-600 px-4 py-2 text-sm font-medium text-white hover:bg-flowops-700 disabled:opacity-60"
              >
                {saving ? 'Criando...' : 'Criar'}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
              >
                Cancelar
              </button>
            </div>
          </form>
        )}

        <div className="space-y-3">
          {loading && <p className="text-sm text-gray-400">Carregando...</p>}

          {!loading && workOrders.length === 0 && (
            <p className="text-sm text-gray-400">Nenhuma WorkOrder encontrada.</p>
          )}

          {workOrders.map((wo) => (
            <div key={wo.uuid} className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="font-medium text-gray-900">{wo.title}</h3>
                  <p className="text-sm text-gray-500">{wo.clientName}</p>
                </div>
                <span className={`rounded-full px-3 py-1 text-xs font-medium ${STATUS_COLORS[wo.status]}`}>
                  {STATUS_LABELS[wo.status]}
                </span>
              </div>

              {wo.description && <p className="mt-2 text-sm text-gray-600">{wo.description}</p>}

              <div className="mt-3 flex flex-wrap items-center gap-3 text-sm">
                <label className="flex items-center gap-2 text-gray-600">
                  Responsável:
                  <select
                    value={wo.assignedToUuid || ''}
                    onChange={(e) => handleAssign(wo, e.target.value)}
                    className="rounded border border-gray-300 px-2 py-1 text-sm focus:border-flowops-600 focus:outline-none"
                  >
                    <option value="">Sem responsável</option>
                    {users.map((u) => (
                      <option key={u.uuid} value={u.uuid}>{u.name}</option>
                    ))}
                  </select>
                </label>

                {NEXT_STATUSES[wo.status]?.map((next) => (
                  <button
                    key={next}
                    onClick={() => handleStatusChange(wo, next)}
                    className="rounded border border-flowops-600 px-3 py-1 text-flowops-700 hover:bg-flowops-50"
                  >
                    Avançar para {STATUS_LABELS[next]}
                  </button>
                ))}

                {STATUS_HINTS[wo.status] && (
                  <span className="text-xs italic text-gray-500">{STATUS_HINTS[wo.status]}</span>
                )}

                <button
                  onClick={() => toggleSteps(wo)}
                  className="rounded border border-gray-300 px-3 py-1 text-gray-600 hover:bg-gray-100"
                >
                  {expandedUuid === wo.uuid ? 'Ocultar etapas' : 'Ver etapas'}
                </button>

                <button
                  onClick={() => toggleBudget(wo)}
                  className="rounded border border-gray-300 px-3 py-1 text-gray-600 hover:bg-gray-100"
                >
                  {budgetExpandedUuid === wo.uuid ? 'Ocultar orçamento' : 'Ver orçamento'}
                </button>

                <button
                  onClick={() => toggleTimeline(wo)}
                  className="rounded border border-gray-300 px-3 py-1 text-gray-600 hover:bg-gray-100"
                >
                  {timelineExpandedUuid === wo.uuid ? 'Ocultar histórico' : 'Ver histórico'}
                </button>
              </div>

              {timelineExpandedUuid === wo.uuid && (
                <div className="mt-4 border-t border-gray-100 pt-3">
                  {timelineLoading && !timelineByWorkOrder[wo.uuid] && (
                    <p className="text-sm text-gray-400">Carregando histórico...</p>
                  )}

                  {timelineByWorkOrder[wo.uuid]?.length === 0 && (
                    <p className="text-sm text-gray-400">Nenhum evento registrado ainda.</p>
                  )}

                  {timelineByWorkOrder[wo.uuid]?.length > 0 && (
                    <ol className="relative ml-2 border-l border-gray-200">
                      {timelineByWorkOrder[wo.uuid].map((event, index) => (
                        <li key={`${event.occurredAt}-${index}`} className="mb-4 ml-4 last:mb-0">
                          <span className="absolute -left-[5px] mt-1.5 h-2.5 w-2.5 rounded-full bg-flowops-600" />
                          <p className="text-sm text-gray-800">{event.description}</p>
                          <p className="text-xs text-gray-500">
                            {formatDateTime(event.occurredAt)}
                            {event.actorName && ` — ${event.actorName}`}
                          </p>
                        </li>
                      ))}
                    </ol>
                  )}
                </div>
              )}

              {budgetExpandedUuid === wo.uuid && (
                <div className="mt-4 border-t border-gray-100 pt-3">
                  {budgetLoading && !(wo.uuid in budgetsByWorkOrder) && (
                    <p className="text-sm text-gray-400">Carregando orçamento...</p>
                  )}

                  {budgetActionError && (
                    <p className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{budgetActionError}</p>
                  )}

                  {!budgetLoading && wo.uuid in budgetsByWorkOrder && budgetsByWorkOrder[wo.uuid] === null && (
                    wo.status === 'SOLICITACAO_RECEBIDA' ? (
                      <button
                        onClick={() => handleCreateBudget(wo.uuid)}
                        className="rounded bg-flowops-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-flowops-700"
                      >
                        Criar orçamento
                      </button>
                    ) : (
                      <p className="text-sm text-gray-400">Esta WorkOrder não tem orçamento.</p>
                    )
                  )}

                  {budgetsByWorkOrder[wo.uuid] && (() => {
                    const budget = budgetsByWorkOrder[wo.uuid]
                    const editable = budget.status === 'RASCUNHO'
                    return (
                      <div>
                        <div className="mb-3 flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className={`rounded-full px-3 py-1 text-xs font-medium ${BUDGET_STATUS_COLORS[budget.status]}`}>
                              {BUDGET_STATUS_LABELS[budget.status]}
                            </span>
                            <button
                              onClick={() => handleDownloadPdf(wo.uuid)}
                              className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-100"
                            >
                              Baixar PDF
                            </button>
                          </div>
                          <span className="text-sm font-medium text-gray-900">
                            Total: {formatCurrency(budget.totalAmount)}
                          </span>
                        </div>

                        {!editable && budget.decidedByName && (
                          <p className="mb-3 text-xs text-gray-500">
                            {budget.status === 'APROVADO' ? 'Aprovado' : 'Recusado'} por {budget.decidedByName} em {formatDateTime(budget.decidedAt)}
                          </p>
                        )}

                        <div className="space-y-2">
                          {budget.items.length === 0 && (
                            <p className="text-sm text-gray-400">Nenhum item adicionado ainda.</p>
                          )}
                          {budget.items.map((item) => (
                            <div key={item.uuid} className="flex items-center justify-between rounded border border-gray-200 bg-gray-50 px-3 py-2 text-sm">
                              <span className="text-gray-700">
                                {item.description} — {item.quantity} x {formatCurrency(item.unitPrice)}
                              </span>
                              <div className="flex items-center gap-3">
                                <span className="font-medium text-gray-900">{formatCurrency(item.subtotal)}</span>
                                {editable && (
                                  <button
                                    onClick={() => handleRemoveItem(wo.uuid, item.uuid)}
                                    className="text-red-600 hover:underline"
                                  >
                                    Remover
                                  </button>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>

                        {editable && (
                          <div className="mt-3 flex flex-wrap items-end gap-2 rounded border border-gray-200 bg-white p-3">
                            <div>
                              <label className="mb-1 block text-xs text-gray-600">Item do catálogo</label>
                              <select
                                value={itemDraft.catalogItemUuid}
                                onChange={(e) => selectCatalogItem(e.target.value)}
                                className="rounded border border-gray-300 px-2 py-1 text-sm focus:border-flowops-600 focus:outline-none"
                              >
                                <option value="">Item avulso</option>
                                {catalogItems.map((c) => (
                                  <option key={c.uuid} value={c.uuid}>{c.name}</option>
                                ))}
                              </select>
                            </div>
                            <div>
                              <label className="mb-1 block text-xs text-gray-600">Descrição</label>
                              <input
                                value={itemDraft.description}
                                onChange={(e) => setItemDraft({ ...itemDraft, description: e.target.value })}
                                className="w-40 rounded border border-gray-300 px-2 py-1 text-sm focus:border-flowops-600 focus:outline-none"
                              />
                            </div>
                            <div>
                              <label className="mb-1 block text-xs text-gray-600">Qtd.</label>
                              <input
                                type="number"
                                min="0.01"
                                step="0.01"
                                value={itemDraft.quantity}
                                onChange={(e) => setItemDraft({ ...itemDraft, quantity: e.target.value })}
                                className="w-20 rounded border border-gray-300 px-2 py-1 text-sm focus:border-flowops-600 focus:outline-none"
                              />
                            </div>
                            <div>
                              <label className="mb-1 block text-xs text-gray-600">Valor unit. (R$)</label>
                              <input
                                type="number"
                                min="0"
                                step="0.01"
                                value={itemDraft.unitPrice}
                                onChange={(e) => setItemDraft({ ...itemDraft, unitPrice: e.target.value })}
                                className="w-28 rounded border border-gray-300 px-2 py-1 text-sm focus:border-flowops-600 focus:outline-none"
                              />
                            </div>
                            <button
                              onClick={() => handleAddItem(wo.uuid)}
                              className="rounded border border-flowops-600 px-3 py-1.5 text-sm text-flowops-700 hover:bg-flowops-50"
                            >
                              Adicionar item
                            </button>
                          </div>
                        )}

                        {editable && (
                          <div className="mt-3 flex gap-2">
                            <button
                              onClick={() => handleBudgetDecision(wo.uuid, 'APROVADO')}
                              className="rounded bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700"
                            >
                              Registrar aprovação
                            </button>
                            <button
                              onClick={() => handleBudgetDecision(wo.uuid, 'RECUSADO')}
                              className="rounded bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700"
                            >
                              Registrar recusa
                            </button>
                          </div>
                        )}
                      </div>
                    )
                  })()}
                </div>
              )}

              {expandedUuid === wo.uuid && (
                <div className="mt-4 border-t border-gray-100 pt-3">
                  {stepsLoading && !stepsByWorkOrder[wo.uuid] && (
                    <p className="text-sm text-gray-400">Carregando etapas...</p>
                  )}

                  {stepsByWorkOrder[wo.uuid]?.length === 0 && (
                    <p className="text-sm text-gray-400">
                      Esta WorkOrder não tem um workflow configurado — nenhuma etapa foi gerada.
                    </p>
                  )}

                  {!isExecutable(wo) && stepsByWorkOrder[wo.uuid]?.length > 0 && stepsLockedReason(wo) && (
                    <p className="mb-3 rounded bg-amber-50 px-3 py-2 text-xs text-amber-800">
                      {stepsLockedReason(wo)}
                    </p>
                  )}

                  <div className="space-y-3">
                    {stepsByWorkOrder[wo.uuid]?.map((step, stepIndex) => {
                      const steps = stepsByWorkOrder[wo.uuid]
                      const blockedByPrevious = steps
                        .slice(0, stepIndex)
                        .find((previous) => previous.status !== 'CONCLUIDA')

                      return (
                        <div key={step.uuid} className="rounded border border-gray-200 bg-gray-50 p-3">
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-medium text-gray-800">
                              {step.stepOrder}. {step.title}
                            </span>
                            <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STEP_STATUS_COLORS[step.status]}`}>
                              {STEP_STATUS_LABELS[step.status]}
                            </span>
                          </div>

                          {isExecutable(wo) && (
                            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                              {STEP_NEXT_STATUSES[step.status]?.map((next) => {
                                // Espelha assertPreviousStepsCompleted no backend:
                                // so o inicio (EM_ANDAMENTO) depende da etapa anterior;
                                // bloquear/desbloquear uma etapa futura e livre.
                                const blocked = next === 'EM_ANDAMENTO' && blockedByPrevious
                                return (
                                  <button
                                    key={next}
                                    disabled={Boolean(blocked)}
                                    title={blocked ? `Conclua "${blockedByPrevious.title}" primeiro` : undefined}
                                    onClick={() => handleStepStatusChange(wo.uuid, step, next)}
                                    className="rounded border border-flowops-600 px-2 py-1 text-flowops-700 hover:bg-flowops-50 disabled:cursor-not-allowed disabled:border-gray-300 disabled:text-gray-400 disabled:hover:bg-transparent"
                                  >
                                    {STEP_STATUS_LABELS[next]}
                                  </button>
                                )
                              })}
                            </div>
                          )}

                          {step.checklistItems?.length > 0 && (
                            <ul className="mt-2 space-y-1">
                              {step.checklistItems.map((item) => {
                                // Etapa concluida e terminal: o checklist vira
                                // historico, igual a regra do backend.
                                const editable = isExecutable(wo) && step.status !== 'CONCLUIDA'
                                return (
                                  <li key={item.uuid} className="flex items-center gap-2 text-xs">
                                    <input
                                      type="checkbox"
                                      checked={item.done}
                                      disabled={!editable}
                                      onChange={() => handleChecklistToggle(wo.uuid, step, item)}
                                      className="h-3.5 w-3.5 rounded border-gray-300 text-flowops-600 focus:ring-flowops-600 disabled:cursor-not-allowed"
                                    />
                                    <span className={item.done ? 'text-gray-400 line-through' : 'text-gray-700'}>
                                      {item.description}
                                    </span>
                                    {!item.fromTemplate && (
                                      <span
                                        title="Item avulso, criado nesta Ordem de Serviço"
                                        className="rounded bg-gray-100 px-1 text-[10px] text-gray-500"
                                      >
                                        avulso
                                      </span>
                                    )}
                                    {item.done && item.doneByName && (
                                      <span className="text-[10px] text-gray-400">
                                        por {item.doneByName}
                                      </span>
                                    )}
                                    {editable && !item.fromTemplate && (
                                      <button
                                        onClick={() => handleChecklistRemove(wo.uuid, step, item)}
                                        className="text-red-600 hover:underline"
                                      >
                                        remover
                                      </button>
                                    )}
                                  </li>
                                )
                              })}
                            </ul>
                          )}

                          {isExecutable(wo) && step.status !== 'CONCLUIDA' && (
                            <div className="mt-2 flex gap-2">
                              <input
                                value={checklistDraft[step.uuid] || ''}
                                onChange={(e) =>
                                  setChecklistDraft((prev) => ({ ...prev, [step.uuid]: e.target.value }))
                                }
                                placeholder="Novo item de checklist..."
                                className="flex-1 rounded border border-gray-300 px-2 py-1 text-xs focus:border-flowops-600 focus:outline-none"
                              />
                              <button
                                onClick={() => handleChecklistAdd(wo.uuid, step)}
                                className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-100"
                              >
                                Adicionar
                              </button>
                            </div>
                          )}

                          {/* Aviso, nao trava: o backend permite concluir com item
                              pendente (o backlog pede "marcar sem mudar status"),
                              mas esconder isso deixaria a verificacao decorativa. */}
                          {isExecutable(wo) && step.status === 'EM_ANDAMENTO' && pendingChecklistCount(step) > 0 && (
                            <p className="mt-2 rounded bg-amber-50 px-2 py-1 text-xs text-amber-800">
                              {pendingChecklistCount(step)} item(ns) de checklist ainda não marcados nesta etapa.
                            </p>
                          )}

                          {/* Evidências (V2.6) */}
                          <div className="mt-3 border-t border-gray-200 pt-2">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-xs font-medium text-gray-600">Evidências</span>
                              {isExecutable(wo) && step.status !== 'CONCLUIDA' && (
                                <label className="cursor-pointer rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-100">
                                  {uploadingStep === step.uuid ? 'Enviando...' : 'Anexar arquivo'}
                                  <input
                                    type="file"
                                    className="hidden"
                                    accept="image/jpeg,image/png,image/webp,image/heic,application/pdf"
                                    disabled={uploadingStep === step.uuid}
                                    onChange={(e) => {
                                      handleEvidenceUpload(wo.uuid, step, e.target.files?.[0])
                                      e.target.value = ''
                                    }}
                                  />
                                </label>
                              )}
                            </div>

                            {(evidencesByStep[step.uuid]?.length ?? 0) === 0 ? (
                              <p className="mt-1 text-xs text-gray-400">Nenhum arquivo anexado.</p>
                            ) : (
                              <ul className="mt-1 space-y-1">
                                {evidencesByStep[step.uuid].map((evidence) => (
                                  <li key={evidence.uuid} className="flex items-center gap-2 text-xs">
                                    <span title={evidence.image ? 'Imagem' : 'Documento'}>
                                      {evidence.image ? '🖼️' : '📄'}
                                    </span>
                                    <button
                                      onClick={() => handleEvidenceDownload(wo.uuid, step, evidence)}
                                      className="text-flowops-600 hover:underline"
                                    >
                                      {evidence.fileName}
                                    </button>
                                    <span className="text-[10px] text-gray-400">
                                      {formatFileSize(evidence.sizeBytes)}
                                      {evidence.uploadedByName ? ` · ${evidence.uploadedByName}` : ''}
                                    </span>
                                    {isExecutable(wo) && step.status !== 'CONCLUIDA' && (
                                      <button
                                        onClick={() => handleEvidenceDelete(wo.uuid, step, evidence)}
                                        className="text-red-600 hover:underline"
                                      >
                                        remover
                                      </button>
                                    )}
                                  </li>
                                ))}
                              </ul>
                            )}
                          </div>

                          {step.status === 'CONCLUIDA' ? (
                            step.notes && <p className="mt-2 text-xs text-gray-600">{step.notes}</p>
                          ) : isExecutable(wo) ? (
                            <div className="mt-2 flex gap-2">
                              <input
                                value={notesDraft[step.uuid] ?? ''}
                                onChange={(e) => setNotesDraft((prev) => ({ ...prev, [step.uuid]: e.target.value }))}
                                placeholder="Observação da etapa..."
                                className="flex-1 rounded border border-gray-300 px-2 py-1 text-xs focus:border-flowops-600 focus:outline-none"
                              />
                              <button
                                onClick={() => handleStepNotesSave(wo.uuid, step)}
                                className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-100"
                              >
                                Salvar
                              </button>
                            </div>
                          ) : (
                            step.notes && <p className="mt-2 text-xs text-gray-600">{step.notes}</p>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>

        {!loading && totalElements > 0 && (
          <div className="mt-4 flex items-center justify-between text-sm text-gray-600">
            <span>
              {totalElements} WorkOrder{totalElements !== 1 ? 's' : ''} — página {page + 1} de {Math.max(totalPages, 1)}
            </span>
            <div className="flex gap-1">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded border border-gray-300 px-3 py-1 disabled:cursor-not-allowed disabled:opacity-40 hover:bg-gray-100"
              >
                Anterior
              </button>
              <button
                onClick={() => setPage((p) => (p + 1 < totalPages ? p + 1 : p))}
                disabled={page + 1 >= totalPages}
                className="rounded border border-gray-300 px-3 py-1 disabled:cursor-not-allowed disabled:opacity-40 hover:bg-gray-100"
              >
                Próxima
              </button>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
