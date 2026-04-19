export type ShiftStatus = 'ACTIVE' | 'ENDED'

export type ShiftSummary = {
  id: string
  workerId: string
  status: ShiftStatus
  startedAt: string
  endedAt?: string
}
