import { get } from './http'
import type { DashboardStats } from '../types/dashboard'

export function fetchDashboard(): Promise<DashboardStats> {
  return get<DashboardStats>('/dashboard')
}
