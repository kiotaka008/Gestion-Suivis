import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProjectDetailPage } from '../pages/ProjectDetailPage';
import { ActivityDetailPage } from '../pages/ActivityDetailPage';
import { ActivitiesPage } from '../pages/ActivitiesPage';
import { projectService } from '../services/projectService';
import { activityService } from '../services/activityService';
import { userService } from '../services/userService';
import { activite, connecter, focusInitial, page, projet, rendre, utilisateur } from './aide';
import type { HistoryEvent } from '../types/models';

vi.mock('../hooks/useAuth', async () => {
  const aide = await import('./aide');
  return { useAuth: () => aide.authEtat };
});
vi.mock('../services/projectService', () => ({
  projectService: {
    getById: vi.fn(), activities: vi.fn(), update: vi.fn(), addMember: vi.fn(), removeMember: vi.fn(),
    remove: vi.fn(), history: vi.fn(), listAll: vi.fn(),
  },
}));
vi.mock('../services/activityService', () => ({
  activityService: { getById: vi.fn(), update: vi.fn(), remove: vi.fn(), history: vi.fn(), create: vi.fn(), listAll: vi.fn() },
}));
vi.mock('../services/userService', () => ({ userService: { listAll: vi.fn() } }));

const alice = utilisateur('alice', 'Alice Martin');
const bob = utilisateur('bob', 'Bob Durand');
const chef = utilisateur('moi', 'Chef Projet', 'manager');

function evenement(description: string): HistoryEvent {
  return {
    id: description, action: 'project.updated', entityType: 'project', entityId: 'p1', description,
    author: { id: 'moi', name: 'Chef Projet' }, fields: [], status: null, createdAt: '2026-11-12T09:00:00Z',
  };
}

const leProjet = projet('p1', 'Budget 2026', 'moi', {
  owner: chef, memberIds: ['alice'], members: [chef, alice],
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(projectService.getById).mockResolvedValue(leProjet);
  vi.mocked(projectService.activities).mockResolvedValue([activite('a1', 'Saisie', 'p1')]);
  vi.mocked(projectService.history).mockResolvedValue(page([evenement('Projet modifié : statut (Archivé)')]));
  vi.mocked(userService.listAll).mockResolvedValue([chef, alice, bob]);
});

const rendreProjet = () => rendre(<ProjectDetailPage />, { route: '/projects/p1', chemin: '/projects/:id' });

describe('ProjectDetailPage', () => {
  it('membre : aucune action de gestion', async () => {
    connecter('member', 'alice');
    rendreProjet();
    expect(await screen.findByRole('heading', { name: 'Budget 2026' })).toBeInTheDocument();
    for (const action of ['Modifier', 'Archiver', 'Supprimer']) {
      expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument();
    }
  });

  it("vraies personnes dans l'équipe (plus de « Membre x » inventé)", async () => {
    connecter('member', 'alice');
    rendreProjet();
    expect(await screen.findAllByText('Alice Martin')).not.toHaveLength(0);
    expect(screen.queryByText(/^Membre [0-9a-z]$/)).not.toBeInTheDocument();
  });

  it('chef de projet : modifier et archiver, mais pas supprimer', async () => {
    connecter('manager', 'moi');
    vi.mocked(projectService.update).mockResolvedValue({ ...leProjet, status: 'archived' });
    const user = userEvent.setup();
    rendreProjet();

    await user.click(await screen.findByRole('button', { name: 'Archiver' }));

    expect(projectService.update).toHaveBeenCalledWith('p1', { status: 'archived' });
    expect(await screen.findByRole('button', { name: 'Réactiver' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Modifier' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Supprimer' })).not.toBeInTheDocument();
  });

  it("chef de projet (responsable) : pas de choix du chef de projet ; administrateur : choix proposé", async () => {
    connecter('manager', 'moi');
    const user = userEvent.setup();
    const { unmount } = rendreProjet();
    await user.click(await screen.findByRole('button', { name: 'Modifier' }));
    await focusInitial();
    expect(screen.getByLabelText('Nom du projet')).toHaveValue('Budget 2026');
    expect(screen.queryByLabelText('Chef de projet')).not.toBeInTheDocument();
    unmount();

    connecter('admin', 'admin');
    rendreProjet();
    await user.click(await screen.findByRole('button', { name: 'Modifier' }));
    await focusInitial();
    await waitFor(() => expect(screen.getByLabelText('Chef de projet')).toBeInTheDocument());
  });

  it('membres : ajout parmi les utilisateurs non membres, puis retrait', async () => {
    connecter('manager', 'moi');
    const user = userEvent.setup();
    vi.mocked(projectService.addMember).mockResolvedValue({
      ...leProjet, memberIds: ['alice', 'bob'], members: [chef, alice, bob],
    });
    vi.mocked(projectService.removeMember).mockResolvedValue({ ...leProjet, memberIds: [], members: [chef] });
    rendreProjet();

    await user.click(await screen.findByRole('button', { name: 'Membres' }));
    const choix = await screen.findByLabelText('Ajouter un membre');
    // Seul Bob n'est ni chef ni déjà membre.
    await waitFor(() =>
      expect(Array.from((choix as HTMLSelectElement).options).map((o) => o.value)).toEqual(['', 'bob'])
    );
    await user.selectOptions(choix, 'bob');
    await user.click(screen.getByRole('button', { name: 'Ajouter' }));
    expect(projectService.addMember).toHaveBeenCalledWith('p1', 'bob');

    await user.click(await screen.findByRole('button', { name: 'Retirer Alice Martin du projet' }));
    expect(projectService.removeMember).toHaveBeenCalledWith('p1', 'alice');
  });

  it("onglet Historique : actions renvoyées par le backend", async () => {
    connecter('member', 'alice');
    const user = userEvent.setup();
    rendreProjet();
    await user.click(await screen.findByRole('button', { name: 'Historique' }));
    expect(await screen.findByText('Projet modifié : statut (Archivé)')).toBeInTheDocument();
    expect(projectService.history).toHaveBeenCalledWith('p1', 1, 20);
  });
});

