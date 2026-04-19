import type { ShiftSummary } from '@/types/shift'

export function getShiftStartedTime(shift: ShiftSummary) {
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(shift.startedAt))
}

export function getShiftStatusText(shift: ShiftSummary) {
  return shift.status === 'ACTIVE' ? 'On shift' : 'Shift ended'
}
