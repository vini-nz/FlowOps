import { createContext, useContext, useEffect, useState } from 'react'
import api from '../services/api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  // Ao carregar a aplicacao, tenta restaurar a sessao a partir do token salvo.
  useEffect(() => {
    const token = localStorage.getItem('flowops_token')

    if (!token) {
      setLoading(false)
      return
    }

    api.get('/auth/me')
      .then((response) => setUser(response.data))
      .catch(() => {
        localStorage.removeItem('flowops_token')
        localStorage.removeItem('flowops_user')
      })
      .finally(() => setLoading(false))
  }, [])

  async function login(email, password) {
    const response = await api.post('/auth/login', { email, password })
    const { accessToken, user: loggedUser } = response.data

    localStorage.setItem('flowops_token', accessToken)
    localStorage.setItem('flowops_user', JSON.stringify(loggedUser))
    setUser(loggedUser)

    return loggedUser
  }

  function logout() {
    localStorage.removeItem('flowops_token')
    localStorage.removeItem('flowops_user')
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth precisa ser usado dentro de um AuthProvider')
  }
  return context
}
