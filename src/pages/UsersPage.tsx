import { useCallback, useMemo, useRef, useState, useEffect } from 'react';
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
import { CreateUserModal, type UserUpdatePayload } from '../components/users/CreateUserModal';
import type { User, Role } from '../types/models';
import { Alert } from '../components/ui/Alert';
import { BoutonActualiser } from '../components/ui/BoutonActualiser';
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

/**
 * AUD-002, F7 : verrou par identifiant pour les actions en cours. La référence bloque un second
 * clic avant même le rendu suivant ; l'état (enCours) sert à l'affichage (entrée désactivée).
 */
function useVerrouParId() {
  const verrous = useRef<Set<string>>(new Set());
  const [enCours, setEnCours] = useState<ReadonlySet<string>>(new Set());
  const prendre = useCallback((id: string) => {
    if (verrous.current.has(id)) return false;
    verrous.current.add(id);
    setEnCours(new Set(verrous.current));
    return true;
  }, []);
  const liberer = useCallback((id: string) => {
    verrous.current.delete(id);
    setEnCours(new Set(verrous.current));
  }, []);
  return { enCours, prendre, liberer };
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

  // Seuls les champs modifiés sont reçus du formulaire (le rôle seulement s'il a changé).
  const handleUpdate = async (data: UserUpdatePayload) => {
    if (!editTarget) return;
    const modifie = await userService.update(editTarget.id, data);
    setUsers((prev) => prev.map((u) => (u.id === modifie.id ? modifie : u)));
  };

  // Conflit 409 : l'utilisateur est relu ; le formulaire garde la saisie jusqu'au clic « Recharger ».
  const rechargerUtilisateur = async () => {
    if (!editTarget) return;
    const actuel = await userService.getById(editTarget.id);
    setUsers((prev) => prev.map((u) => (u.id === actuel.id ? actuel : u)));
    setEditTarget(actuel);
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

  // AUD-002, F7 : comptes dont l'activation ou la désactivation est en cours.
  const { enCours: activationsEnCours, prendre: prendreActivation, liberer: libererActivation } = useVerrouParId();

  const remplacerUtilisateur = (actuel: User) =>
    setUsers((prev) => prev.map((u) => (u.id === actuel.id ? actuel : u)));

  // Erreurs d'activation : le message du backend reste affiché ; relecture seulement si l'état
  // réel est utile (409) ou incertain (réseau). 403 : aucune relecture (droits perdus).
  const traiterEchecActivation = async (id: string, err: unknown) => {
    const message = err instanceof ApiError ? err.message : 'Modification impossible.';
    const statut = err instanceof ApiError ? err.status : 0;
    if (statut === 404) {
      setUsers((prev) => prev.filter((u) => u.id !== id));
      setError(message);
      return;
    }
    if (statut === 409 || statut === 0) {
      setError(message);
      try {
        remplacerUtilisateur(await userService.getById(id));
      } catch {
        if (statut === 0) setError(`${message} État du compte incertain : actualisez la page.`);
        /* 409 : le message initial reste affiché, la ligne garde son état. */
      }
      return;
    }
    setError(message);
  };

  // La valeur cible est fixée au clic d'après l'état affiché (le backend applique une valeur
  // absolue) ; l'état local n'est jamais inversé : il vient de la relecture du serveur.
  const handleToggleActive = async (user: User) => {
    if (!prendreActivation(user.id)) return;
    const cible = !user.isActive;
    setError(null);
    try {
      try {
        if (cible) await userService.activate(user.id);
        else await userService.deactivate(user.id);
      } catch (err) {
        await traiterEchecActivation(user.id, err);
        return;
      }
      try {
        remplacerUtilisateur(await userService.getById(user.id));
      } catch {
        // Relecture impossible : la valeur demandée a été confirmée par le 204 du serveur.
        setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, isActive: cible } : u)));
      }
    } finally {
      libererActivation(user.id);
    }
  };

  // AUD-002, F9 : après un 404, le compte a disparu (retiré de la liste) ; après un 409, il est
  // relu une fois. Le message d'erreur déjà affiché n'est jamais remplacé.
  const relireUtilisateurApresEchec = async (id: string, err: unknown) => {
    const statut = err instanceof ApiError ? err.status : undefined;
    const retirer = () => setUsers((prev) => prev.filter((u) => u.id !== id));
    if (statut === 404) {
      retirer();
      return;
    }
    if (statut !== 409) return;
    try {
      remplacerUtilisateur(await userService.getById(id));
    } catch (relecture) {
      if (relecture instanceof ApiError && relecture.status === 404) retirer();
    }
  };

  // AUD-002, F9 : actualisation manuelle ; ni le formulaire ouvert ni sa cible ne sont modifiés.
  const actualiser = async () => {
    try {
      setUsers(await userService.listAll());
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Actualisation impossible.');
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
      await relireUtilisateurApresEchec(deleteTarget.id, err);
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
        actions={
          canListUsers && (
            <>
              <BoutonActualiser onActualiser={actualiser} />
              {canManageUsers && <Button leftIcon={<Plus className="h-4 w-4" />} onClick={() => setIsCreateOpen(true)}>Nouvel utilisateur</Button>}
            </>
          )
        }
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
                              label: activationsEnCours.has(user.id)
                                ? user.isActive ? 'Désactivation…' : 'Activation…'
                                : user.isActive ? 'Désactiver' : 'Activer',
                              icon: user.isActive ? <UserX className="h-4 w-4" /> : <UserCheck className="h-4 w-4" />,
                              disabled: activationsEnCours.has(user.id),
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
              <UserCard
                key={user.id}
                user={user}
                {...actionsPour(user)}
                activationEnCours={activationsEnCours.has(user.id)}
              />
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
          onConflit={rechargerUtilisateur}
        />
      )}
      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDeleteConfirm} title="Supprimer l'utilisateur ?" description={`L'utilisateur "${deleteTarget?.name}" sera définitivement supprimé.`} confirmLabel="Supprimer" isLoading={isDeleting} />
    </Container>
  );
}