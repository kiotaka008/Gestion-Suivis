import { useMemo, useState, useEffect } from 'react';
import { Plus, Users } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Avatar } from '../components/ui/Avatar';
import { EmptyState } from '../components/ui/EmptyState';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { SkeletonTable } from '../components/ui/SkeletonTable';
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
import { Alert } from '../components/ui/Alert';
import { userService, type CreateUserPayload } from '../services/userService';
import { ApiError } from '../services/ApiError';

function getRoleVariant(role: Role) {
  switch (role) {
    case 'admin': return 'danger' as const;
    case 'manager': return 'warning' as const;
    default: return 'info' as const;
  }
}

function getRoleLabel(role: Role) {
  switch (role) {
    case 'admin': return 'Administrateur';
    case 'manager': return 'Responsable';
    default: return 'Membre';
  }
}

export function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const [search, setSearch] = useState('');
  const [role, setRole] = useState('all');
  const [status, setStatus] = useState('all');
  const [sort, setSort] = useState('recent');
  const [view, setView] = useState<ViewMode>('table');

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Liste réservée aux ADMIN et MANAGER (403 sinon : le message du backend est affiché).
  useEffect(() => {
    let annule = false;
    userService
      .listAll()
      .then((liste) => {
        if (!annule) setUsers(liste);
      })
      .catch((err) => {
        if (!annule) setError(err instanceof ApiError ? err.message : 'Chargement des utilisateurs impossible.');
      })
      .finally(() => {
        if (!annule) setIsLoading(false);
      });
    return () => {
      annule = true;
    };
  }, []);

  const filtered = useMemo(() => {
    let result = [...users];
    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter((u) => u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q));
    }
    if (role !== 'all') result = result.filter((u) => u.role === role);
    if (status === 'active') result = result.filter((u) => u.isActive);
    else if (status === 'inactive') result = result.filter((u) => !u.isActive);

    switch (sort) {
      case 'oldest': result.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()); break;
      case 'name': result.sort((a, b) => a.name.localeCompare(b.name)); break;
      default: result.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    }
    return result;
  }, [users, search, role, status, sort]);

  // Les erreurs remontent jusqu'au formulaire, qui affiche le message du backend.
  const handleCreate = async (data: CreateUserPayload) => {
    const cree = await userService.create(data);
    setUsers((prev) => [cree, ...prev]);
  };

  const handleToggleActive = async (user: User) => {
    setError(null);
    try {
      if (user.isActive) await userService.deactivate(user.id);
      else await userService.activate(user.id);
      setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, isActive: !u.isActive } : u)));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Modification impossible.');
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await userService.remove(deleteTarget.id);
      setUsers((prev) => prev.filter((u) => u.id !== deleteTarget.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Suppression impossible.');
    } finally {
      setIsDeleting(false);
      setDeleteTarget(null);
    }
  };

  return (
    <Container className="py-6">
      <PageHeader
        title="Utilisateurs"
        description="Gérez les membres de votre organisation."
        actions={<Button leftIcon={<Plus className="h-4 w-4" />} onClick={() => setIsCreateOpen(true)}>Nouvel utilisateur</Button>}
      />

      {error && (
        <Alert variant="danger" className="mt-6">
          {error}
        </Alert>
      )}

      <div className="mt-6">
        <UsersFilters
          search={search} onSearchChange={setSearch}
          role={role} onRoleChange={setRole}
          status={status} onStatusChange={setStatus}
          sort={sort} onSortChange={setSort}
          view={view} onViewChange={setView}
        />
      </div>

      <div className="mt-6">
        {isLoading ? (
          <SkeletonTable rows={6} cols={5} />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={<Users className="h-6 w-6" />}
            title="Aucun utilisateur trouvé"
            description="Ajustez vos filtres ou ajoutez un nouvel utilisateur."
            action={<Button leftIcon={<Plus className="h-4 w-4" />} onClick={() => setIsCreateOpen(true)}>Ajouter un utilisateur</Button>}
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
                    <TableCell className="text-sm text-muted-foreground">{user.email}</TableCell>
                    <TableCell><Badge variant={getRoleVariant(user.role)}>{getRoleLabel(user.role)}</Badge></TableCell>
                    <TableCell><Badge variant={user.isActive ? 'success' : 'muted'}>{user.isActive ? 'Actif' : 'Inactif'}</Badge></TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {new Date(user.createdAt).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' })}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {filtered.map((user) => (
              <UserCard key={user.id} user={user} onDelete={(u) => setDeleteTarget(u)} onToggleActive={handleToggleActive} />
            ))}
          </div>
        )}
      </div>

      <CreateUserModal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} onCreate={handleCreate} />
      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDeleteConfirm} title="Supprimer l'utilisateur ?" description={`L'utilisateur "${deleteTarget?.name}" sera définitivement supprimé.`} confirmLabel="Supprimer" isLoading={isDeleting} />
    </Container>
  );
}