import api from './api';
import type { User } from '../types/models';

export interface LoginPayload {
  email: string;
  password: string;
  /** Slug de l'organisation, requis seulement si l'email existe dans plusieurs organisations. */
  organization?: string;
}

export interface LoginResponse {
  user: User;
}

export interface RegisterPayload {
  name: string;
  email: string;
  password: string;
  organization?: string;
}

export interface GoogleConfig {
  enabled: boolean;
  clientId: string;
}

export const authService = {
  async login(payload: LoginPayload): Promise<LoginResponse> {
    const { data } = await api.post<LoginResponse>('/auth/login', payload);
    return data;
  },

  /** Connexion avec le jeton d'identité renvoyé par Google Identity Services. */
  async loginWithGoogle(credential: string, organization?: string): Promise<LoginResponse> {
    const { data } = await api.post<LoginResponse>('/auth/google', { credential, organization });
    return data;
  },

  async googleConfig(): Promise<GoogleConfig> {
    const { data } = await api.get<GoogleConfig>('/auth/google/config');
    return data;
  },

  async logout(): Promise<void> {
    await api.post('/auth/logout');
  },

  async register(payload: RegisterPayload): Promise<User> {
    const { data } = await api.post<User>('/auth/register', payload);
    return data;
  },

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await api.post('/auth/change-password', { currentPassword, newPassword });
  },

  async me(): Promise<User> {
    const { data, headers } = await api.get<User>('/auth/me');

    // Si le backend répond avec du HTML au lieu de JSON,
    // c'est que ce n'est PAS le vrai endpoint d'auth.
    const contentType = String(headers['content-type'] ?? '');
    if (!contentType.includes('application/json')) {
      throw new Error('Invalid auth response');
    }

    if (!data || typeof data !== 'object' || !('id' in data)) {
      throw new Error('Invalid auth payload');
    }

    return data;
  },
};
