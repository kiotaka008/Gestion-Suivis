import { useMemo } from 'react';
import { useAuth } from './useAuth';
import type { Activity, Project } from '../types/models';

/**
 * Droits de l'utilisateur connecté, calqués sur les règles du backend
 * (backend/.../security/ControleAcces.java). Ils servent uniquement à afficher
 * les actions autorisées : le backend reste seul juge et refuse le reste (403).
 */
export interface Permissions {
  isAdmin: boolean;
  isManager: boolean;
  /** Liste des utilisateurs : ADMIN et MANAGER. */
  canListUsers: boolean;
  /** Création, modification, activation et suppression d'utilisateurs : ADMIN. */
  canManageUsers: boolean;
  /** Création de projet : ADMIN et MANAGER. */
  canCreateProject: boolean;
  /** Suppression de projet : ADMIN. */
  canDeleteProject: boolean;
  /** Modification, membres, activités du projet : ADMIN ou chef de projet. */
  canManageProject: (project: Pick<Project, 'ownerId'> | undefined | null) => boolean;
  /** Modification complète et suppression d'une activité : comme la gestion de son projet. */
  canManageActivity: (projectOwnerId: string | undefined) => boolean;
  /** Suivi (statut, progression) : gestionnaire du projet ou responsable de l'activité. */
  canTrackActivity: (activity: Pick<Activity, 'assigneeId'>, projectOwnerId: string | undefined) => boolean;
}

export function usePermissions(): Permissions {
  const { user } = useAuth();

  return useMemo(() => {
    const role = user?.role;
    const id = user?.id;
    const isAdmin = role === 'admin';
    const isManager = role === 'manager';
    const canManageActivity = (projectOwnerId: string | undefined) =>
      isAdmin || (!!id && projectOwnerId === id);

    return {
      isAdmin,
      isManager,
      canListUsers: isAdmin || isManager,
      canManageUsers: isAdmin,
      canCreateProject: isAdmin || isManager,
      canDeleteProject: isAdmin,
      canManageProject: (project) => isAdmin || (!!id && project?.ownerId === id),
      canManageActivity,
      canTrackActivity: (activity, projectOwnerId) =>
        canManageActivity(projectOwnerId) || (!!id && activity.assigneeId === id),
    };
  }, [user?.role, user?.id]);
}
