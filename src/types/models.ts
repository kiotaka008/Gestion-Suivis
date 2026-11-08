export type Role = 'admin' | 'manager' | 'member';

export type ProjectStatus = 'active' | 'on_hold' | 'completed' | 'archived';
export type ActivityStatus = 'todo' | 'in_progress' | 'done' | 'blocked';
export type Priority = 'low' | 'medium' | 'high' | 'urgent';

export interface User {
  id: string;
  email: string;
  name: string;
  role: Role;
  avatarUrl?: string;
  isActive: boolean;
  createdAt: string;
  organizationId?: string;
  lastLoginAt?: string;
}

export interface Project {
  id: string;
  name: string;
  description?: string;
  status: ProjectStatus;
  priority: Priority;
  progress: number;
  ownerId: string;
  owner?: User;
  memberIds: string[];
  members?: User[];
  startDate?: string;
  dueDate?: string;
  isOverdue?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Activity {
  id: string;
  projectId: string;
  projectName?: string;
  title: string;
  description?: string;
  status: ActivityStatus;
  priority: Priority;
  progress: number;
  assigneeId?: string;
  assignee?: User;
  startDate?: string;
  dueDate?: string;
  isOverdue?: boolean;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export type NotificationType = 'project' | 'activity' | 'comment' | 'deadline' | 'ai' | 'user';

export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  message?: string;
  isRead: boolean;
  createdAt: string;
}

/** Séries de graphique : { labels, datasets: [{ label, data }] }. */
export interface ChartSeries {
  labels: string[];
  datasets: { label: string; data: number[] }[];
}

/** Répartition simple : { labels, data }. */
export interface Distribution {
  labels: string[];
  data: number[];
}

/** Réponse de GET /api/dashboard/stats. */
export interface DashboardStats {
  totalProjects: number;
  activeProjects: number;
  completedProjects: number;
  overdueProjects: number;
  totalActivities: number;
  completedActivities: number;
  overdueActivities: number;
  monthlyEvolution: ChartSeries;
  statusDistribution: Distribution;
  tasksPerWeek: ChartSeries;
  upcomingDeadlines: Project[];
  recentActivities: Activity[];
}

/** Réponse de GET /api/reports/overview. */
export interface Report {
  scope: { type: string; id?: string; name?: string | null };
  period: { code: string; from: string; to: string };
  totalActivities: number;
  completedActivities: number;
  inProgressActivities: number;
  todoActivities: number;
  overdueActivities: number;
  completionRate: number;
  overdueRate: number;
  averageCompletionDays?: number;
  totalProjects: number;
  overdueProjects: number;
  monthlyEvolution: ChartSeries;
  statusDistribution: Distribution;
  priorityDistribution: Distribution;
  projectsSummary: { id: string; name: string; progress: number; status: ProjectStatus }[];
}
