import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext.jsx'
import NotificationBell from '../components/NotificationBell.jsx'
import api from '../services/api.js'

function formatDateTime(isoDateTime) {
  if (!isoDateTime) return '—'
  return new Date(isoDateTime).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

export default function Profile() {
  const { user, logout } = useAuth()

  const [profile, setProfile] = useState(null)
  const [form, setForm] = useState({ name: '', email: '', currentPassword: '' })
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })

  const [profileMessage, setProfileMessage] = useState(null)
  const [passwordMessage, setPasswordMessage] = useState(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    api.get('/profile')
      .then((response) => {
        setProfile(response.data)
        setForm({ name: response.data.name, email: response.data.email, currentPassword: '' })
      })
      .catch(() => setProfileMessage({ type: 'error', text: 'Não foi possível carregar o perfil.' }))
  }, [])

  const emailChanged = profile && form.email.trim().toLowerCase() !== profile.email.toLowerCase()

  // O backend devolve um token novo quando a alteracao invalida o anterior
  // (troca de e-mail ou de senha). Sem trocar aqui, a proxima requisicao
  // levaria 401 e o usuario seria deslogado sem entender o motivo.
  function adoptNewTokenIfAny(data) {
    if (data.accessToken) {
      localStorage.setItem('flowops_token', data.accessToken)
    }
  }

  async function handleProfileSubmit(e) {
    e.preventDefault()
    setSaving(true)
    setProfileMessage(null)

    try {
      const { data } = await api.put('/profile', {
        name: form.name,
        email: form.email,
        currentPassword: emailChanged ? form.currentPassword : null
      })
      adoptNewTokenIfAny(data)
      setProfile(data)
      setForm({ name: data.name, email: data.email, currentPassword: '' })
      setProfileMessage({ type: 'ok', text: 'Perfil atualizado.' })
    } catch (err) {
      setProfileMessage({
        type: 'error',
        text: err.response?.data?.message || 'Não foi possível salvar o perfil.'
      })
    } finally {
      setSaving(false)
    }
  }

  async function handlePasswordSubmit(e) {
    e.preventDefault()
    setPasswordMessage(null)

    if (passwordForm.newPassword !== passwordForm.confirm) {
      setPasswordMessage({ type: 'error', text: 'A confirmação não confere com a nova senha.' })
      return
    }

    setSaving(true)
    try {
      const { data } = await api.patch('/profile/password', {
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword
      })
      adoptNewTokenIfAny(data)
      setProfile(data)
      setPasswordForm({ currentPassword: '', newPassword: '', confirm: '' })
      setPasswordMessage({
        type: 'ok',
        text: 'Senha alterada. Outras sessões suas foram desconectadas.'
      })
    } catch (err) {
      setPasswordMessage({
        type: 'error',
        text: err.response?.data?.message || 'Não foi possível alterar a senha.'
      })
    } finally {
      setSaving(false)
    }
  }

  function Message({ message }) {
    if (!message) return null
    const style = message.type === 'ok'
      ? 'bg-emerald-50 text-emerald-800'
      : 'bg-red-50 text-red-700'
    return <p className={`mb-3 rounded px-3 py-2 text-sm ${style}`}>{message.text}</p>
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
            <Link to="/catalog" className="text-gray-500 hover:text-flowops-700">Catálogo</Link>
            <Link to="/workflow" className="text-gray-500 hover:text-flowops-700">Workflow</Link>
            <span className="font-medium text-flowops-700">Perfil</span>
          </nav>
        </div>
        <div className="flex items-center gap-4">
          <NotificationBell />
          <button
            onClick={logout}
            className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
          >
            Sair
          </button>
        </div>
      </header>

      <main className="max-w-2xl p-6">
        {!profile && <p className="text-sm text-gray-400">Carregando...</p>}

        {profile && (
          <div className="space-y-6">
            <section className="rounded-lg border border-gray-200 bg-white p-5">
              <h2 className="mb-1 text-base font-medium text-gray-900">Dados pessoais</h2>
              <p className="mb-4 text-xs text-gray-500">
                {profile.role} · {profile.companyName} · último acesso em {formatDateTime(profile.lastLoginAt)}
              </p>

              <Message message={profileMessage} />

              <form onSubmit={handleProfileSubmit}>
                <label className="mb-1 block text-sm text-gray-700">Nome *</label>
                <input
                  required
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                />

                <label className="mb-1 block text-sm text-gray-700">E-mail *</label>
                <input
                  required
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  className="mb-2 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                />

                {emailChanged && (
                  <div className="mb-3 rounded bg-amber-50 px-3 py-2 text-xs text-amber-800">
                    <p className="font-medium">O e-mail é usado para entrar no sistema.</p>
                    <p className="mt-1">
                      Confirme sua senha para alterá-lo. Nos próximos acessos, use o novo e-mail.
                    </p>
                    <input
                      required
                      type="password"
                      value={form.currentPassword}
                      onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
                      placeholder="Senha atual"
                      className="mt-2 w-full rounded border border-amber-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                    />
                  </div>
                )}

                <button
                  type="submit"
                  disabled={saving}
                  className="rounded bg-flowops-600 px-4 py-2 text-sm font-medium text-white hover:bg-flowops-700 disabled:opacity-60"
                >
                  {saving ? 'Salvando...' : 'Salvar'}
                </button>
              </form>
            </section>

            <section className="rounded-lg border border-gray-200 bg-white p-5">
              <h2 className="mb-1 text-base font-medium text-gray-900">Trocar senha</h2>
              <p className="mb-4 text-xs text-gray-500">
                Alterada pela última vez em {formatDateTime(profile.passwordChangedAt)}. Ao trocar,
                suas outras sessões são desconectadas.
              </p>

              <Message message={passwordMessage} />

              <form onSubmit={handlePasswordSubmit}>
                <label className="mb-1 block text-sm text-gray-700">Senha atual *</label>
                <input
                  required
                  type="password"
                  value={passwordForm.currentPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })}
                  className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                />

                <label className="mb-1 block text-sm text-gray-700">Nova senha *</label>
                <input
                  required
                  type="password"
                  minLength={8}
                  value={passwordForm.newPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
                  className="mb-1 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                />
                <p className="mb-3 text-xs text-gray-400">Mínimo de 8 caracteres.</p>

                <label className="mb-1 block text-sm text-gray-700">Confirmar nova senha *</label>
                <input
                  required
                  type="password"
                  value={passwordForm.confirm}
                  onChange={(e) => setPasswordForm({ ...passwordForm, confirm: e.target.value })}
                  className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm focus:border-flowops-600 focus:outline-none"
                />

                <button
                  type="submit"
                  disabled={saving}
                  className="rounded bg-flowops-600 px-4 py-2 text-sm font-medium text-white hover:bg-flowops-700 disabled:opacity-60"
                >
                  {saving ? 'Alterando...' : 'Trocar senha'}
                </button>
              </form>
            </section>
          </div>
        )}
      </main>
    </div>
  )
}
