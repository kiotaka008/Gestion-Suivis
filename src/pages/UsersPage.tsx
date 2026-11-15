import { useCallback, useMemo, useState, useEffect } from 'react';
import { Edit, History, MoreVertical, Plus, Trash2, UserCheck, UserX, Users } from 'lucide-react';
import { Modal } from '../components/ui/Modal';
import { HistoryTimeline } from '../components/history/HistoryTimeline';
import { Dropdown } from '../components/ui/Dropdown';
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
import { useAuth } from '../hooks/useAuth';
import { usePermissions } from '../hooks/usePermissions';

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
  const { user: moi } = useAuth();
  const { canListUsers, canManageUsers } = usePermissions();
  const [users, setUsers] = useState<User[]>([]);
  const [isLoading, setIsLoading] = useState(canListUsers);

  const [search, setSearch] = useState('');
  const [role, setRole] = useState('all');
  const [status, setStatus] = useState('all');
  const [sort, setSort] = useState('recent');
  const [view, setView] = useState<ViewMode>('table');

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<User | null>(null);
  const [historyTarget, setHistoryTarget] = useState<User | null>(null);
  // Source stable pour HistoryTimeline (sinon l'historique serait rechargé à chaque rendu).
  const historyId = historyTarget?.id;
  const chargerHistorique = useCallback(
    (page: number, perPage: number) => userService.history(historyId ?? '', page, perPage),
    [historyId]
  );
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Liste réservée aux ADMIN et MANAGER (403 sinon : le message du backend est affiché).
  useEffect(() => {
    if (!canListUsers) return;
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
  }, [canListUsers]);

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

  const handleUpdate = async (data: Pick<User, 'name' | 'email' | 'role'>) => {
    if (!editTarget) return;
    const modifie = await userService.update(editTarget.id, data);
    setUsers((prev) => prev.map((u) => (u.id === modifie.id ? modifie : u)));
  };

  // Actions d'administration ; son propre compte ne peut être ni désactivé ni supprimé (règle du backend).
  const actionsPour = (user: User) => {
    const estMoi = user.id === moi?.id;
    return {
      onEdit: canManageUsers ? (u: User) => setEditTarget(u) : undefined,
      onToggleActive: canManageUsers && !estMoi ? handleToggleActive : undefined,
      onDelete: canManageUsers && !estMoi ? (u: User) => setDeleteTarget(u) : undefined,
    };
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
        actions={canManageUsers && <Button leftIcon={<Plus className="h-4 w-4" />} onClick={() => setIsCreateOpen(true)}>Nouvel utilisateur</Button>}
      />

      {!canListUsers && (
        <div className="mt-6">
          <EmptyState
            icon={<Users className="h-6 w-6" />}
            title="Accès réservé"
            description="La liste des utilisateurs est réservée aux administrateurs et aux responsables."
          />
        </div>
      )}

      {error && (
        <Alert variant="danger" className="mt-6">
          {error}
        </Alert>
      )}

      {canListUsers && (<>
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
            description={canManageUsers ? 'Ajustez vos filtres ou ajoutez un nouvel utilisateur.' : 'Ajustez vos filtres.'}
            action={canManageUsers && <Button leftIcon={<Plus className="h-4 w-4" />} onClick={() => setIsCreateOpen(true)}>Ajouter un utilisateur</Button>}
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
                  {canManageUsers && <TableHeader><span className="sr-only">Actions</span></TableHeader>}
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
                    {canManageUsers && (
                      <TableCell className="w-12 text-right">
                        <Dropdown
                          trigger={
                            <button className="rounded-md p-1 text-muted-foreground hover:bg-muted" aria-label={`Actions pour ${user.name}`}>
                              <MoreVertical className="h-4 w-4" />
                            </button>
                          }
                          items={[
                            actionsPour(user).onEdit && { label: 'Modifier', icon: <Edit className="h-4 w-4" />, onClick: () => setEditTarget(user) },
                            canManageUsers && { label: 'Historique', icon: <History className="h-4 w-4" />, onClick: () => setHistoryTarget(user) },
                            actionsPour(user).onToggleActive && {
                              label: user.isActive ? 'Désactiver' : 'Activer',
                              icon: user.isActive ? <UserX className="h-4 w-4" /> : <UserCheck className="h-4 w-4" />,
                              onClick: () => handleToggleActive(user),
                            },
                            actionsPour(user).onDelete && { label: 'Supprimer', icon: <Trash2 className="h-4 w-4" />, variant: 'danger', onClick: () => setDeleteTarget(user) },
                          ]}
                        />
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {filtered.map((user) => (
              <UserCard key={user.id} user={user} {...actionsPour(user)} />
            ))}
          </div>
        )}
      </div>
      </>)}

      <CreateUserModal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} onCreate={handleCreate} />
      {historyTarget && (
        <Modal
          isOpen
          onClose={() => setHistoryTarget(null)}
          title={`Historique de ${historyTarget.name}`}
          description="Connexions et modifications du compte, de la plus récente à la plus ancienne."
          size="lg"
        >
          <div className="max-h-[60vh] overflow-y-auto">
            <HistoryTimeline
              key={historyTarget.id}
              load={chargerHistorique}
            />
          </div>
        </Modal>
      )}
      {editTarget && (
        <CreateUserModal
          key={editTarget.id}
          isOpen
          user={editTarget}
          onClose={() => setEditTarget(null)}
          onCreate={() => undefined}
          onUpdate={handleUpdate}
        />
      )}
      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDeleteConfirm} title="Supprimer l'utilisateur ?" description={`L'utilisateur "${deleteTarget?.name}" sera définitivement supprimé.`} confirmLabel="Supprimer" isLoading={isDeleting} />
    </Container>
  );
}