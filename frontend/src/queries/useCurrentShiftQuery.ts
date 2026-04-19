import { useQuery } from '@tanstack/vue-query'

import { mockShift } from '@/mocks/shift'
import { queryKeys } from './queryKeys'
import type { ShiftSummary } from '@/types/shift'

const USE_MOCK_SHIFT = import.meta.env.VITE_USE_MOCK_API !== 'false'

async function fetchCurrentShift(): Promise<ShiftSummary> {
  if (USE_MOCK_SHIFT) {
    return Promise.resolve(mockShift)
  }

  const { getCurrentShift } = await import('@/api/shifts.api')
  return getCurrentShift()
}

export function useCurrentShiftQuery() {
  return useQuery({
    queryKey: queryKeys.shifts.current(),
    queryFn: fetchCurrentShift,
  })
}
