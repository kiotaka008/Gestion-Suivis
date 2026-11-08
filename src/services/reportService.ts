import api from './api';
import type { DashboardStats, Report } from '../types/models';

export const reportService = {
  /** Statistiques du tableau de bord, calculées par le backend sur le périmètre de l'utilisateur. */
  async dashboard(): Promise<DashboardStats> {
    const { data } = await api.get<DashboardStats>('/dashboard/stats');
    return data;
  },

  /** Bilan global sur une période : 7d, 30d, 3m, 6m, 12m (ou week, month, year). */
  async overview(period: string): Promise<Report> {
    const { data } = await api.get<Report>('/reports/overview', { params: { period } });
    return data;
  },
};
