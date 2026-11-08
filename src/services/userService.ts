import api, { lireTout } from './api';
import type { Role, User } from '../types/models';
import type { PaginatedResponse, PaginationParams } from '../types/api';

export interface CreateUserPayload {
  name: string;
  email: string;
  role: Role;
  password: string;
  isActive?: boolean;
}

export const userService = {
  async list(params?: PaginationParams): Promise<PaginatedResponse<User>> {
    const { data } = await api.get<PaginatedResponse<User>>('/users', { params });
    return data;
  },

  /** Tous les utilisateurs visibles (ADMIN et MANAGER). */
  listAll(): Promise<User[]> {
    return lireTout<User>('/users');
  },

  async getById(id: string): Promise<User> {
    const { data } = await api.get<User>(`/users/${id}`);
    return data;
  },

  async create(payload: CreateUserPayload): Promise<User> {
    const { data } = await api.post<User>('/users', payload);
    return data;
  },

  async update(id: string, payload: Partial<User>): Promise<User> {
    const { data } = await api.patch<User>(`/users/${id}`, payload);
    return data;
  },

  /** Modification de son propre profil. */
  async updateMe(payload: { name?: string; email?: string; avatarUrl?: string }): Promise<User> {
    const { data } = await api.patch<User>('/users/me', payload);
    return data;
  },

  async activate(id: string): Promise<void> {
    await api.post(`/users/${id}/activate`);
  },

  async deactivate(id: string): Promise<void> {
    await api.post(`/users/${id}/deactivate`);
  },

  async remove(id: string): Promise<void> {
    await api.delete(`/users/${id}`);
  },
};
