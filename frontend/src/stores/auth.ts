import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi } from '../api/auth'
import type { User } from '../types/user'

const TOKEN_KEY = 'zm-token'
const USER_KEY = 'zm-user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const rawUser = localStorage.getItem(USER_KEY)
  const user = ref<User | null>(rawUser ? JSON.parse(rawUser) : null)

  async function login(username: string, password: string) {
    const result = await loginApi(username, password)
    token.value = result.token
    user.value = result.user
    localStorage.setItem(TOKEN_KEY, result.token)
    localStorage.setItem(USER_KEY, JSON.stringify(result.user))
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, login, logout }
})
