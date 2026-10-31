import type { AxiosRequestConfig } from 'axios'
import { api } from './client'
import type { Envelope } from './types'

export async function getData<T>(path: string, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await api.get<Envelope<T>>(path, config)
  if (!data.success || data.data === null) throw new Error(data.error?.code ?? 'UNKNOWN_ERROR')
  return data.data
}

export async function sendData<T>(path: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await api.post<Envelope<T>>(path, body, config)
  if (!data.success || data.data === null) throw new Error(data.error?.code ?? 'UNKNOWN_ERROR')
  return data.data
}

export async function putData<T>(path: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await api.put<Envelope<T>>(path, body, config)
  if (!data.success || data.data === null) throw new Error(data.error?.code ?? 'UNKNOWN_ERROR')
  return data.data
}

export async function deleteData<T>(path: string, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await api.delete<Envelope<T>>(path, config)
  if (!data.success || data.data === null) throw new Error(data.error?.code ?? 'UNKNOWN_ERROR')
  return data.data
}
