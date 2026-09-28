import api from './api';
import type { User } from '../types/models';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface LoginResponse {
  user: User;
}

export const authService = {
  async login(payload: LoginPayload): Promise<LoginResponse> {
    const { data } = await api.post<LoginResponse>('/auth/login', payload);
    return data;
  },

  async logout(): Promise<void> {
    await api.post('/auth/logout');
  },

  async me(): Promise<User> {
    const { data, headers } = await api.get<User>('/auth/me');

    // Si le backend répond avec du HTML au lieu de JSON,
    // c'est que ce n'est PAS le vrai endpoint d'auth.
    const contentType = headers['content-type'] ?? '';
    if (!contentType.includes('application/json')) {
      throw new Error('Invalid auth response');
    }

    if (!data || typeof data !== 'object' || !('id' in data)) {
      throw new Error('Invalid auth payload');
    }

    return data;
  },
};