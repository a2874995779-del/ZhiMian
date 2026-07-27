import axios from 'axios'
import { ElMessage } from 'element-plus'

// 后端统一返回体 { code, message, data },code=0 为成功
// 拦截器里直接拆包:业务层拿到的就是 data 本身
const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 需要登录的接口都靠这个头带 token,不直接依赖 Pinia store(避免和 stores/auth.ts 互相 import 成环),
// 登录态的唯一事实来源就是 localStorage
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('zm-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

const NOT_LOGIN_CODES = new Set([40100, 40101])

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      if (NOT_LOGIN_CODES.has(body.code)) {
        // 未登录/登录过期:清掉失效 token,下次请求不会带着一个注定被拒绝的 token 再试一次。
        // 用动态 import 拿 auth store 调 logout(),而不是直接摸 localStorage——
        // 直接摸的话 Pinia 里的响应式 token/user 不会跟着变,界面会继续显示"已登录"。
        // 动态 import 是为了绕开 http.ts -> stores/auth.ts -> api/auth.ts -> http.ts 这个环形依赖。
        import('../stores/auth').then(({ useAuthStore }) => useAuthStore().logout())
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return body
  },
  (err) => {
    ElMessage.error('网络异常,请确认后端服务已启动(localhost:8080)')
    return Promise.reject(err)
  },
)

export default http
