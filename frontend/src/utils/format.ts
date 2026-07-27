// 后端 LocalDateTime 被 Jackson 序列化成 "2026-07-24T10:30:00" 这种 ISO 字符串,统一格式化成 "YYYY-MM-DD HH:mm"
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
