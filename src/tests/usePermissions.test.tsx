import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import type { User } from '../types/models';

const utilisateurCourant: { user: User | null } = { user: null };

vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({ user: utilisateurCourant.user }),
}));

const { usePermissions } = await import('../hooks/usePermissions');

function connecter(role: User['role'], id = 'moi') {
  utilisateurCourant.user = {
    id,
    email: `${id}@exemple.com`,
    name: id,
    role,
    isActive: true,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

describe('usePermissions (règles calquées sur ControleAcces du backend)', () => {
  beforeEach(() => {
    utilisateurCourant.user = null;
  });

  it('administrateur : gère tout', () => {
    connecter('admin');
    const { result } = renderHook(() => usePermissions());
    const p = result.current;
    expect(p.canListUsers).toBe(true);
    expect(p.canManageUsers).toBe(true);
    expect(p.canCreateProject).toBe(true);
    expect(p.canDeleteProject).toBe(true);
    expect(p.canManageProject({ ownerId: 'autre' })).toBe(true);
    expect(p.canManageActivity('autre')).toBe(true);
  });

  it('responsable : crée des projets, ne gère que les siens, ne gère pas les utilisateurs', () => {
    connecter('manager');
    const { result } = renderHook(() => usePermissions());
    const p = result.current;
    expect(p.canListUsers).toBe(true);
    expect(p.canManageUsers).toBe(false);
    expect(p.canCreateProject).toBe(true);
    expect(p.canDeleteProject).toBe(false);
    expect(p.canManageProject({ ownerId: 'moi' })).toBe(true);
    expect(p.canManageProject({ ownerId: 'autre' })).toBe(false);
  });

  it('membre : ni liste des utilisateurs ni création de projet, suivi de ses activités seulement', () => {
    connecter('member');
    const { result } = renderHook(() => usePermissions());
    const p = result.current;
    expect(p.canListUsers).toBe(false);
    expect(p.canCreateProject).toBe(false);
    expect(p.canManageActivity('chef')).toBe(false);
    expect(p.canTrackActivity({ assigneeId: 'moi' }, 'chef')).toBe(true);
    expect(p.canTrackActivity({ assigneeId: 'autre' }, 'chef')).toBe(false);
  });

  it('non connecté : aucun droit', () => {
    const { result } = renderHook(() => usePermissions());
    const p = result.current;
    expect(p.canListUsers).toBe(false);
    expect(p.canManageProject({ ownerId: undefined as unknown as string })).toBe(false);
    expect(p.canTrackActivity({ assigneeId: undefined }, undefined)).toBe(false);
  });
});
