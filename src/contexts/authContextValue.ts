import { createContext } from 'react';
import type { User } from '../types/models';
import type { LoginPayload } from '../services/authService';

export interface AuthContextValue {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  loginWithGoogle: (credential: string, organization?: string) => Promise<void>;
  /** Met à jour l'utilisateur affiché (ex. après modification du profil). */
  setUser: (user: User) => void;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
