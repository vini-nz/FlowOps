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

// Espelha WorkOrderStatusTransitions.java - so para guiar a interface.
// A validacao de verdade acontece no backend; se as duas divergirem, o
// backend responde 409 com a mensagem do que deu errado.
const NEXT_STATUSES = {
  SOLICITACAO_RECEBIDA: ['ORCAMENTO_GERADO'],
  ORCAMENTO_GERADO: ['AGUARDANDO_APROVACAO'],
  AGUARDANDO_APROVACAO: ['APROVADO', 'RECUSADO'],
  APROVADO: ['EM_EXECUCAO'],
  EM_EXECUCAO: ['ENTREGUE'],
  ENTREGUE: ['FINALIZADO'],
  RECUSADO: [],
  FINALIZADO: []
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

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-medium text-flowops-900">FlowOps</h1>
          <nav className="mt-1 flex gap-4 text-sm">
            <Link to="/dashboard" className="text-gray-500 hover:text-flowops-700">Dashboard</Link>
            <Link to="/clients" className="text-gray-500 hover:text-flowops-700">Clientes</Link>
            <span className="font-medium text-flowops-700">WorkOrders</span>
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
              </div>
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
