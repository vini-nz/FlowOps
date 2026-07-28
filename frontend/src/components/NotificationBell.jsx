import { useEffect, useRef, useState } from 'react'
import api from '../services/api.js'

function formatWhen(isoDateTime) {
  if (!isoDateTime) return ''
  const diffMs = Date.now() - new Date(isoDateTime).getTime()
  const minutes = Math.floor(diffMs / 60000)
  if (minutes < 1) return 'agora'
  if (minutes < 60) return `há ${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `há ${hours}h`
  return new Date(isoDateTime).toLocaleDateString('pt-BR')
}

export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState([])
  const [unread, setUnread] = useState(0)
  const containerRef = useRef(null)

  function loadUnreadCount() {
    api.get('/notifications/unread-count')
      .then((response) => setUnread(response.data.count))
      .catch(() => {})
  }

  function loadItems() {
    api.get('/notifications', { params: { size: 10 } })
      .then((response) => setItems(response.data.content))
      .catch(() => {})
  }

  useEffect(() => {
    loadUnreadCount()
    // Sem WebSocket ainda (Notificacoes em Tempo Real e V3): uma consulta
    // leve ao contador de tempos em tempos basta e nao segura conexao aberta.
    const timer = setInterval(loadUnreadCount, 60000)
    return () => clearInterval(timer)
  }, [])

  // Fecha ao clicar fora, senao o painel fica presete na tela toda navegacao.
  useEffect(() => {
    function onClickOutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  function toggle() {
    const next = !open
    setOpen(next)
    if (next) loadItems()
  }

  async function markAsRead(notification) {
    if (notification.read) return
    try {
      await api.patch(`/notifications/${notification.uuid}/read`)
      loadItems()
      loadUnreadCount()
    } catch {
      /* silencioso: marcar como lida nao e critico */
    }
  }

  async function markAllAsRead() {
    try {
      await api.patch('/notifications/read-all')
      loadItems()
      loadUnreadCount()
    } catch {
      /* idem */
    }
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        onClick={toggle}
        title="Notificações"
        className="relative rounded border border-gray-300 px-2 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
      >
        🔔
        {unread > 0 && (
          <span className="absolute -right-1.5 -top-1.5 min-w-[18px] rounded-full bg-red-600 px-1 text-[10px] font-medium leading-[18px] text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-20 mt-2 w-80 rounded-lg border border-gray-200 bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-gray-100 px-3 py-2">
            <span className="text-sm font-medium text-gray-800">Notificações</span>
            {unread > 0 && (
              <button onClick={markAllAsRead} className="text-xs text-flowops-600 hover:underline">
                Marcar todas como lidas
              </button>
            )}
          </div>

          <ul className="max-h-80 overflow-y-auto">
            {items.length === 0 && (
              <li className="px-3 py-6 text-center text-sm text-gray-400">Nenhuma notificação.</li>
            )}
            {items.map((item) => (
              <li
                key={item.uuid}
                onClick={() => markAsRead(item)}
                className={`cursor-pointer border-b border-gray-50 px-3 py-2 text-xs hover:bg-gray-50 ${
                  item.read ? 'text-gray-500' : 'bg-flowops-50/40 font-medium text-gray-800'
                }`}
              >
                <p>{item.message}</p>
                <span className="text-[10px] text-gray-400">{formatWhen(item.createdAt)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
