import api from './api';
import type { Project } from '../types/models';
import type { PaginatedResponse, PaginationParams } from '../types/api';

export const projectService = {
  async list(params?: PaginationParams): Promise<PaginatedResponse<Project>> {
    const { data } = await api.get<PaginatedResponse<Project>>('/projects', { params });
    return data;
  },

  async getById(id: string): Promise<Project> {
    const { data } = await api.get<Project>(`/projects/${id}`);
    return data;
  },

  async create(payload: Partial<Project>): Promise<Project> {
    const { data } = await api.post<Project>('/projects', payload);
    return data;
  },

  async update(id: string, payload: Partial<Project>): Promise<Project> {
    const { data } = await api.patch<Project>(`/projects/${id}`, payload);
    return data;
  },

  async remove(id: string): Promise<void> {
    await api.delete(`/projects/${id}`);
  },
};