import http from './http'
import type { LoginResult } from '../types/user'

export function login(username: string, password: string): Promise<LoginResult> {
  return http.post('/auth/login', { username, password }) as unknown as Promise<LoginResult>
}
