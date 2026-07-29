import { useState } from 'react'
import api from '../services/api.js'

/**
 * Baixa um CSV da API. Precisa passar pelo axios (e não por um <a href>)
 * porque a rota exige o header Authorization — um link simples abriria a URL
 * sem o token e receberia 401.
 */
export default function ExportButton({ path, params, label = 'Exportar CSV', onError }) {
  const [downloading, setDownloading] = useState(false)

  async function handleClick() {
    setDownloading(true)
    try {
      const response = await api.get(path, { params, responseType: 'blob' })

      // O nome vem no Content-Disposition montado pelo backend; se o header
      // nao estiver acessivel (CORS), cai num nome padrao.
      const disposition = response.headers['content-disposition'] || ''
      const match = disposition.match(/filename="?([^"]+)"?/)
      const fileName = match ? match[1] : 'export.csv'

      const url = window.URL.createObjectURL(
        new Blob([response.data], { type: 'text/csv;charset=utf-8' })
      )
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      onError?.('Não foi possível exportar.')
    } finally {
      setDownloading(false)
    }
  }

  return (
    <button
      onClick={handleClick}
      disabled={downloading}
      className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 disabled:opacity-60"
    >
      {downloading ? 'Exportando...' : label}
    </button>
  )
}
