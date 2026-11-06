import { CheckCheck } from 'lucide-react';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';

export interface NotificationsFiltersProps {
  type: string;
  onTypeChange: (value: string) => void;
  status: string;
  onStatusChange: (value: string) => void;
  onMarkAllAsRead: () => void;
  unreadCount: number;
}

export function NotificationsFilters({
  type,
  onTypeChange,
  status,
  onStatusChange,
  onMarkAllAsRead,
  unreadCount,
}: NotificationsFiltersProps) {
  return (
    <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={type}
          onChange={(e) => onTypeChange(e.target.value)}
          options={[
            { value: 'all', label: 'Tous les types' },
            { value: 'project', label: 'Projets' },
            { value: 'activity', label: 'Activités' },
            { value: 'comment', label: 'Commentaires' },
            { value: 'deadline', label: 'Échéances' },
            { value: 'ai', label: 'Assistant IA' },
            { value: 'user', label: 'Utilisateurs' },
          ]}
          wrapperClassName="w-auto"
          className="w-auto min-w-44"
        />

        <Select
          value={status}
          onChange={(e) => onStatusChange(e.target.value)}
          options={[
            { value: 'all', label: 'Toutes' },
            { value: 'unread', label: 'Non lues' },
            { value: 'read', label: 'Lues' },
          ]}
          wrapperClassName="w-auto"
          className="w-auto min-w-36"
        />
      </div>

      <Button
        variant="outline"
        leftIcon={<CheckCheck className="h-4 w-4" />}
        onClick={onMarkAllAsRead}
        disabled={unreadCount === 0}
      >
        Tout marquer comme lu
        {unreadCount > 0 && (
          <span className="ml-1.5 rounded-full bg-primary px-2 py-0.5 text-xs text-white">
            {unreadCount}
          </span>
        )}
      </Button>
    </div>
  );
}