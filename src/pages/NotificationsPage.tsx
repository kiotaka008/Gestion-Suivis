import { useMemo, useState } from 'react';
import { Bell } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { EmptyState } from '../components/ui/EmptyState';
import { NotificationItem } from '../components/notifications/NotificationItem';
import { NotificationsFilters } from '../components/notifications/NotificationsFilters';
import type { Notification } from '../types/models';
import { mockNotifications } from '../mocks/notifications';

export function NotificationsPage() {
  const [notifications, setNotifications] = useState<Notification[]>(mockNotifications);
  const [type, setType] = useState('all');
  const [status, setStatus] = useState('all');

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  const filtered = useMemo(() => {
    let result = [...notifications];

    if (type !== 'all') {
      result = result.filter((n) => n.type === type);
    }

    if (status === 'unread') {
      result = result.filter((n) => !n.isRead);
    } else if (status === 'read') {
      result = result.filter((n) => n.isRead);
    }

    result.sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    );

    return result;
  }, [notifications, type, status]);

  const handleMarkAsRead = (notification: Notification) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === notification.id ? { ...n, isRead: true } : n))
    );
  };

  const handleMarkAllAsRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
  };

  const handleDelete = (notification: Notification) => {
    setNotifications((prev) => prev.filter((n) => n.id !== notification.id));
  };

  return (
    <Container className="py-6">
      <PageHeader
        title="Notifications"
        description={
          unreadCount > 0
            ? `Vous avez ${unreadCount} notification${unreadCount > 1 ? 's' : ''} non lue${unreadCount > 1 ? 's' : ''}.`
            : 'Toutes vos notifications sont à jour.'
        }
      />

      <div className="mt-6">
        <NotificationsFilters
          type={type}
          onTypeChange={setType}
          status={status}
          onStatusChange={setStatus}
          onMarkAllAsRead={handleMarkAllAsRead}
          unreadCount={unreadCount}
        />
      </div>

      <div className="mt-6">
        {filtered.length === 0 ? (
          <EmptyState
            icon={<Bell className="h-6 w-6" />}
            title="Aucune notification"
            description="Vous n'avez aucune notification pour ces filtres."
          />
        ) : (
          <div className="space-y-3">
            {filtered.map((notification) => (
              <NotificationItem
                key={notification.id}
                notification={notification}
                onMarkAsRead={handleMarkAsRead}
                onDelete={handleDelete}
              />
            ))}
          </div>
        )}
      </div>
    </Container>
  );
}