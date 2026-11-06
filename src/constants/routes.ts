import {
  Home,
  FolderKanban,
  ClipboardList,
  Users,
  BarChart3,
  Settings,
  Bot,
  Bell,
} from 'lucide-react';

export const ROUTES = {
  LOGIN: '/login',
  REGISTER: '/register',
  DASHBOARD: '/',
  PROJECTS: '/projects',
  ACTIVITIES: '/activities',
  USERS: '/users',
  REPORTS: '/reports',
  NOTIFICATIONS: '/notifications',
  SETTINGS: '/settings',
  AI_ASSISTANT: '/ai',
} as const;

export const NAV_ITEMS = [
  { label: 'Dashboard', path: ROUTES.DASHBOARD, icon: Home },
  { label: 'Projets', path: ROUTES.PROJECTS, icon: FolderKanban },
  { label: 'Activités', path: ROUTES.ACTIVITIES, icon: ClipboardList },
  { label: 'Utilisateurs', path: ROUTES.USERS, icon: Users },
  { label: 'Rapports', path: ROUTES.REPORTS, icon: BarChart3 },
  { label: 'Assistant IA', path: ROUTES.AI_ASSISTANT, icon: Bot },
] as const;

export const SECONDARY_NAV_ITEMS = [
  { label: 'Notifications', path: ROUTES.NOTIFICATIONS, icon: Bell },
  { label: 'Paramètres', path: ROUTES.SETTINGS, icon: Settings },
] as const;