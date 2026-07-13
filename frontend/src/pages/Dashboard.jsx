import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext.jsx'
import api from '../services/api.js'

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

function formatDate(isoDate) {
  if (!isoDate) return '—'
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

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
          <nav className="mt-1 flex gap-4 text-sm">
            <span className="font-medium text-flowops-700">Dashboard</span>
            <Link to="/clients" className="text-gray-500 hover:text-flowops-700">Clientes</Link>
            <Link to="/work-orders" className="text-gray-500 hover:text-flowops-700">WorkOrders</Link>
          </nav>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm text-gray-600">
            {user?.name} <span className="text-gray-400">({user?.role} — {user?.companyName})</span>
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
          <>
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

            <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
              <div className="rounded-lg border border-gray-200 bg-white p-5">
                <h3 className="mb-3 text-sm font-medium text-gray-900">WorkOrders recentes</h3>
                {summary.recentWorkOrders.length === 0 && (
                  <p className="text-sm text-gray-400">Nenhuma WorkOrder cadastrada ainda.</p>
                )}
                <ul className="space-y-2">
                  {summary.recentWorkOrders.map((wo) => (
                    <li key={wo.uuid} className="flex items-center justify-between text-sm">
                      <div>
                        <p className="text-gray-800">{wo.title}</p>
                        <p className="text-xs text-gray-500">{wo.clientName}</p>
                      </div>
                      <span className="text-xs text-gray-500">{STATUS_LABELS[wo.status]}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="rounded-lg border border-gray-200 bg-white p-5">
                <h3 className="mb-3 text-sm font-medium text-gray-900">Próximas entregas agendadas</h3>
                {summary.upcomingDeliveries.length === 0 && (
                  <p className="text-sm text-gray-400">Nenhuma entrega agendada nos próximos dias.</p>
                )}
                <ul className="space-y-2">
                  {summary.upcomingDeliveries.map((wo) => (
                    <li key={wo.uuid} className="flex items-center justify-between text-sm">
                      <div>
                        <p className="text-gray-800">{wo.title}</p>
                        <p className="text-xs text-gray-500">{wo.clientName}</p>
                      </div>
                      <span className="text-xs text-gray-500">{formatDate(wo.scheduledEnd)}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </>
        )}

        <p className="mt-8 text-xs text-gray-400">
          Login, JWT, Clientes, WorkOrders e Etapas confirmados end-to-end (Sprints 1 a 4).
        </p>
      </main>
    </div>
  )
}
