import { useMemo, useState } from 'react';
import { Plus, Users } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Avatar } from '../components/ui/Avatar';
import { EmptyState } from '../components/ui/EmptyState';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/Table';
import { UsersFilters, type ViewMode } from '../components/users/UsersFilters';
import { UserCard } from '../components/users/UserCard';
import { CreateUserModal } from '../components/users/CreateUserModal';
import type { User, Role } from '../types/models';
import { mockUsers } from '../mocks/users';

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

export function UsersPage() {
  const [users, setUsers] = useState<User[]>(mockUsers);
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('all');
  const [status, setStatus] = useState('all');
  const [sort, setSort] = useState('recent');
  const [view, setView] = useState<ViewMode>('table');

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const filtered = useMemo(() => {
    let result = [...users];

    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter(
        (u) =>
          u.name.toLowerCase().includes(q) ||
          u.email.toLowerCase().includes(q)
      );
    }

    if (role !== 'all') {
      result = result.filter((u) => u.role === role);
    }

    if (status === 'active') {
      result = result.filter((u) => u.isActive);
    } else if (status === 'inactive') {
      result = result.filter((u) => !u.isActive);
    }

    switch (sort) {
      case 'oldest':
        result.sort(
          (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        );
        break;
      case 'name':
        result.sort((a, b) => a.name.localeCompare(b.name));
        break;
      default:
        result.sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
    }

    return result;
  }, [users, search, role, status, sort]);

  const handleCreate = (data: Partial<User>) => {
    const newUser: User = {
      id: `u-${Date.now()}`,
      name: data.name ?? 'Sans nom',
      email: data.email ?? '',
      role: data.role ?? 'member',
      isActive: data.isActive ?? true,
      createdAt: new Date().toISOString(),
    };
    setUsers((prev) => [newUser, ...prev]);
  };

  const handleToggleActive = (user: User) => {
    setUsers((prev) =>
      prev.map((u) => (u.id === user.id ? { ...u, isActive: !u.isActive } : u))
    );
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    await new Promise((r) => setTimeout(r, 400));
    setUsers((prev) => prev.filter((u) => u.id !== deleteTarget.id));
    setIsDeleting(false);
    setDeleteTarget(null);
  };

  return (
    <Container className="py-6">
      <PageHeader
        title="Utilisateurs"
        description="Gérez les membres de votre organisation."
        actions={
          <Button
            leftIcon={<Plus className="h-4 w-4" />}
            onClick={() => setIsCreateOpen(true)}
          >
            Nouvel utilisateur
          </Button>
        }
      />

      <div className="mt-6">
        <UsersFilters
          search={search}
          onSearchChange={setSearch}
          role={role}
          onRoleChange={setRole}
          status={status}
          onStatusChange={setStatus}
          sort={sort}
          onSortChange={setSort}
          view={view}
          onViewChange={setView}
        />
      </div>

      <div className="mt-6">
        {filtered.length === 0 ? (
          <EmptyState
            icon={<Users className="h-6 w-6" />}
            title="Aucun utilisateur trouvé"
            description="Ajustez vos filtres ou ajoutez un nouvel utilisateur."
            action={
              <Button
                leftIcon={<Plus className="h-4 w-4" />}
                onClick={() => setIsCreateOpen(true)}
              >
                Ajouter un utilisateur
              </Button>
            }
          />
        ) : view === 'table' ? (
          <div className="overflow-x-auto rounded-lg border border-border bg-surface">
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeader>Utilisateur</TableHeader>
                  <TableHeader>Email</TableHeader>
                  <TableHeader>Rôle</TableHeader>
                  <TableHeader>Statut</TableHeader>
                  <TableHeader>Inscrit le</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <Avatar name={user.name} size="sm" />
                        <span className="font-medium text-foreground">{user.name}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {user.email}
                    </TableCell>
                    <TableCell>
                      <Badge variant={getRoleVariant(user.role)}>
                        {getRoleLabel(user.role)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={user.isActive ? 'success' : 'muted'}>
                        {user.isActive ? 'Actif' : 'Inactif'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {new Date(user.createdAt).toLocaleDateString('fr-FR', {
                        day: '2-digit',
                        month: 'short',
                        year: 'numeric',
                      })}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {filtered.map((user) => (
              <UserCard
                key={user.id}
                user={user}
                onDelete={(u) => setDeleteTarget(u)}
                onToggleActive={handleToggleActive}
              />
            ))}
          </div>
        )}
      </div>

      <CreateUserModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreate={handleCreate}
      />

      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDeleteConfirm}
        title="Supprimer l'utilisateur ?"
        description={`L'utilisateur "${deleteTarget?.name}" sera définitivement supprimé.`}
        confirmLabel="Supprimer"
        isLoading={isDeleting}
      />
    </Container>
  );
}