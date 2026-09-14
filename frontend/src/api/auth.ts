import type { LoginRequest, LoginResponse, MeResponse } from './types'
import { post, get } from './client'

export const authApi = {
  login: (body: LoginRequest): Promise<LoginResponse> =>
    post<LoginResponse>('/api/auth/login', body),

  logout: (): Promise<void> =>
    post<void>('/api/auth/logout', {}),

  me: (): Promise<MeResponse> =>
    get<MeResponse>('/api/auth/me'),
}
