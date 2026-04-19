import { apiRequest } from './client'
import type { ShiftSummary } from '@/types/shift'

export function getCurrentShift() {
  return apiRequest<ShiftSummary>('/shifts/current')
}
