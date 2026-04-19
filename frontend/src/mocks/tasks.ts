import type { TaskListItem } from '@/types/task'

export const mockTasks: TaskListItem[] = [
  {
    id: '7e181036-93f2-4c87-9b11-91276db29d56',
    type: 'BATTERY_SWAP',
    status: 'IN_PROGRESS',
    priority: 'URGENT',
    title: 'Swap low battery scooter',
    address: 'Sveavagen 44',
    dueAt: '2026-04-19T12:30:00+02:00',
    location: {
      latitude: 59.3401,
      longitude: 18.0603,
    },
    version: 4,
  },
  {
    id: 'c4a4356f-239c-42f6-9470-06302cba078d',
    type: 'REBALANCE',
    status: 'ASSIGNED',
    priority: 'HIGH',
    title: 'Move two bikes to station',
    address: 'Odenplan',
    dueAt: '2026-04-19T13:15:00+02:00',
    location: {
      latitude: 59.342,
      longitude: 18.0495,
    },
    version: 2,
  },
  {
    id: '097f122d-266b-4aa5-8e27-9e06745117e6',
    type: 'INSPECTION',
    status: 'COMPLETED',
    priority: 'NORMAL',
    title: 'Check dock sensor',
    address: 'Kungstradgarden',
    dueAt: '2026-04-19T10:45:00+02:00',
    location: {
      latitude: 59.331,
      longitude: 18.0717,
    },
    version: 5,
  },
  {
    id: '64c14db1-4ee6-4291-8d54-30d06a4732eb',
    type: 'REPAIR',
    status: 'BLOCKED',
    priority: 'LOW',
    title: 'Loose brake report',
    address: 'Hornstull',
    dueAt: '2026-04-19T15:00:00+02:00',
    location: {
      latitude: 59.3159,
      longitude: 18.0346,
    },
    version: 1,
  },
]
