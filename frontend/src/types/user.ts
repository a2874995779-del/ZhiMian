export interface User {
  id: number
  username: string
  nickname: string
  avatar: string | null
  role: string
}

export interface LoginResult {
  token: string
  user: User
}
