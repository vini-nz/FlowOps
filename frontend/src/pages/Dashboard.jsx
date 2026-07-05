import { useEffect, useState } from 'react'
import { useAuth } from '../contexts/AuthContext.jsx'
import api from '../services/api.js'

export default function Dashboard() {
  const { user, logout } = useAuth()
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/dashboard/summary')
      .then((response) => setSummary(response.data))
      .catch(() => setError('Nao foi possivel carregar o dashboard.'))
  }, [])

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-medium text-flowops-900">FlowOps</h1>
          <p className="text-sm text-gray-500">{user?.companyName}</p>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm text-gray-600">
            {user?.name} <span className="text-gray-400">({user?.role})</span>
          </span>
          <button
            onClick={logout}
            className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
          >
            Sair
          </button>
        </div>
      </header>

      <main className="p-6">
        <h2 className="mb-4 text-base font-medium text-gray-900">Resumo operacional</h2>

        {error && <p className="text-sm text-red-600">{error}</p>}

        {!summary && !error && <p className="text-sm text-gray-500">Carregando...</p>}

        {summary && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div className="rounded-lg border border-gray-200 bg-white p-5">
              <p className="text-sm text-gray-500">Total de WorkOrders</p>
              <p className="mt-1 text-2xl font-medium text-flowops-900">
                {summary.totalWorkOrders}
              </p>
            </div>

            {Object.entries(summary.byStatus).map(([status, count]) => (
              <div key={status} className="rounded-lg border border-gray-200 bg-white p-5">
                <p className="text-sm text-gray-500">{status.replaceAll('_', ' ')}</p>
                <p className="mt-1 text-2xl font-medium text-flowops-900">{count}</p>
              </div>
            ))}
          </div>
        )}

        <p className="mt-8 text-xs text-gray-400">
          Login, JWT e leitura protegida do banco confirmados end-to-end (Sprint 1).
          Modulos de Clientes, WorkOrders e Etapas chegam nas Sprints 2 a 4.
        </p>
      </main>
    </div>
  )
}
