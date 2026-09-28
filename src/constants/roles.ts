import type { Role } from '../types/models';

export const ROLES: Record<Role, Role> = {
  admin: 'admin',
  manager: 'manager',
  member: 'member',
};

export const ROLE_LABELS: Record<Role, string> = {
  admin: 'Administrateur',
  manager: 'Responsable',
  member: 'Membre',
};