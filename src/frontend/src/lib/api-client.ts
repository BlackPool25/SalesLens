import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

interface FailedQueueItem {
  resolve: (token: string) => void
  reject: (error: unknown) => void
}

const apiClient = axios.create({
  baseURL: '',
})

// ---- Module-level token (memory only, NOT localStorage) ----
let accessToken: string | null = null
let logoutHandler: (() => void) | null = null

// ---- 401 queue state ----
let isRefreshing = false
let failedQueue: FailedQueueItem[] = []

function processQueue(error: unknown, token: string | null = null) {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token!)
    }
  })
  failedQueue = []
}

// ---- Public setters / getter for AuthProvider ----

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function setLogoutHandler(handler: () => void) {
  logoutHandler = handler
}

export function getAccessToken(): string | null {
  return accessToken
}

// ---- Request interceptor: attach Bearer token if available ----

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (accessToken && config.headers) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ---- Response interceptor: handle 401 with silent token refresh ----

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean
    }

    // Only handle 401 errors from non-refresh endpoints
    if (
      !error.response ||
      error.response.status !== 401 ||
      !originalRequest ||
      originalRequest._retry ||
      originalRequest.url?.includes('/auth/refresh')
    ) {
      return Promise.reject(error)
    }

    // If a refresh is already in flight, queue this request
    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then((newToken) => {
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
        }
        return apiClient(originalRequest)
      })
    }

    // Start silent refresh
    originalRequest._retry = true
    isRefreshing = true

    try {
      const response = await apiClient.post<{ accessToken: string }>('/auth/refresh')
      const newToken = response.data.accessToken

      accessToken = newToken
      processQueue(null, newToken)

      // Retry the original request with the new token
      if (originalRequest.headers) {
        originalRequest.headers.Authorization = `Bearer ${newToken}`
      }
      return apiClient(originalRequest)
    } catch (refreshError) {
      processQueue(refreshError, null)
      logoutHandler?.()
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)

export { apiClient }
