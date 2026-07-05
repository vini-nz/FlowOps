import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1'
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('flowops_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Se o token expirar ou for invalido, o backend responde 401.
// Limpamos a sessao local e deixamos o ProtectedRoute redirecionar ao login.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('flowops_token')
      localStorage.removeItem('flowops_user')
    }
    return Promise.reject(error)
  }
)

export default api
