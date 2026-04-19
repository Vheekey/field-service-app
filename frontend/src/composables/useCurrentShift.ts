import { computed } from 'vue'

import { mockShift } from '@/mocks/shift'
import { useCurrentShiftQuery } from '@/queries/useCurrentShiftQuery'

export function useCurrentShift() {
  const currentShiftQuery = useCurrentShiftQuery()
  const shift = computed(() => currentShiftQuery.data.value ?? mockShift)

  return {
    error: currentShiftQuery.error,
    isFetching: currentShiftQuery.isFetching,
    isLoading: currentShiftQuery.isLoading,
    refetch: currentShiftQuery.refetch,
    shift,
  }
}
