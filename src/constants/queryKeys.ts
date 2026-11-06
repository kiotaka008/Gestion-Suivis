export const queryKeys = {
  auth: {
    me: ['auth', 'me'] as const,
  },
  projects: {
    all: ['projects'] as const,
    list: (filters?: Record<string, unknown>) => ['projects', 'list', filters] as const,
    detail: (id: string) => ['projects', 'detail', id] as const,
  },
  activities: {
    all: ['activities'] as const,
    list: (filters?: Record<string, unknown>) => ['activities', 'list', filters] as const,
    detail: (id: string) => ['activities', 'detail', id] as const,
  },
  users: {
    all: ['users'] as const,
    list: (filters?: Record<string, unknown>) => ['users', 'list', filters] as const,
    detail: (id: string) => ['users', 'detail', id] as const,
  },
  notifications: {
    all: ['notifications'] as const,
  },
} as const;