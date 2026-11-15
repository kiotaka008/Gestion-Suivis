import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Emplacement } from './Emplacement';
import type { Activity, Project, User } from '../types/models';
import type { PaginatedResponse } from '../types/api';

/**
 * Utilisateur connecté simulé, lu par le faux `useAuth` des fichiers de tests :
 *   vi.mock('../hooks/useAuth', async () => {
 *     const aide = await import('./aide');
 *     return { useAuth: () => aide.authEtat };
 *   });
 */
export const authEtat: {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  logout: () => Promise<void>;
} = { user: null, isAuthenticated: false, isLoading: false, logout: async () => undefined };

export function connecter(role: User['role'], id = 'moi'): User {
  const user = utilisateur(id, id, role);
  authEtat.user = user;
  authEtat.isAuthenticated = true;
  return user;
}

export function utilisateur(id: string, nom: string, role: User['role'] = 'member'): User {
  return { id, name: nom, email: `${id}@exemple.com`, role, isActive: true, createdAt: '2026-01-01T00:00:00Z' };
}

export function projet(id: string, nom: string, ownerId: string, extra: Partial<Project> = {}): Project {
  return {
    id,
    name: nom,
    status: 'active',
    priority: 'medium',
    progress: 30,
    ownerId,
    memberIds: [],
    members: [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...extra,
  };
}

export function activite(id: string, titre: string, projectId: string, extra: Partial<Activity> = {}): Activity {
  return {
    id,
    title: titre,
    projectId,
    status: 'todo',
    priority: 'medium',
    progress: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...extra,
  };
}

export function page<T>(data: T[], numero = 1, totalPages = 1): PaginatedResponse<T> {
  return { data, meta: { page: numero, perPage: 20, total: data.length, totalPages } };
}

/** Rend un élément dans un routeur en mémoire, à l'adresse `route` (motif `chemin`). */
export function rendre(ui: ReactElement, { route = '/', chemin = '*' }: { route?: string; chemin?: string } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path={chemin} element={<>{ui}<Emplacement /></>} />
        <Route path="*" element={<Emplacement />} />
      </Routes>
    </MemoryRouter>
  );
}

/** La fenêtre modale place le focus sur son premier champ 50 ms après l'ouverture. */
export const focusInitial = () => new Promise((r) => setTimeout(r, 100));
