import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from 'react'
import { apiClient, setAccessToken, setLogoutHandler } from './api-client'

// ---- Types ----

export interface UserProfile {
  id: number
  username: string
  email: string
  roles: string[]
}

interface AuthContextType {
  user: UserProfile | null
  accessToken: string | null
  isAuthenticated: boolean
  loading: boolean
  login: (identifier: string, password: string) => Promise<void>
  logout: () => void
}

// ---- Context ----

const AuthContext = createContext<AuthContextType | null>(null)

// ---- Provider ----

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const logout = useCallback(() => {
    setAccessToken(null)
    setToken(null)
    setUser(null)
  }, [])

  // Register the logout handler so the API interceptor can call it on refresh failure
  useEffect(() => {
    setLogoutHandler(logout)
  }, [logout])

  const login = useCallback(
    async (identifier: string, password: string) => {
      const response = await apiClient.post<{ accessToken: string }>('/auth/login', {
        identifier,
        password,
      })
      const newToken = response.data.accessToken

      // Sync module-level token for the interceptor
      setAccessToken(newToken)
      setToken(newToken)

      // Fetch the full user profile
      const meResponse = await apiClient.get<UserProfile>('/auth/me')
      setUser(meResponse.data)
    },
    [],
  )

  // On mount: attempt silent refresh via httpOnly cookie
  useEffect(() => {
    const attemptRefresh = async () => {
      try {
        const response = await apiClient.post<{ accessToken: string }>('/auth/refresh')
        const newToken = response.data.accessToken

        setAccessToken(newToken)
        setToken(newToken)

        const meResponse = await apiClient.get<UserProfile>('/auth/me')
        setUser(meResponse.data)
      } catch {
        // Silent refresh failed — user is unauthenticated
        setAccessToken(null)
        setToken(null)
        setUser(null)
      } finally {
        setLoading(false)
      }
    }

    attemptRefresh()
  }, [])

  const value: AuthContextType = {
    user,
    accessToken: token,
    isAuthenticated: !!token,
    loading,
    login,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// ---- Hook ----

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