describe('ActivityDetailPage', () => {
  const lActivite = activite('a1', 'Saisie des dépenses', 'p1', {
    projectName: 'Budget 2026', assigneeId: 'alice', assignee: alice, progress: 40, status: 'in_progress',
  });

  beforeEach(() => {
    vi.mocked(activityService.getById).mockResolvedValue(lActivite);
    vi.mocked(activityService.history).mockResolvedValue(page([evenement('Activité modifiée : progression')]));
  });

  const rendreActivite = () => rendre(<ActivityDetailPage />, { route: '/activities/a1', chemin: '/activities/:id' });

  it("responsable de l'activité : met à jour l'avancement seulement", async () => {
    connecter('member', 'alice');
    vi.mocked(activityService.update).mockResolvedValue({ ...lActivite, progress: 80 });
    const user = userEvent.setup();
    rendreActivite();

    await user.click(await screen.findByRole('button', { name: "Mettre à jour l'avancement" }));
    expect(screen.queryByRole('button', { name: 'Supprimer' })).not.toBeInTheDocument();
    await focusInitial();
    expect(screen.queryByLabelText("Titre de l'activité")).not.toBeInTheDocument();
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '80');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(activityService.update).toHaveBeenCalledWith('a1', { status: 'in_progress', progress: 80 });
    expect(await screen.findByText('80%')).toBeInTheDocument();
  });

  it('chef du projet : modification complète et suppression', async () => {
    connecter('manager', 'moi');
    rendreActivite();
    expect(await screen.findByRole('button', { name: 'Modifier' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Supprimer' })).toBeInTheDocument();
  });

  it('historique de l’activité affiché', async () => {
    connecter('member', 'alice');
    rendreActivite();
    expect(await screen.findByText('Activité modifiée : progression')).toBeInTheDocument();
    expect(activityService.history).toHaveBeenCalledWith('a1', 1, 20);
  });

  it('activité introuvable ou non accessible', async () => {
    connecter('member', 'alice');
    vi.mocked(activityService.getById).mockRejectedValue(
      Object.assign(new Error('introuvable'), { status: 404 })
    );
    rendreActivite();
    expect(await screen.findByText('Activité introuvable')).toBeInTheDocument();
  });
});

describe('ActivitiesPage', () => {
  it("membre responsable : « Mettre à jour l'avancement » sans « Nouvelle activité »", async () => {
    connecter('member', 'alice');
    vi.mocked(activityService.listAll).mockResolvedValue([
      activite('a1', 'Saisie', 'p1', { assigneeId: 'alice', status: 'todo', progress: 0 }),
    ]);
    vi.mocked(projectService.listAll).mockResolvedValue([leProjet]);
    vi.mocked(activityService.update).mockResolvedValue(
      activite('a1', 'Saisie', 'p1', { assigneeId: 'alice', status: 'done', progress: 100 })
    );
    const user = userEvent.setup();
    rendre(<ActivitiesPage />);

    expect(await screen.findByText('Saisie')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Nouvelle activité' })).not.toBeInTheDocument();
    expect(userService.listAll).not.toHaveBeenCalled();

    await user.click(screen.getByLabelText('Vue cartes'));
    await user.click(screen.getByLabelText("Actions de l'activité"));
    expect(screen.getAllByRole('menuitem').map((b) => b.textContent)).toEqual(['Voir', 'Modifier']);
    await user.click(screen.getByRole('menuitem', { name: 'Modifier' }));
    await focusInitial();
    expect(screen.getByRole('heading', { name: "Mettre à jour l'avancement" })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Statut'), 'done');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(activityService.update).toHaveBeenCalledWith('a1', { status: 'done', progress: 0 });
  });

  it('tableau : un clic sur une ligne ouvre le détail de l’activité', async () => {
    connecter('manager', 'moi');
    vi.mocked(activityService.listAll).mockResolvedValue([activite('a1', 'Saisie', 'p1')]);
    vi.mocked(projectService.listAll).mockResolvedValue([leProjet]);
    const user = userEvent.setup();
    rendre(<ActivitiesPage />);
    await user.click(await screen.findByText('Saisie'));
    expect(screen.getByTestId('emplacement')).toHaveTextContent('/activities/a1');
  });
});
