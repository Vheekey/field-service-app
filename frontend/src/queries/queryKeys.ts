export const queryKeys = {
  shifts: {
    all: ['shifts'] as const,
    current: () => [...queryKeys.shifts.all, 'current'] as const,
  },
  tasks: {
    all: ['tasks'] as const,
    assigned: (workerId: string) => [...queryKeys.tasks.all, 'assigned', workerId] as const,
    route: (workerId: string) => [...queryKeys.tasks.all, 'route', workerId] as const,
  },
}
