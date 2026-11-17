import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Emplacement } from './Emplacement';
import { ProjectDetailPage } from '../pages/ProjectDetailPage';
import { ActivityDetailPage } from '../pages/ActivityDetailPage';
import { ActivitiesPage } from '../pages/ActivitiesPage';
import { projectService } from '../services/projectService';
import { activityService } from '../services/activityService';
import { userService } from '../services/userService';
import { ApiError } from '../services/ApiError';
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

    // AUD-002, F4 : le statut inchangé n'est pas renvoyé.
    expect(activityService.update).toHaveBeenCalledWith('a1', { progress: 80 });
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
    // AUD-002, F4 : la progression inchangée n'est pas renvoyée (le backend met « terminée » à 100 %).
    expect(activityService.update).toHaveBeenCalledWith('a1', { status: 'done' });
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

// Projet terminé ou archivé : activités en lecture seule (AUD-012, le backend refuse avec 409).
describe('Projet clôturé : activités en lecture seule', () => {
  const lActivite = activite('a1', 'Saisie des dépenses', 'p1', {
    projectName: 'Budget 2026', assigneeId: 'alice', assignee: alice, progress: 40, status: 'in_progress',
  });
  const projetAvecStatut = (status: 'active' | 'on_hold' | 'completed' | 'archived') => ({ ...leProjet, status });
  const rendreActivite = () => rendre(<ActivityDetailPage />, { route: '/activities/a1', chemin: '/activities/:id' });
  const ACTIONS = ['Modifier', 'Supprimer', "Mettre à jour l'avancement"];

  beforeEach(() => {
    vi.mocked(activityService.getById).mockResolvedValue(lActivite);
    vi.mocked(activityService.history).mockResolvedValue(page([]));
  });

  describe('ActivityDetailPage', () => {
    it.each(['completed', 'archived'] as const)(
      'projet %s, chef du projet : aucune action, lecture seule annoncée avec la réactivation',
      async (status) => {
        connecter('manager', 'moi');
        vi.mocked(projectService.getById).mockResolvedValue(projetAvecStatut(status));
        rendreActivite();

        expect(await screen.findByText(/ses activités sont en\s+lecture seule/)).toBeInTheDocument();
        expect(screen.getByText(/Réactivez le projet pour les modifier/)).toBeInTheDocument();
        for (const action of ACTIONS) {
          expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument();
        }
        // Le reste de la page reste affiché.
        expect(screen.getByRole('heading', { name: 'Saisie des dépenses' })).toBeInTheDocument();
        expect(screen.getByText('40%')).toBeInTheDocument();
      }
    );

    it.each(['completed', 'archived'] as const)(
      "projet %s, personne affectée : pas de « Mettre à jour l'avancement », pas d'invitation à réactiver",
      async (status) => {
        connecter('member', 'alice');
        vi.mocked(projectService.getById).mockResolvedValue(projetAvecStatut(status));
        rendreActivite();

        expect(await screen.findByText(/ses activités sont en\s+lecture seule/)).toBeInTheDocument();
        expect(screen.queryByText(/Réactivez le projet/)).not.toBeInTheDocument();
        for (const action of ACTIONS) {
          expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument();
        }
      }
    );

    it.each(['active', 'on_hold'] as const)('projet %s : actions habituelles selon les droits', async (status) => {
      vi.mocked(projectService.getById).mockResolvedValue(projetAvecStatut(status));

      connecter('manager', 'moi');
      const premier = rendreActivite();
      expect(await screen.findByRole('button', { name: 'Modifier' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Supprimer' })).toBeInTheDocument();
      expect(screen.queryByText(/lecture seule/)).not.toBeInTheDocument();
      premier.unmount();

      connecter('member', 'alice');
      rendreActivite();
      expect(await screen.findByRole('button', { name: "Mettre à jour l'avancement" })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Supprimer' })).not.toBeInTheDocument();
    });

    it.each(['active', 'completed'] as const)(
      'projet %s, utilisateur sans droit : aucune action ne devient accessible',
      async (status) => {
        connecter('member', 'bob');
        vi.mocked(projectService.getById).mockResolvedValue(projetAvecStatut(status));
        rendreActivite();

        expect(await screen.findByRole('heading', { name: 'Saisie des dépenses' })).toBeInTheDocument();
        for (const action of ACTIONS) {
          expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument();
        }
      }
    );
  });

  describe('ActivitiesPage (vue cartes)', () => {
    const menuDeLaCarte = async () => {
      const user = userEvent.setup();
      rendre(<ActivitiesPage />);
      expect(await screen.findByText('Saisie des dépenses')).toBeInTheDocument();
      await user.click(screen.getByLabelText('Vue cartes'));
      await user.click(screen.getByLabelText("Actions de l'activité"));
      return screen.getAllByRole('menuitem').map((b) => b.textContent);
    };

    beforeEach(() => {
      vi.mocked(activityService.listAll).mockResolvedValue([lActivite]);
    });

    it.each(['completed', 'archived'] as const)(
      'projet %s : chef du projet et administrateur ne voient que « Voir »',
      async (status) => {
        vi.mocked(projectService.listAll).mockResolvedValue([projetAvecStatut(status)]);

        connecter('manager', 'moi');
        expect(await menuDeLaCarte()).toEqual(['Voir']);
        // Seul projet géré clôturé : pas de création non plus.
        expect(screen.queryByRole('button', { name: 'Nouvelle activité' })).not.toBeInTheDocument();
      }
    );

    it.each(['completed', 'archived'] as const)('projet %s : administrateur, « Voir » seulement', async (status) => {
      vi.mocked(projectService.listAll).mockResolvedValue([projetAvecStatut(status)]);
      connecter('admin', 'admin1');
      expect(await menuDeLaCarte()).toEqual(['Voir']);
    });

    it.each(['completed', 'archived'] as const)('projet %s : personne affectée, « Voir » seulement', async (status) => {
      vi.mocked(projectService.listAll).mockResolvedValue([projetAvecStatut(status)]);
      connecter('member', 'alice');
      expect(await menuDeLaCarte()).toEqual(['Voir']);
    });

    it.each(['active', 'on_hold'] as const)('projet %s : menu habituel selon les droits', async (status) => {
      vi.mocked(projectService.listAll).mockResolvedValue([projetAvecStatut(status)]);
      connecter('manager', 'moi');
      expect(await menuDeLaCarte()).toEqual(['Voir', 'Modifier', 'Supprimer']);
      expect(screen.getByRole('button', { name: 'Nouvelle activité' })).toBeInTheDocument();
    });
  });

  describe('ProjectDetailPage', () => {
    it('projet archivé : lecture seule annoncée ; après réactivation, la création redevient possible', async () => {
      connecter('manager', 'moi');
      vi.mocked(projectService.getById).mockResolvedValue(projetAvecStatut('archived'));
      vi.mocked(projectService.update).mockResolvedValue(projetAvecStatut('active'));
      const user = userEvent.setup();
      rendreProjet();

      await user.click(await screen.findByRole('button', { name: 'Activités' }));
      expect(screen.getByText(/les activités sont en\s+lecture seule/)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Nouvelle activité' })).not.toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'Réactiver' }));

      expect(projectService.update).toHaveBeenCalledWith('p1', { status: 'active' });
      expect(await screen.findByRole('button', { name: 'Nouvelle activité' })).toBeInTheDocument();
      expect(screen.queryByText(/lecture seule/)).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Archiver' })).toBeInTheDocument();
    });

    it('projet terminé, membre sans droit : lecture seule annoncée, sans invitation à réactiver', async () => {
      connecter('member', 'alice');
      vi.mocked(projectService.getById).mockResolvedValue(projetAvecStatut('completed'));
      const user = userEvent.setup();
      rendreProjet();

      await user.click(await screen.findByRole('button', { name: 'Activités' }));
      expect(screen.getByText(/les activités sont en\s+lecture seule/)).toBeInTheDocument();
      expect(screen.queryByText(/Réactivez le projet/)).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Réactiver' })).not.toBeInTheDocument();
    });

    it('projet actif : pas de mention de lecture seule, création disponible pour le chef', async () => {
      connecter('manager', 'moi');
      const user = userEvent.setup();
      rendreProjet();

      await user.click(await screen.findByRole('button', { name: 'Activités' }));
      expect(screen.getByRole('button', { name: 'Nouvelle activité' })).toBeInTheDocument();
      expect(screen.queryByText(/lecture seule/)).not.toBeInTheDocument();
    });
  });
});

// AUD-002, F6 : conflit 409 en modification, données relues, saisie conservée.
describe('Conflit 409 : relecture des données, saisie conservée', () => {
  const lActivite = activite('a1', 'Saisie des dépenses', 'p1', {
    projectName: 'Budget 2026', assigneeId: 'alice', assignee: alice, progress: 40, status: 'in_progress',
  });
  const conflit = new ApiError('Modifié entre-temps.', 409, 'CONFLICT');

  beforeEach(() => {
    vi.mocked(activityService.getById).mockResolvedValue(lActivite);
    vi.mocked(activityService.history).mockResolvedValue(page([]));
  });

  it('ActivityDetailPage : activité et projet relus, saisie conservée', async () => {
    connecter('manager', 'moi');
    vi.mocked(activityService.update).mockRejectedValue(conflit);
    const user = userEvent.setup();
    rendre(<ActivityDetailPage />, { route: '/activities/a1', chemin: '/activities/:id' });

    await user.click(await screen.findByRole('button', { name: 'Modifier' }));
    await focusInitial();
    const titre = screen.getByLabelText("Titre de l'activité");
    await user.clear(titre);
    await user.type(titre, 'Ma saisie');
    vi.mocked(activityService.getById).mockClear();
    vi.mocked(projectService.getById).mockClear();
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Modifié entre-temps.')).toBeInTheDocument();
    await waitFor(() => expect(activityService.getById).toHaveBeenCalledWith('a1'));
    await waitFor(() => expect(projectService.getById).toHaveBeenCalledWith('p1'));
    expect(await screen.findByRole('button', { name: 'Recharger les valeurs actuelles' })).toBeInTheDocument();
    expect(screen.getByLabelText("Titre de l'activité")).toHaveValue('Ma saisie');
  });

  it('ActivitiesPage : activité et projet relus après un 409, saisie conservée', async () => {
    connecter('member', 'alice');
    vi.mocked(activityService.listAll).mockResolvedValue([lActivite]);
    vi.mocked(projectService.listAll).mockResolvedValue([leProjet]);
    vi.mocked(activityService.update).mockRejectedValue(conflit);
    const user = userEvent.setup();
    rendre(<ActivitiesPage />);

    expect(await screen.findByText('Saisie des dépenses')).toBeInTheDocument();
    await user.click(screen.getByLabelText('Vue cartes'));
    await user.click(screen.getByLabelText("Actions de l'activité"));
    await user.click(screen.getByRole('menuitem', { name: 'Modifier' }));
    await focusInitial();
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '75');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Modifié entre-temps.')).toBeInTheDocument();
    await waitFor(() => expect(activityService.getById).toHaveBeenCalledWith('a1'));
    await waitFor(() => expect(projectService.getById).toHaveBeenCalledWith('p1'));
    expect(screen.getByLabelText('Progression (%)')).toHaveValue(75);
  });

  it('ProjectDetailPage : projet relu après un 409, saisie conservée', async () => {
    connecter('manager', 'moi');
    vi.mocked(projectService.update).mockRejectedValue(conflit);
    const user = userEvent.setup();
    rendreProjet();

    await user.click(await screen.findByRole('button', { name: 'Modifier' }));
    await focusInitial();
    const nom = screen.getByLabelText('Nom du projet');
    await user.clear(nom);
    await user.type(nom, 'Ma saisie');
    vi.mocked(projectService.getById).mockClear();
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Modifié entre-temps.')).toBeInTheDocument();
    await waitFor(() => expect(projectService.getById).toHaveBeenCalledWith('p1'));
    expect(screen.getByLabelText('Nom du projet')).toHaveValue('Ma saisie');
  });
});

// AUD-002, F6 : projet clôturé pendant la modification d'une activité. Le formulaire déjà ouvert
// reste affiché, verrouillé (lecture seule, aucun enregistrement) ; AUD-012 reste appliqué.
describe('Projet clôturé pendant la modification d’une activité (AUD-002, F6)', () => {
  const lActivite = activite('a1', 'Saisie des dépenses', 'p1', {
    projectName: 'Budget 2026', assigneeId: 'alice', assignee: alice, progress: 40, status: 'in_progress',
  });
  const cloture = new ApiError('Ce projet est clôturé : ses activités sont en lecture seule.', 409, 'CONFLICT');
  const projetClos = (status: 'completed' | 'archived') => ({ ...leProjet, status });
  const VERROU = {
    completed: 'Ce projet est terminé : ses activités sont en lecture seule.',
    archived: 'Ce projet est archivé : ses activités sont en lecture seule.',
  };
  const formulaire = () => document.getElementById('edit-activity-form') as HTMLFormElement;
  const piedDuFormulaire = () => screen.getByRole('button', { name: 'Enregistrer' }).parentElement as HTMLElement;

  // Même arbre que `rendre` : un nouveau rendu conserve l'état de la page (droits relus).
  const arbreActivite = () => (
    <MemoryRouter initialEntries={['/activities/a1']}>
      <Routes>
        <Route path="/activities/:id" element={<><ActivityDetailPage /><Emplacement /></>} />
        <Route path="*" element={<Emplacement />} />
      </Routes>
    </MemoryRouter>
  );

  beforeEach(() => {
    vi.mocked(activityService.getById).mockResolvedValue(lActivite);
    vi.mocked(activityService.history).mockResolvedValue(page([]));
  });

  it.each(['completed', 'archived'] as const)(
    'ActivityDetailPage, chef du projet : projet %s pendant la modification, formulaire verrouillé et saisie conservée',
    async (status) => {
      connecter('manager', 'moi');
      vi.mocked(activityService.update).mockRejectedValue(cloture);
      const user = userEvent.setup();
      render(arbreActivite());

      await user.click(await screen.findByRole('button', { name: 'Modifier' }));
      await focusInitial();
      const titre = screen.getByLabelText("Titre de l'activité");
      await user.clear(titre);
      await user.type(titre, 'Ma saisie');
      // Entre-temps, le projet a été clôturé : la relecture qui suit le 409 le découvre.
      vi.mocked(projectService.getById).mockResolvedValue(projetClos(status));
      await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

      const dialogue = screen.getByRole('dialog');
      expect(await within(dialogue).findByText(VERROU[status])).toBeInTheDocument();
      // Formulaire toujours affiché, en modification complète, saisie conservée.
      expect(within(dialogue).getByRole('heading', { name: "Modifier l'activité" })).toBeInTheDocument();
      expect(screen.getByLabelText("Titre de l'activité")).toHaveValue('Ma saisie');
      expect(screen.getByLabelText("Titre de l'activité")).toHaveAttribute('readonly');
      expect(screen.getByLabelText("Titre de l'activité")).toBeEnabled();
      expect(screen.getByLabelText('Statut')).toBeDisabled();
      expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
      expect(screen.queryByRole('button', { name: 'Recharger les valeurs actuelles' })).not.toBeInTheDocument();

      fireEvent.submit(formulaire());
      expect(activityService.update).toHaveBeenCalledTimes(1);

      // Fermeture : AUD-012 s'applique, aucune action de modification n'est proposée.
      await user.click(within(piedDuFormulaire()).getByRole('button', { name: 'Fermer' }));
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(screen.getByText(/ses activités sont en\s+lecture seule/)).toBeInTheDocument();
      for (const action of ['Modifier', 'Supprimer', "Mettre à jour l'avancement"]) {
        expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument();
      }
    }
  );

  it('ActivityDetailPage, personne affectée : verrouillé en suivi seul, progression saisie conservée', async () => {
    connecter('member', 'alice');
    vi.mocked(activityService.update).mockRejectedValue(cloture);
    const user = userEvent.setup();
    render(arbreActivite());

    await user.click(await screen.findByRole('button', { name: "Mettre à jour l'avancement" }));
    await focusInitial();
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '75');
    vi.mocked(projectService.getById).mockResolvedValue(projetClos('completed'));
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    const dialogue = screen.getByRole('dialog');
    expect(await within(dialogue).findByText(VERROU.completed)).toBeInTheDocument();
    expect(within(dialogue).getByRole('heading', { name: "Mettre à jour l'avancement" })).toBeInTheDocument();
    expect(screen.getByLabelText('Progression (%)')).toHaveValue(75);
    expect(screen.getByLabelText('Progression (%)')).toHaveAttribute('readonly');
    expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
    fireEvent.submit(formulaire());
    expect(activityService.update).toHaveBeenCalledTimes(1);
  });

  it('perte de droits sans clôture : le formulaire se ferme et ne réapparaît pas quand les droits reviennent', async () => {
    connecter('manager', 'moi');
    const user = userEvent.setup();
    const { rerender } = render(arbreActivite());

    await user.click(await screen.findByRole('button', { name: 'Modifier' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    // Droits perdus (autre utilisateur, projet toujours actif) : fermeture, comme avant.
    connecter('member', 'bob');
    rerender(arbreActivite());
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByText(/lecture seule/)).not.toBeInTheDocument();

    // Droits retrouvés : l'ancien formulaire ne se rouvre pas tout seul.
    connecter('manager', 'moi');
    rerender(arbreActivite());
    expect(await screen.findByRole('button', { name: 'Modifier' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('perte de droits révélée par la relecture après un 409 (activité réaffectée) : formulaire fermé', async () => {
    connecter('member', 'alice');
    vi.mocked(activityService.update).mockRejectedValue(new ApiError('Modifié entre-temps.', 409, 'CONFLICT'));
    const user = userEvent.setup();
    render(arbreActivite());

    await user.click(await screen.findByRole('button', { name: "Mettre à jour l'avancement" }));
    await focusInitial();
    vi.mocked(activityService.getById).mockResolvedValue({ ...lActivite, assigneeId: 'bob', assignee: bob });
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '75');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: "Mettre à jour l'avancement" })).not.toBeInTheDocument();
  });

  it('projet déjà clôturé : aucun formulaire de modification ne peut s’ouvrir', async () => {
    connecter('manager', 'moi');
    vi.mocked(projectService.getById).mockResolvedValue(projetClos('completed'));
    const { rerender } = render(arbreActivite());

    expect(await screen.findByText(/ses activités sont en\s+lecture seule/)).toBeInTheDocument();
    for (const action of ['Modifier', 'Supprimer', "Mettre à jour l'avancement"]) {
      expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument();
    }
    rerender(arbreActivite());
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('ActivitiesPage : projet clôturé pendant la modification, formulaire verrouillé, puis « Voir » seulement', async () => {
    connecter('member', 'alice');
    vi.mocked(activityService.listAll).mockResolvedValue([lActivite]);
    vi.mocked(projectService.listAll).mockResolvedValue([leProjet]);
    vi.mocked(activityService.update).mockRejectedValue(cloture);
    const user = userEvent.setup();
    rendre(<ActivitiesPage />);

    expect(await screen.findByText('Saisie des dépenses')).toBeInTheDocument();
    await user.click(screen.getByLabelText('Vue cartes'));
    await user.click(screen.getByLabelText("Actions de l'activité"));
    await user.click(screen.getByRole('menuitem', { name: 'Modifier' }));
    await focusInitial();
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '75');
    vi.mocked(projectService.getById).mockResolvedValue(projetClos('archived'));
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    const dialogue = screen.getByRole('dialog');
    expect(await within(dialogue).findByText(VERROU.archived)).toBeInTheDocument();
    expect(screen.getByLabelText('Progression (%)')).toHaveValue(75);
    expect(screen.getByLabelText('Progression (%)')).toHaveAttribute('readonly');
    expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Recharger les valeurs actuelles' })).not.toBeInTheDocument();
    fireEvent.submit(formulaire());
    expect(activityService.update).toHaveBeenCalledTimes(1);

    await user.click(within(piedDuFormulaire()).getByRole('button', { name: 'Fermer' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await user.click(screen.getByLabelText("Actions de l'activité"));
    expect(screen.getAllByRole('menuitem').map((b) => b.textContent)).toEqual(['Voir']);
  });
});

// AUD-002, F9 : relectures ciblées après 404/409 et actualisation manuelle (pages de détail).
describe('Pages de détail : relectures après erreur et actualisation (AUD-002, F9)', () => {
  const lActivite = activite('a1', 'Saisie des dépenses', 'p1', {
    projectName: 'Budget 2026', assigneeId: 'alice', assignee: alice, progress: 40, status: 'in_progress',
  });
  const formulaire = () => document.getElementById('edit-activity-form') as HTMLFormElement;
  const rendreActivite = () => rendre(<ActivityDetailPage />, { route: '/activities/a1', chemin: '/activities/:id' });

  beforeEach(() => {
    vi.mocked(activityService.getById).mockResolvedValue(lActivite);
    vi.mocked(activityService.history).mockResolvedValue(page([]));
  });

  describe('ProjectDetailPage', () => {
    it('« Actualiser » relit le projet, ses activités et son historique', async () => {
      connecter('member', 'alice');
      const user = userEvent.setup();
      rendreProjet();
      expect(await screen.findByRole('heading', { name: 'Budget 2026' })).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: 'Historique' }));
      await screen.findByText('Projet modifié : statut (Archivé)');

      vi.mocked(projectService.getById).mockResolvedValue({ ...leProjet, name: 'Budget relu' });
      vi.mocked(projectService.activities).mockResolvedValue([activite('a2', 'Nouvelle saisie', 'p1')]);
      vi.mocked(projectService.history).mockResolvedValue(page([evenement('Activité ajoutée')]));
      await user.click(screen.getByRole('button', { name: 'Actualiser' }));

      expect(await screen.findByRole('heading', { name: 'Budget relu' })).toBeInTheDocument();
      expect(await screen.findByText('Activité ajoutée')).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: 'Activités' }));
      expect(screen.getByText('Nouvelle saisie')).toBeInTheDocument();
    });

    it('archivage refusé (409) : message conservé, projet relu une seule fois', async () => {
      connecter('manager', 'moi');
      vi.mocked(projectService.update).mockRejectedValue(new ApiError('Projet modifié entre-temps.', 409, 'CONFLICT'));
      const user = userEvent.setup();
      rendreProjet();
      const archiver = await screen.findByRole('button', { name: 'Archiver' });
      // Entre-temps, quelqu'un d'autre a archivé le projet : la relecture le découvre.
      vi.mocked(projectService.getById).mockClear();
      vi.mocked(projectService.getById).mockResolvedValue({ ...leProjet, status: 'archived' });
      await user.click(archiver);

      expect(await screen.findByRole('button', { name: 'Réactiver' })).toBeInTheDocument();
      expect(screen.getByText('Projet modifié entre-temps.')).toBeInTheDocument();
      expect(projectService.getById).toHaveBeenCalledTimes(1);
    });

    it('ajout de membre en 404 (utilisateur disparu) : projet relu, page conservée, choix conservé', async () => {
      connecter('manager', 'moi');
      vi.mocked(projectService.addMember).mockRejectedValue(new ApiError('Utilisateur introuvable.', 404, 'NOT_FOUND'));
      const user = userEvent.setup();
      rendreProjet();

      await user.click(await screen.findByRole('button', { name: 'Membres' }));
      const choix = await screen.findByLabelText('Ajouter un membre');
      await waitFor(() => expect((choix as HTMLSelectElement).options.length).toBe(2));
      await user.selectOptions(choix, 'bob');
      vi.mocked(projectService.getById).mockClear();
      await user.click(screen.getByRole('button', { name: 'Ajouter' }));

      expect(await screen.findByText('Utilisateur introuvable.')).toBeInTheDocument();
      await waitFor(() => expect(projectService.getById).toHaveBeenCalledTimes(1));
      expect(screen.getByRole('heading', { name: 'Budget 2026' })).toBeInTheDocument();
      expect(screen.getByLabelText('Ajouter un membre')).toHaveValue('bob');
    });

    it('retrait de membre en 409 puis projet disparu à la relecture : « Projet introuvable » avec le message', async () => {
      connecter('manager', 'moi');
      vi.mocked(projectService.removeMember).mockRejectedValue(new ApiError('Projet modifié entre-temps.', 409, 'CONFLICT'));
      const user = userEvent.setup();
      rendreProjet();

      await user.click(await screen.findByRole('button', { name: 'Membres' }));
      vi.mocked(projectService.getById).mockRejectedValue(new ApiError('Projet introuvable.', 404, 'NOT_FOUND'));
      await user.click(await screen.findByRole('button', { name: 'Retirer Alice Martin du projet' }));

      expect(await screen.findByText('Projet introuvable')).toBeInTheDocument();
      expect(screen.getByText('Projet modifié entre-temps.')).toBeInTheDocument();
    });

    it('suppression en 404 : « Projet introuvable » avec le message du backend', async () => {
      connecter('admin', 'admin');
      vi.mocked(projectService.remove).mockRejectedValue(new ApiError('Ce projet a déjà été supprimé.', 404, 'NOT_FOUND'));
      const user = userEvent.setup();
      rendreProjet();

      await user.click(await screen.findByRole('button', { name: 'Supprimer' }));
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Supprimer' }));

      expect(await screen.findByText('Projet introuvable')).toBeInTheDocument();
      expect(screen.getByText('Ce projet a déjà été supprimé.')).toBeInTheDocument();
    });

    it('création d’activité refusée (409, projet clôturé) : projet relu, saisie et message conservés', async () => {
      connecter('manager', 'moi');
      vi.mocked(activityService.create).mockRejectedValue(
        new ApiError('Ce projet est terminé : ses activités sont en lecture seule.', 409, 'CONFLICT')
      );
      const user = userEvent.setup();
      rendreProjet();

      await user.click(await screen.findByRole('button', { name: 'Activités' }));
      await user.click(screen.getByRole('button', { name: 'Nouvelle activité' }));
      await focusInitial();
      await user.type(screen.getByLabelText("Titre de l'activité"), 'Ma nouvelle activité');
      vi.mocked(projectService.getById).mockClear();
      vi.mocked(projectService.getById).mockResolvedValue({ ...leProjet, status: 'completed' });
      await user.click(screen.getByRole('button', { name: "Créer l'activité" }));

      expect(
        await within(screen.getByRole('dialog')).findByText('Ce projet est terminé : ses activités sont en lecture seule.')
      ).toBeInTheDocument();
      await waitFor(() => expect(projectService.getById).toHaveBeenCalledTimes(1));
      expect(screen.getByLabelText("Titre de l'activité")).toHaveValue('Ma nouvelle activité');
    });
  });

  describe('ActivityDetailPage', () => {
    it('« Actualiser » relit l’activité et son projet', async () => {
      connecter('member', 'alice');
      const user = userEvent.setup();
      rendreActivite();
      expect(await screen.findByRole('heading', { name: 'Saisie des dépenses' })).toBeInTheDocument();

      vi.mocked(activityService.getById).mockResolvedValue({ ...lActivite, title: 'Saisie relue', progress: 90 });
      await user.click(screen.getByRole('button', { name: 'Actualiser' }));

      expect(await screen.findByRole('heading', { name: 'Saisie relue' })).toBeInTheDocument();
      expect(screen.getByText('90%')).toBeInTheDocument();
      expect(projectService.getById).toHaveBeenLastCalledWith('p1');
    });

    it('« Actualiser » : activité disparue, « Activité introuvable » avec un message', async () => {
      connecter('member', 'alice');
      const user = userEvent.setup();
      rendreActivite();
      await screen.findByRole('heading', { name: 'Saisie des dépenses' });

      vi.mocked(activityService.getById).mockRejectedValue(new ApiError('Activité introuvable.', 404, 'NOT_FOUND'));
      await user.click(screen.getByRole('button', { name: 'Actualiser' }));

      expect(await screen.findByText('Activité introuvable')).toBeInTheDocument();
      expect(screen.getByText("L'activité n'existe plus ou ne vous est plus accessible.")).toBeInTheDocument();
    });

    it('« Actualiser » avec le formulaire ouvert : projet découvert clôturé, formulaire verrouillé, saisie conservée', async () => {
      connecter('manager', 'moi');
      const user = userEvent.setup();
      rendreActivite();

      await user.click(await screen.findByRole('button', { name: 'Modifier' }));
      await focusInitial();
      const titre = screen.getByLabelText("Titre de l'activité");
      await user.clear(titre);
      await user.type(titre, 'Ma saisie');
      vi.mocked(activityService.getById).mockResolvedValue({ ...lActivite, title: 'Titre modifié ailleurs' });
      vi.mocked(projectService.getById).mockResolvedValue({ ...leProjet, status: 'completed' });

      await user.click(screen.getByRole('button', { name: 'Actualiser' }));

      expect(
        await within(screen.getByRole('dialog')).findByText('Ce projet est terminé : ses activités sont en lecture seule.')
      ).toBeInTheDocument();
      expect(screen.getByLabelText("Titre de l'activité")).toHaveValue('Ma saisie');
      expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
      fireEvent.submit(formulaire());
      expect(activityService.update).not.toHaveBeenCalled();
    });

    it('F6 : formulaire verrouillé fermé, projet rouvert puis actualisé, le formulaire ne réapparaît pas', async () => {
      connecter('manager', 'moi');
      const user = userEvent.setup();
      rendreActivite();

      await user.click(await screen.findByRole('button', { name: 'Modifier' }));
      await focusInitial();
      vi.mocked(projectService.getById).mockResolvedValue({ ...leProjet, status: 'completed' });
      await user.click(screen.getByRole('button', { name: 'Actualiser' }));
      await within(screen.getByRole('dialog')).findByText('Ce projet est terminé : ses activités sont en lecture seule.');

      const pied = screen.getByRole('button', { name: 'Enregistrer' }).parentElement as HTMLElement;
      await user.click(within(pied).getByRole('button', { name: 'Fermer' }));
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

      // Le projet est rouvert, puis la page est actualisée.
      vi.mocked(projectService.getById).mockResolvedValue({ ...leProjet, status: 'active' });
      await user.click(screen.getByRole('button', { name: 'Actualiser' }));

      expect(await screen.findByRole('button', { name: 'Modifier' })).toBeInTheDocument();
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('suppression en 409 (projet clôturé entre-temps) : message conservé, relecture, actions masquées', async () => {
      connecter('manager', 'moi');
      vi.mocked(activityService.remove).mockRejectedValue(
        new ApiError('Ce projet est terminé : ses activités sont en lecture seule.', 409, 'CONFLICT')
      );
      const user = userEvent.setup();
      rendreActivite();

      await user.click(await screen.findByRole('button', { name: 'Supprimer' }));
      vi.mocked(activityService.getById).mockClear();
      vi.mocked(projectService.getById).mockResolvedValue({ ...leProjet, status: 'completed' });
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Supprimer' }));

      expect(await screen.findByText('Ce projet est terminé : ses activités sont en lecture seule.')).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByRole('button', { name: 'Modifier' })).not.toBeInTheDocument());
      expect(screen.queryByRole('button', { name: 'Supprimer' })).not.toBeInTheDocument();
      expect(activityService.getById).toHaveBeenCalledTimes(1);
    });

    it('suppression en 404 : « Activité introuvable » avec le message du backend', async () => {
      connecter('manager', 'moi');
      vi.mocked(activityService.remove).mockRejectedValue(new ApiError('Activité déjà supprimée.', 404, 'NOT_FOUND'));
      const user = userEvent.setup();
      rendreActivite();

      await user.click(await screen.findByRole('button', { name: 'Supprimer' }));
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Supprimer' }));

      expect(await screen.findByText('Activité introuvable')).toBeInTheDocument();
      expect(screen.getByText('Activité déjà supprimée.')).toBeInTheDocument();
    });
  });

  describe('ActivitiesPage', () => {
    const autre = activite('a2', 'Autre activité', 'p1');

    async function supprimerDepuisLaCarte(user: ReturnType<typeof userEvent.setup>) {
      expect(await screen.findByText('Saisie des dépenses')).toBeInTheDocument();
      await user.click(screen.getByLabelText('Vue cartes'));
      const carte = screen.getByText('Saisie des dépenses').closest('div.group') as HTMLElement;
      await user.click(within(carte).getByLabelText("Actions de l'activité"));
      await user.click(screen.getByRole('menuitem', { name: 'Supprimer' }));
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Supprimer' }));
    }

    beforeEach(() => {
      connecter('manager', 'moi');
      vi.mocked(activityService.listAll).mockResolvedValue([lActivite, autre]);
      vi.mocked(projectService.listAll).mockResolvedValue([leProjet]);
    });

    it('« Actualiser » relit activités, projets et utilisateurs', async () => {
      const user = userEvent.setup();
      rendre(<ActivitiesPage />);
      expect(await screen.findByText('Saisie des dépenses')).toBeInTheDocument();

      vi.mocked(activityService.listAll).mockResolvedValue([{ ...lActivite, title: 'Saisie relue' }]);
      await user.click(screen.getByRole('button', { name: 'Actualiser' }));

      expect(await screen.findByText('Saisie relue')).toBeInTheDocument();
      expect(screen.queryByText('Autre activité')).not.toBeInTheDocument();
      expect(projectService.listAll).toHaveBeenCalledTimes(2);
      expect(userService.listAll).toHaveBeenCalledTimes(2);
    });

    it('suppression en 404 : activité retirée de la liste, aucune relecture', async () => {
      vi.mocked(activityService.remove).mockRejectedValue(new ApiError('Activité introuvable.', 404, 'NOT_FOUND'));
      const user = userEvent.setup();
      rendre(<ActivitiesPage />);

      await supprimerDepuisLaCarte(user);

      expect(await screen.findByText('Activité introuvable.')).toBeInTheDocument();
      expect(screen.queryByText('Saisie des dépenses')).not.toBeInTheDocument();
      expect(screen.getByText('Autre activité')).toBeInTheDocument();
      expect(activityService.getById).not.toHaveBeenCalled();
    });

    it('suppression en 409 (projet clôturé) : message conservé, activité et projet relus, « Voir » seulement', async () => {
      vi.mocked(activityService.remove).mockRejectedValue(
        new ApiError('Ce projet est terminé : ses activités sont en lecture seule.', 409, 'CONFLICT')
      );
      vi.mocked(projectService.getById).mockResolvedValue({ ...leProjet, status: 'completed' });
      const user = userEvent.setup();
      rendre(<ActivitiesPage />);

      await supprimerDepuisLaCarte(user);

      expect(await screen.findByText('Ce projet est terminé : ses activités sont en lecture seule.')).toBeInTheDocument();
      await waitFor(() => expect(projectService.getById).toHaveBeenCalledWith('p1'));
      expect(activityService.getById).toHaveBeenCalledTimes(1);
      const carte = screen.getByText('Saisie des dépenses').closest('div.group') as HTMLElement;
      await user.click(within(carte).getByLabelText("Actions de l'activité"));
      await waitFor(() => expect(screen.getAllByRole('menuitem').map((b) => b.textContent)).toEqual(['Voir']));
    });
  });
});
