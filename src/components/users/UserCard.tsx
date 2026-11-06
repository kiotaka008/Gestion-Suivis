import { MoreVertical, Mail, Edit, Trash2, Eye, UserX, UserCheck } from 'lucide-react';
import type { User, Role } from '../../types/models';
import { Card, CardContent, CardHeader } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Avatar } from '../ui/Avatar';
import { Dropdown } from '../ui/Dropdown';

function getRoleVariant(role: Role) {
  switch (role) {
    case 'admin':
      return 'danger' as const;
    case 'manager':
      return 'warning' as const;
    default:
      return 'info' as const;
  }
}

function getRoleLabel(role: Role) {
  switch (role) {
    case 'admin':
      return 'Administrateur';
    case 'manager':
      return 'Responsable';
    default:
      return 'Membre';
  }
}

function formatDate(date?: string) {
  if (!date) return '—';
  return new Date(date).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

export interface UserCardProps {
  user: User;
  onEdit?: (user: User) => void;
  onDelete?: (user: User) => void;
  onView?: (user: User) => void;
  onToggleActive?: (user: User) => void;
}

export function UserCard({ user, onEdit, onDelete, onView, onToggleActive }: UserCardProps) {
  return (
    <Card className="group transition-shadow hover:shadow-md">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <Avatar name={user.name} size="lg" />
            <div className="min-w-0">
              <h3 className="truncate text-base font-semibold text-foreground">
                {user.name}
              </h3>
              <p className="truncate text-xs text-muted-foreground">{user.email}</p>
            </div>
          </div>

          <Dropdown
            trigger={
              <button
                className="rounded-md p-1 text-muted-foreground hover:bg-muted"
                aria-label="Actions de l'utilisateur"
              >
                <MoreVertical className="h-4 w-4" />
              </button>
            }
            items={[
              {
                label: 'Voir',
                icon: <Eye className="h-4 w-4" />,
                onClick: () => onView?.(user),
              },
              {
                label: 'Modifier',
                icon: <Edit className="h-4 w-4" />,
                onClick: () => onEdit?.(user),
              },
              {
                label: user.isActive ? 'Désactiver' : 'Activer',
                icon: user.isActive ? <UserX className="h-4 w-4" /> : <UserCheck className="h-4 w-4" />,
                onClick: () => onToggleActive?.(user),
              },
              {
                label: 'Supprimer',
                icon: <Trash2 className="h-4 w-4" />,
                variant: 'danger',
                onClick: () => onDelete?.(user),
              },
            ]}
          />
        </div>
      </CardHeader>

      <CardContent className="space-y-3 pt-0">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={getRoleVariant(user.role)}>{getRoleLabel(user.role)}</Badge>
          <Badge variant={user.isActive ? 'success' : 'muted'}>
            {user.isActive ? 'Actif' : 'Inactif'}
          </Badge>
        </div>

        <div className="flex items-center gap-1.5 border-t border-border pt-3 text-xs text-muted-foreground">
          <Mail className="h-3.5 w-3.5" />
          <span>Inscrit le {formatDate(user.createdAt)}</span>
        </div>
      </CardContent>
    </Card>
  );
}