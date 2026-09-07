// Pass AxiosError through unchanged so handleApiError can classify by status code.
// Use TIMEOUTS.API_REQUEST (30 s) as the single source of truth for HTTP timeout.
import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'

import { TIMEOUTS } from '@/shared/constants'

class ApiClient {
  private readonly client: AxiosInstance

  constructor() {
    this.client = axios.create({
      timeout: TIMEOUTS.API_REQUEST,
    })

    this.client.interceptors.response.use((response) => response.data)
  }

  async get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.client.get<never, T>(url, config)
  }

  async getBlob(url: string, config?: AxiosRequestConfig): Promise<Blob> {
    return this.client.get<never, Blob>(url, { ...config, responseType: 'blob' })
  }

  async post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.client.post<never, T>(url, data, config)
  }

  async postBlob(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Blob> {
    return this.client.post<never, Blob>(url, data, { ...config, responseType: 'blob' })
  }

  async put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.client.put<never, T>(url, data, config)
  }

  async delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.client.delete<never, T>(url, config)
  }

  async patch<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.client.patch<never, T>(url, data, config)
  }
}

export default new ApiClient()
