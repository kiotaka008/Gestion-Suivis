import type { Role } from '../types/models';

/**
 * ⚠️ ATTENTION — SÉCURITÉ
 *
 * Ces helpers ne servent QU'À adapter l'affichage côté UI.
 * Ils ne constituent PAS une protection de sécurité.
 *
 * Ny back dia mila mivérifier ny :
 *   - l'identité de l'utilisateur
 *   - son rôle
 *   - son organisation (multi-tenant)
 *   - la ressource demandée
 *
 * Un utilisateur malveillant peut modifier le JS dans son navigateur.
 */

const hierarchy: Record<Role, number> = {
  member: 0,
  manager: 1,
  admin: 2,
};

export function hasRole(userRole: Role | undefined, required: Role): boolean {
  if (!userRole) return false;
  return hierarchy[userRole] >= hierarchy[required];
}

export function canManageUsers(role: Role | undefined): boolean {
  return hasRole(role, 'admin');
}

export function canManageProjects(role: Role | undefined): boolean {
  return hasRole(role, 'manager');
}

export function canDeleteProject(role: Role | undefined): boolean {
  return hasRole(role, 'admin');
}