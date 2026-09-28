import api from './api';
import type { User } from '../types/models';
import type { PaginatedResponse, PaginationParams } from '../types/api';

export const userService = {
  async list(params?: PaginationParams): Promise<PaginatedResponse<User>> {
    const { data } = await api.get<PaginatedResponse<User>>('/users', { params });
    return data;
  },

  async getById(id: string): Promise<User> {
    const { data } = await api.get<User>(`/users/${id}`);
    return data;
  },

  async update(id: string, payload: Partial<User>): Promise<User> {
    const { data } = await api.patch<User>(`/users/${id}`, payload);
    return data;
  },

  async deactivate(id: string): Promise<void> {
    await api.post(`/users/${id}/deactivate`);
  },
};