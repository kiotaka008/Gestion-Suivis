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
  createdAt: string;
  updatedAt: string;
}

export interface Activity {
  id: string;
  projectId: string;
  title: string;
  description?: string;
  status: ActivityStatus;
  priority: Priority;
  progress: number;
  assigneeId?: string;
  assignee?: User;
  dueDate?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Notification {
  id: string;
  type: 'project' | 'activity' | 'comment' | 'deadline' | 'ai' | 'user';
  title: string;
  message?: string;
  isRead: boolean;
  createdAt: string;