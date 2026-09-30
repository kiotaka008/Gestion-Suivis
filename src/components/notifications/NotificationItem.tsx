import {
  FolderKanban,
  ClipboardList,
  MessageSquare,
  Calendar,
  Bot,
  Users,
  Check,
  Trash2,
} from 'lucide-react';
import type { Notification } from '../../types/models';
import { cn } from '../../utils/cn';

function getTypeIcon(type: Notification['type']) {
  switch (type) {
    case 'project':
      return <FolderKanban className="h-5 w-5" />;
    case 'activity':
      return <ClipboardList className="h-5 w-5" />;
    case 'comment':
      return <MessageSquare className="h-5 w-5" />;
    case 'deadline':
      return <Calendar className="h-5 w-5" />;
    case 'ai':
      return <Bot className="h-5 w-5" />;
    case 'user':
      return <Users className="h-5 w-5" />;
    default:
      return <FolderKanban className="h-5 w-5" />;
  }
}

function getTypeBg(type: Notification['type']) {
  switch (type) {
    case 'project':
      return 'bg-primary/10 text-primary';
    case 'activity':
      return 'bg-info/10 text-info';
    case 'comment':
      return 'bg-purple-500/10 text-purple-500';
    case 'deadline':
      return 'bg-warning/10 text-warning';
    case 'ai':
      return 'bg-success/10 text-success';
    case 'user':
      return 'bg-muted text-muted-foreground';
    default:
      return 'bg-muted text-muted-foreground';
  }
}

function formatRelativeDate(date: string) {
  const now = new Date();
  const d = new Date(date);
  const diffMs = now.getTime() - d.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  const diffHour = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHour / 24);

  if (diffMin < 1) return "à l'instant";
  if (diffMin < 60) return `il y a ${diffMin} min`;
  if (diffHour < 24) return `il y a ${diffHour} h`;
  if (diffDay < 7) return `il y a ${diffDay} j`;
  return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
}

export interface NotificationItemProps {
  notification: Notification;
  onMarkAsRead?: (notification: Notification) => void;
  onDelete?: (notification: Notification) => void;
}

export function NotificationItem({
  notification,
  onMarkAsRead,
  onDelete,
}: NotificationItemProps) {
  return (
    <div
      className={cn(
        'group flex items-start gap-3 rounded-lg border border-border p-4 transition-colors',
        notification.isRead ? 'bg-surface' : 'bg-primary/5'
      )}
    >
      <div
        className={cn(
          'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
          getTypeBg(notification.type)
        )}
      >
        {getTypeIcon(notification.type)}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-foreground">
              {notification.title}
            </p>
            <p className="mt-0.5 line-clamp-2 text-sm text-muted-foreground">
              {notification.message}
            </p>
          </div>

          {!notification.isRead && (
            <span
              className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-primary"
              aria-label="Non lue"
            />
          )}
        </div>

        <div className="mt-2 flex items-center justify-between">
          <span className="text-xs text-muted-foreground">
            {formatRelativeDate(notification.createdAt)}
          </span>

          <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
            {!notification.isRead && (
              <button
                onClick={() => onMarkAsRead?.(notification)}
                className="rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
                aria-label="Marquer comme lue"
                title="Marquer comme lue"
              >
                <Check className="h-4 w-4" />
              </button>
            )}
            <button
              onClick={() => onDelete?.(notification)}
              className="rounded-md p-1.5 text-muted-foreground hover:bg-danger/10 hover:text-danger"
              aria-label="Supprimer"
              title="Supprimer"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}