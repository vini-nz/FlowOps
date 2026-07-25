import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api.js'

const emptyForm = { name: '', email: '', phone: '', document: '', notes: '' }
const PAGE_SIZE_OPTIONS = [10, 20, 50]

export default function Clients() {
  const [clients, setClients] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  const [form, setForm] = useState(emptyForm)
  const [editingUuid, setEditingUuid] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  function loadClients() {
    setLoading(true)
    const params = { page, size, sort: 'name,asc' }
    if (search) params.search = search

    api.get('/clients', { params })
      .then((response) => {
        setClients(response.data.content)
        setTotalPages(response.data.totalPages)
        setTotalElements(response.data.totalElements)
      })
      .catch(() => setError('Não foi possível carregar os clientes.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadClients()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, search])

  function handleSearchSubmit(e) {
    e.preventDefault()
    setPage(0)
    setSearch(searchInput.trim())
  }

  function openCreateForm() {
    setForm(emptyForm)
    setEditingUuid(null)
    setFormError('')
    setShowForm(true)
  }

  function openEditForm(client) {
    setForm({
      name: client.name,
      email: client.email || '',
      phone: client.phone || '',
      document: client.document || '',
      notes: client.notes || ''
    })
    setEditingUuid(client.uuid)
    setFormError('')
    setShowForm(true)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSaving(true)
    setFormError('')

    try {
      if (editingUuid) {
        await api.put(`/clients/${editingUuid}`, form)
      } else {
        await api.post('/clients', form)
      }
      setShowForm(false)
      loadClients()
    } catch (err) {
      setFormError(err.response?.data?.message || 'Não foi possível salvar o cliente.')
    } finally {
      setSaving(false)
    }
  }

  async function handleDeactivate(client) {
    if (!confirm(`Remover o cliente "${client.name}"? Se ele tiver WorkOrders associadas, será apenas desativado; caso contrário, será removido definitivamente.`)) return

    try {
      await api.delete(`/clients/${client.uuid}`)
      loadClients()
    } catch {
      setError('Não foi possível remover o cliente.')
    }
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-medium text-flowops-900">FlowOps</h1>
          <nav className="mt-1 flex gap-4 text-sm">
            <Link to="/dashboard" className="text-gray-500 hover:text-flowops-700">Dashboard</Link>
            <span className="font-medium text-flowops-700">Clientes</span>
            <Link to="/work-orders" className="text-gray-500 hover:text-flowops-700">WorkOrders</Link>
            <Link to="/catalog" className="text-gray-500 hover:text-flowops-700">Catálogo</Link>
            <Link to="/workflow" className="text-gray-500 hover:text-flowops-700">Workflow</Link>
          </nav>
        </div>
        <button
          onClick={openCreateForm}
          className="rounded bg-flowops-600 px-4 py-2 text-sm font-medium text-white hover:bg-flowops-700"
        >
          Novo cliente
        </button>
      </header>

      <main className="p-6">
        {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

        <div className="mb-4 flex items-center justify-between gap-4">
          <form onSubmit={handleSearchSubmit} className="flex gap-2">
            <input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Pesquisar por nome..."
              className="w-64 rounded border border-gray-300 px-3 py-1.5 text-sm focus:border-flowops-600 focus:outline-none"
            />
            <button
              type="submit"
              className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
            >
              Buscar
            </button>
            {search && (
              <button
                type="button"
                onClick={() => { setSearchInput(''); setSearch(''); setPage(0) }}
                className="text-sm text-gray-500 hover:underline"
              >
                Limpar
              </button>
            )}
          </form>

          <label className="flex items-center gap-2 text-sm text-gray-600">
            Por página
            <select
              value={size}
              onChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
              className="rounded border border-gray-300 px-2 py-1 text-sm focus:border-flowops-600 focus:outline-none"
            >
              {PAGE_SIZE_OPTIONS.map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          </label>
        </div>

        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="mb-6 max-w-lg rounded-lg border border-gray-200 bg-white p-5"
          >
            <h2 className="mb-4 text-base font-medium text-gray-900">
              {editingUuid ? 'Editar cliente' : 'Novo cliente'}
            </h2>

            <label className="mb-1 block text-sm text-gray-700">Nome *</label>
            <input
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
            />

            <label className="mb-1 block text-sm text-gray-700">E-mail</label>
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
            />

            <label className="mb-1 block text-sm text-gray-700">Telefone</label>
            <input
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
            />

            <label className="mb-1 block text-sm text-gray-700">Documento</label>
            <input
              value={form.document}
              onChange={(e) => setForm({ ...form, document: e.target.value })}
              className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
            />

            <label className="mb-1 block text-sm text-gray-700">Observações</label>
            <textarea
              value={form.notes}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
              className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
              rows={2}
            />

            {formError && (
              <p className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{formError}</p>
            )}

            <div className="flex gap-2">
              <button
                type="submit"
                disabled={saving}
                className="rounded bg-flowops-600 px-4 py-2 text-sm font-medium text-white hover:bg-flowops-700 disabled:opacity-60"
              >
                {saving ? 'Salvando...' : 'Salvar'}
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

        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-gray-500">
              <tr>
                <th className="px-4 py-3 font-medium">Nome</th>
                <th className="px-4 py-3 font-medium">E-mail</th>
                <th className="px-4 py-3 font-medium">Telefone</th>
                <th className="px-4 py-3 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400">Carregando...</td></tr>
              )}

              {!loading && clients.length === 0 && (
                <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400">Nenhum cliente cadastrado.</td></tr>
              )}

              {clients.map((client) => (
                <tr key={client.uuid} className="border-t border-gray-100">
                  <td className="px-4 py-3 text-gray-900">{client.name}</td>
                  <td className="px-4 py-3 text-gray-600">{client.email || '—'}</td>
                  <td className="px-4 py-3 text-gray-600">{client.phone || '—'}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => openEditForm(client)}
                      className="mr-3 text-flowops-600 hover:underline"
                    >
                      Editar
                    </button>
                    <button
                      onClick={() => handleDeactivate(client)}
                      className="text-red-600 hover:underline"
                    >
                      Remover
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {!loading && totalElements > 0 && (
          <div className="mt-4 flex items-center justify-between text-sm text-gray-600">
            <span>
              {totalElements} cliente{totalElements !== 1 ? 's' : ''} — página {page + 1} de {Math.max(totalPages, 1)}
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
