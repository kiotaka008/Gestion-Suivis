import api from './api';
import type { Activity } from '../types/models';
import type { PaginatedResponse, PaginationParams } from '../types/api';

export const activityService = {
  async list(params?: PaginationParams): Promise<PaginatedResponse<Activity>> {
    const { data } = await api.get<PaginatedResponse<Activity>>('/activities', { params });
    return data;
  },

  async getById(id: string): Promise<Activity> {
    const { data } = await api.get<Activity>(`/activities/${id}`);
    return data;
  },

  async create(payload: Partial<Activity>): Promise<Activity> {
    const { data } = await api.post<Activity>('/activities', payload);
    return data;
  },

  async update(id: string, payload: Partial<Activity>): Promise<Activity> {
    const { data } = await api.patch<Activity>(`/activities/${id}`, payload);
    return data;
  },

  async remove(id: string): Promise<void> {
    await api.delete(`/activities/${id}`);
  },
};