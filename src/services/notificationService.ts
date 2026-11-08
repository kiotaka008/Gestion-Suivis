import api, { lireTout } from './api';
import type { Notification } from '../types/models';

export const notificationService = {
  /** Toutes les notifications de l'utilisateur connecté, les plus récentes d'abord. */
  listAll(): Promise<Notification[]> {
    return lireTout<Notification>('/notifications');
  },

  async unreadCount(): Promise<number> {
    const { data } = await api.get<{ count: number }>('/notifications/unread-count');
    return data.count;
  },

  async markAsRead(id: string): Promise<void> {
    await api.patch(`/notifications/${id}/read`);
  },

  async markAllAsRead(): Promise<void> {
    await api.post('/notifications/read-all');
  },

  async remove(id: string): Promise<void> {
    await api.delete(`/notifications/${id}`);
  },
};
