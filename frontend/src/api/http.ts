import axios from 'axios'
import { ElMessage } from 'element-plus'

// 后端统一返回体 { code, message, data },code=0 为成功
// 拦截器里直接拆包:业务层拿到的就是 data 本身
const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
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
