import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { Envelope } from './types'

const baseURL = `${import.meta.env.VITE_API_URL ?? 'http://localhost:8080'}/api/v1`
export const api = axios.create({ baseURL, headers: { 'Content-Type': 'application/json' } })
const refreshClient = axios.create({ baseURL, headers: { 'Content-Type': 'application/json' } })

let accessToken: string | null = null
let refreshPromise: Promise<string> | null = null

type RetryConfig = InternalAxiosRequestConfig & { _retried?: boolean; skipAuthRefresh?: boolean }

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getRefreshToken() {
  return sessionStorage.getItem('olgax.refreshToken')
}

export function setRefreshToken(token: string | null) {
  if (token) sessionStorage.setItem('olgax.refreshToken', token)
  else sessionStorage.removeItem('olgax.refreshToken')
}

function requestRefresh(): Promise<string> {
  if (!refreshPromise) {
    const refreshToken = getRefreshToken()
    if (!refreshToken) return Promise.reject(new Error('NO_REFRESH_TOKEN'))
    refreshPromise = refreshClient.post<Envelope<{ accessToken: string; refreshToken: string }>>('/auth/refresh', { refreshToken })
      .then(({ data }) => {
        if (!data.success || !data.data?.accessToken || !data.data.refreshToken) {
          throw new Error(data.error?.code ?? 'REFRESH_FAILED')
        }
        accessToken = data.data.accessToken
        setRefreshToken(data.data.refreshToken)
        return data.data.accessToken
      })
      .finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetryConfig | undefined
    if (error.response?.status !== 401 || !config || config._retried || config.skipAuthRefresh) {
      return Promise.reject(error)
    }
    config._retried = true
    try {
      const token = await requestRefresh()
      config.headers.Authorization = `Bearer ${token}`
      return await api(config)
    } catch (refreshError) {
      accessToken = null
      setRefreshToken(null)
      window.dispatchEvent(new Event('olgax:session-expired'))
      return Promise.reject(refreshError)
    }
  },
)
