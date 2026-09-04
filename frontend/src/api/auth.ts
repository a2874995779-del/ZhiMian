import { post } from './http'
import type { LoginResult } from '../types/user'

export function login(username: string, password: string): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', { username, password })
}
