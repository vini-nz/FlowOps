import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext.jsx'
import NotificationBell from '../components/NotificationBell.jsx'
import api from '../services/api.js'

export default function Workflow() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN_EMPRESA'

  const [templates, setTemplates] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [newTemplateName, setNewTemplateName] = useState('')
  const [stepDraft, setStepDraft] = useState({})
  const [checklistDraft, setChecklistDraft] = useState({})

  function loadTemplates() {
    setLoading(true)
    api.get('/workflows')
      .then((response) => setTemplates(response.data))
      .catch(() => setError('Não foi possível carregar os workflows.'))
      .finally(() => setLoading(false))
  }

  useEffect(loadTemplates, [])

  // O backend devolve o template inteiro em toda escrita, entao basta
  // substituir na lista - evita um GET extra a cada clique.
  function replaceTemplate(updated) {
    setTemplates((prev) => prev.map((t) => (t.uuid === updated.uuid ? updated : t)))
  }

  async function run(action, onSuccess) {
    setError('')
    try {
      const response = await action()
      onSuccess?.(response)
    } catch (err) {
      setError(err.response?.data?.message || 'Não foi possível concluir a operação.')
    }
  }

  const createTemplate = (e) => {
    e.preventDefault()
    if (!newTemplateName.trim()) return
    run(
      () => api.post('/workflows', { name: newTemplateName.trim() }),
      () => { setNewTemplateName(''); loadTemplates() }
    )
  }

  const setAsDefault = (template) =>
    run(
      () => api.put(`/workflows/${template.uuid}`, { name: template.name, isDefault: true }),
      loadTemplates
    )

  const renameTemplate = (template) => {
    const name = prompt('Novo nome do workflow:', template.name)
    if (!name?.trim()) return
    run(
      () => api.put(`/workflows/${template.uuid}`, { name: name.trim(), isDefault: template.isDefault }),
      loadTemplates
    )
  }

  const deleteTemplate = (template) => {
    if (!confirm(`Excluir o workflow "${template.name}"? Ordens de Serviço já criadas mantêm suas etapas.`)) return
    run(() => api.delete(`/workflows/${template.uuid}`), loadTemplates)
  }

  const addStep = (template) => {
    const title = (stepDraft[template.uuid] || '').trim()
    if (!title) return
    run(
      () => api.post(`/workflows/${template.uuid}/steps`, { title }),
      (r) => { setStepDraft((p) => ({ ...p, [template.uuid]: '' })); replaceTemplate(r.data) }
    )
  }

  const renameStep = (template, step) => {
    const title = prompt('Novo nome da etapa:', step.title)
    if (!title?.trim()) return
    run(
      () => api.put(`/workflows/${template.uuid}/steps/${step.uuid}`, { title: title.trim() }),
      (r) => replaceTemplate(r.data)
    )
  }

  const deleteStep = (template, step) => {
    if (!confirm(`Remover a etapa "${step.title}" deste workflow?`)) return
    run(
      () => api.delete(`/workflows/${template.uuid}/steps/${step.uuid}`),
      (r) => replaceTemplate(r.data)
    )
  }

  const moveStep = (template, step, direction) =>
    run(
      () => api.patch(`/workflows/${template.uuid}/steps/${step.uuid}/move?direction=${direction}`),
      (r) => replaceTemplate(r.data)
    )

  const addChecklistItem = (template, step) => {
    const key = `${template.uuid}:${step.uuid}`
    const description = (checklistDraft[key] || '').trim()
    if (!description) return
    run(
      () => api.post(`/workflows/${template.uuid}/steps/${step.uuid}/checklist`, { description }),
      (r) => { setChecklistDraft((p) => ({ ...p, [key]: '' })); replaceTemplate(r.data) }
    )
  }

  const deleteChecklistItem = (template, step, item) =>
    run(
      () => api.delete(`/workflows/${template.uuid}/steps/${step.uuid}/checklist/${item.uuid}`),
      (r) => replaceTemplate(r.data)
    )

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-medium text-flowops-900">FlowOps</h1>
          <nav className="mt-1 flex gap-4 text-sm">
            <Link to="/dashboard" className="text-gray-500 hover:text-flowops-700">Dashboard</Link>
            <Link to="/clients" className="text-gray-500 hover:text-flowops-700">Clientes</Link>
            <Link to="/work-orders" className="text-gray-500 hover:text-flowops-700">WorkOrders</Link>
            <Link to="/catalog" className="text-gray-500 hover:text-flowops-700">Catálogo</Link>
            <span className="font-medium text-flowops-700">Workflow</span>
            <Link to="/profile" className="text-gray-500 hover:text-flowops-700">Perfil</Link>
          </nav>
        </div>
        <NotificationBell />
      </header>

      <main className="p-6">
        {error && <p className="mb-4 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        <p className="mb-4 max-w-3xl text-sm text-gray-600">
          As etapas definidas aqui são copiadas para cada nova Ordem de Serviço criada com o workflow
          padrão. Alterar um workflow <strong>não</strong> altera Ordens de Serviço já existentes.
        </p>

        {!isAdmin && (
          <p className="mb-4 rounded bg-amber-50 px-3 py-2 text-sm text-amber-800">
            Somente o Admin da empresa pode alterar workflows. Você está vendo a configuração atual.
          </p>
        )}

        {isAdmin && (
          <form onSubmit={createTemplate} className="mb-6 flex gap-2">
            <input
              value={newTemplateName}
              onChange={(e) => setNewTemplateName(e.target.value)}
              placeholder="Nome do novo workflow (ex: Assistência Técnica)"
              className="w-80 rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
            />
            <button
              type="submit"
              className="rounded bg-flowops-600 px-4 py-2 text-sm font-medium text-white hover:bg-flowops-700"
            >
              Criar workflow
            </button>
          </form>
        )}

        {loading && <p className="text-sm text-gray-400">Carregando...</p>}

        {!loading && templates.length === 0 && (
          <div className="rounded-lg border border-dashed border-gray-300 bg-white p-6 text-center">
            <p className="text-sm font-medium text-gray-700">Nenhum workflow configurado</p>
            <p className="mt-1 text-sm text-gray-500">
              Sem um workflow padrão, as Ordens de Serviço nascem sem nenhuma etapa.
              {isAdmin ? ' Crie o primeiro acima — ele já vira o padrão da empresa.' : ' Peça ao Admin da empresa para criar o primeiro.'}
            </p>
          </div>
        )}

        <div className="space-y-4">
          {templates.map((template) => (
            <div key={template.uuid} className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <h2 className="text-base font-medium text-gray-900">{template.name}</h2>
                  {template.isDefault && (
                    <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">
                      Padrão
                    </span>
                  )}
                </div>

                {isAdmin && (
                  <div className="flex gap-3 text-sm">
                    {!template.isDefault && (
                      <button onClick={() => setAsDefault(template)} className="text-flowops-600 hover:underline">
                        Tornar padrão
                      </button>
                    )}
                    <button onClick={() => renameTemplate(template)} className="text-flowops-600 hover:underline">
                      Renomear
                    </button>
                    <button onClick={() => deleteTemplate(template)} className="text-red-600 hover:underline">
                      Excluir
                    </button>
                  </div>
                )}
              </div>

              <div className="mt-4 space-y-3">
                {template.steps.length === 0 && (
                  <p className="text-sm text-gray-400">
                    Nenhuma etapa neste workflow — Ordens de Serviço criadas com ele nascerão sem etapas.
                  </p>
                )}

                {template.steps.map((step, index) => (
                  <div key={step.uuid} className="rounded border border-gray-200 bg-gray-50 p-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="text-sm font-medium text-gray-800">
                        {step.stepOrder}. {step.title}
                      </span>

                      {isAdmin && (
                        <div className="flex items-center gap-2 text-xs">
                          <button
                            onClick={() => moveStep(template, step, 'up')}
                            disabled={index === 0}
                            title="Mover para cima"
                            className="rounded border border-gray-300 px-2 py-1 text-gray-600 hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40"
                          >
                            ↑
                          </button>
                          <button
                            onClick={() => moveStep(template, step, 'down')}
                            disabled={index === template.steps.length - 1}
                            title="Mover para baixo"
                            className="rounded border border-gray-300 px-2 py-1 text-gray-600 hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40"
                          >
                            ↓
                          </button>
                          <button onClick={() => renameStep(template, step)} className="text-flowops-600 hover:underline">
                            Renomear
                          </button>
                          <button onClick={() => deleteStep(template, step)} className="text-red-600 hover:underline">
                            Remover
                          </button>
                        </div>
                      )}
                    </div>

                    <ul className="mt-2 space-y-1">
                      {step.checklistItems.length === 0 && (
                        <li className="text-xs text-gray-400">Sem itens de checklist.</li>
                      )}
                      {step.checklistItems.map((item) => (
                        <li key={item.uuid} className="flex items-center justify-between text-xs text-gray-700">
                          <span>• {item.description}</span>
                          {isAdmin && (
                            <button
                              onClick={() => deleteChecklistItem(template, step, item)}
                              className="text-red-600 hover:underline"
                            >
                              Remover
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>

                    {isAdmin && (
                      <div className="mt-2 flex gap-2">
                        <input
                          value={checklistDraft[`${template.uuid}:${step.uuid}`] || ''}
                          onChange={(e) =>
                            setChecklistDraft((p) => ({ ...p, [`${template.uuid}:${step.uuid}`]: e.target.value }))
                          }
                          placeholder="Novo item de checklist..."
                          className="flex-1 rounded border border-gray-300 px-2 py-1 text-xs focus:border-flowops-600 focus:outline-none"
                        />
                        <button
                          onClick={() => addChecklistItem(template, step)}
                          className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-100"
                        >
                          Adicionar
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>

              {isAdmin && (
                <div className="mt-3 flex gap-2">
                  <input
                    value={stepDraft[template.uuid] || ''}
                    onChange={(e) => setStepDraft((p) => ({ ...p, [template.uuid]: e.target.value }))}
                    placeholder="Nova etapa (ex: Diagnóstico)"
                    className="w-64 rounded border border-gray-300 px-3 py-1.5 text-sm focus:border-flowops-600 focus:outline-none"
                  />
                  <button
                    onClick={() => addStep(template)}
                    className="rounded border border-flowops-600 px-3 py-1.5 text-sm text-flowops-700 hover:bg-flowops-50"
                  >
                    Adicionar etapa
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      </main>
    </div>
  )
}
