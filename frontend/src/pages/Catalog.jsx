import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import NotificationBell from '../components/NotificationBell.jsx'
import api from '../services/api.js'

const emptyForm = { name: '', description: '', unitPrice: '', unit: 'UN' }

function formatCurrency(value) {
  return Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export default function Catalog() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  const [form, setForm] = useState(emptyForm)
  const [editingUuid, setEditingUuid] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  function loadItems() {
    setLoading(true)
    const params = { page, size: 20, sort: 'name,asc' }
    if (search) params.search = search

    api.get('/catalog-items', { params })
      .then((response) => {
        setItems(response.data.content)
        setTotalPages(response.data.totalPages)
        setTotalElements(response.data.totalElements)
      })
      .catch(() => setError('Não foi possível carregar o catálogo.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadItems()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, search])

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

  function openEditForm(item) {
    setForm({
      name: item.name,
      description: item.description || '',
      unitPrice: String(item.unitPrice),
      unit: item.unit
    })
    setEditingUuid(item.uuid)
    setFormError('')
    setShowForm(true)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSaving(true)
    setFormError('')

    try {
      const payload = { ...form, unitPrice: Number(form.unitPrice) }
      if (editingUuid) {
        await api.put(`/catalog-items/${editingUuid}`, payload)
      } else {
        await api.post('/catalog-items', payload)
      }
      setShowForm(false)
      loadItems()
    } catch (err) {
      setFormError(err.response?.data?.message || 'Não foi possível salvar o item.')
    } finally {
      setSaving(false)
    }
  }

  async function handleDeactivate(item) {
    if (!confirm(`Remover "${item.name}" do catálogo?`)) return

    try {
      await api.delete(`/catalog-items/${item.uuid}`)
      loadItems()
    } catch {
      setError('Não foi possível remover o item.')
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
            <Link to="/work-orders" className="text-gray-500 hover:text-flowops-700">WorkOrders</Link>
            <span className="font-medium text-flowops-700">Catálogo</span>
            <Link to="/workflow" className="text-gray-500 hover:text-flowops-700">Workflow</Link>
          </nav>
        </div>
        <div className="flex items-center gap-3">
          <NotificationBell />
        <button
          onClick={openCreateForm}
          className="rounded bg-flowops-600 px-4 py-2 text-sm font-medium text-white hover:bg-flowops-700"
        >
          Novo item
        </button>
        </div>
      </header>

      <main className="p-6">
        {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

        <form onSubmit={handleSearchSubmit} className="mb-4 flex gap-2">
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

        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="mb-6 max-w-lg rounded-lg border border-gray-200 bg-white p-5"
          >
            <h2 className="mb-4 text-base font-medium text-gray-900">
              {editingUuid ? 'Editar item' : 'Novo item de catálogo'}
            </h2>

            <label className="mb-1 block text-sm text-gray-700">Nome *</label>
            <input
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
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
                <label className="mb-1 block text-sm text-gray-700">Valor unitário (R$) *</label>
                <input
                  required
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.unitPrice}
                  onChange={(e) => setForm({ ...form, unitPrice: e.target.value })}
                  className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm text-gray-700">Unidade</label>
                <input
                  value={form.unit}
                  onChange={(e) => setForm({ ...form, unit: e.target.value })}
                  placeholder="UN, HORA, M2..."
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
                <th className="px-4 py-3 font-medium">Valor unitário</th>
                <th className="px-4 py-3 font-medium">Unidade</th>
                <th className="px-4 py-3 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400">Carregando...</td></tr>
              )}

              {!loading && items.length === 0 && (
                <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400">Nenhum item cadastrado.</td></tr>
              )}

              {items.map((item) => (
                <tr key={item.uuid} className="border-t border-gray-100">
                  <td className="px-4 py-3 text-gray-900">
                    {item.name}
                    {item.description && <p className="text-xs text-gray-500">{item.description}</p>}
                  </td>
                  <td className="px-4 py-3 text-gray-600">{formatCurrency(item.unitPrice)}</td>
                  <td className="px-4 py-3 text-gray-600">{item.unit}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => openEditForm(item)}
                      className="mr-3 text-flowops-600 hover:underline"
                    >
                      Editar
                    </button>
                    <button
                      onClick={() => handleDeactivate(item)}
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
              {totalElements} ite{totalElements !== 1 ? 'ns' : 'm'} — página {page + 1} de {Math.max(totalPages, 1)}
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
