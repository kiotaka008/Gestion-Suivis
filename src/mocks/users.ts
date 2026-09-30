import type { User } from '../types/models';

export const mockUsers: User[] = [
  {
    id: 'u-001',
    email: 'rickael@company.com',
    name: 'Rickael Brayan',
    role: 'admin',
    isActive: true,
    createdAt: '2025-01-10T09:00:00Z',
  },
  {
    id: 'u-002',
    email: 'tiavina@company.com',
    name: 'Tiavina Herinjaka',
    role: 'manager',
    isActive: true,
    createdAt: '2025-01-12T09:00:00Z',
  },
  {
    id: 'u-003',
    email: 'romeo@company.com',
    name: 'Romeo Joseph',
    role: 'manager',
    isActive: true,
    createdAt: '2025-01-15T09:00:00Z',
  },
  {
    id: 'u-004',
    email: 'agathe@company.com',
    name: 'Agathe Tsiresimiady',
    role: 'member',
    isActive: true,
    createdAt: '2025-02-01T09:00:00Z',
  },
  {
    id: 'u-005',
    email: 'jean.rakoto@company.com',
    name: 'Jean Rakoto',
    role: 'member',
    isActive: true,
    createdAt: '2025-02-10T09:00:00Z',
  },
  {
    id: 'u-006',
    email: 'miora@company.com',
    name: 'Miora Rakotoarisoa',
    role: 'member',
    isActive: false,
    createdAt: '2025-03-05T09:00:00Z',
  },
];