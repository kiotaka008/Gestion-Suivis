import api, { lireTout } from './api';
import type { Activity, Project } from '../types/models';
import type { PaginatedResponse, PaginationParams } from '../types/api';

export const projectService = {
  async list(params?: PaginationParams): Promise<PaginatedResponse<Project>> {
    const { data } = await api.get<PaginatedResponse<Project>>('/projects', { params });
    return data;
  },

  /** Tous les projets visibles par l'utilisateur connecté. */
  listAll(): Promise<Project[]> {
    return lireTout<Project>('/projects', { sortBy: 'createdAt', sortOrder: 'desc' });
  },

  async getById(id: string): Promise<Project> {
    const { data } = await api.get<Project>(`/projects/${id}`);
    return data;
  },

  /** Activités du projet visibles par l'utilisateur connecté. */
  activities(id: string): Promise<Activity[]> {
    return lireTout<Activity>(`/projects/${id}/activities`);
  },

  async create(payload: Partial<Project>): Promise<Project> {
    const { data } = await api.post<Project>('/projects', payload);
    return data;
  },

  async update(id: string, payload: Partial<Project>): Promise<Project> {
    const { data } = await api.patch<Project>(`/projects/${id}`, payload);
    return data;
  },

  async addMember(id: string, userId: string): Promise<Project> {
    const { data } = await api.post<Project>(`/projects/${id}/members`, { userId });
    return data;
  },

  async removeMember(id: string, userId: string): Promise<Project> {
    const { data } = await api.delete<Project>(`/projects/${id}/members/${userId}`);
    return data;
  },

  async remove(id: string): Promise<void> {
    await api.delete(`/projects/${id}`);
  },
};
