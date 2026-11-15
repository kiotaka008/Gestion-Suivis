import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProjectsPage } from '../pages/ProjectsPage';
import { UsersPage } from '../pages/UsersPage';
import { projectService } from '../services/projectService';
import { userService } from '../services/userService';
import { ApiError } from '../services/ApiError';
import { connecter, focusInitial, projet, rendre, utilisateur } from './aide';

vi.mock('../hooks/useAuth', async () => {
  const aide = await import('./aide');
  return { useAuth: () => aide.authEtat };
});
vi.mock('../services/projectService', () => ({
  projectService: { listAll: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}));
vi.mock('../services/userService', () => ({
  userService: {
    listAll: vi.fn(), create: vi.fn(), update: vi.fn(), activate: vi.fn(), deactivate: vi.fn(),
    remove: vi.fn(), history: vi.fn(),
  },
}));

const monProjet = projet('p1', 'Budget 2026', 'moi');
const projetDAutrui = projet('p2', 'Refonte du site', 'autre');

beforeEach(() => {
  vi.mocked(projectService.listAll).mockReset().mockResolvedValue([monProjet, projetDAutrui]);
  vi.mocked(projectService.update).mockReset();
  vi.mocked(userService.listAll).mockReset();
  vi.mocked(userService.update).mockReset();
});

async function vueCartes() {
  const user = userEvent.setup();
  await user.click(await screen.findByLabelText('Vue cartes'));
  return user;
}

function menuDe(nomCarte: string) {
  const carte = screen.getByText(nomCarte).closest('div.group') as HTMLElement;
  return within(carte).queryByLabelText('Actions du projet');
}

describe('ProjectsPage', () => {
  it('membre : pas de bouton « Nouveau projet », projets visibles', async () => {
    connecter('member');
    rendre(<ProjectsPage />);
    expect(await screen.findByText('Budget 2026')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Nouveau projet' })).not.toBeInTheDocument();
  });

  it('responsable : modifie ses projets seulement, ne supprime rien', async () => {
    connecter('manager');
    rendre(<ProjectsPage />);
    expect(await screen.findByRole('button', { name: 'Nouveau projet' })).toBeInTheDocument();
    const user = await vueCartes();

    await user.click(menuDe('Budget 2026')!);
    const actions = screen.getAllByRole('menuitem').map((b) => b.textContent);
    expect(actions).toEqual(['Voir', 'Modifier']);
    await user.keyboard('{Escape}');

    await user.click(menuDe('Refonte du site')!);
    expect(screen.getAllByRole('menuitem').map((b) => b.textContent)).toEqual(['Voir']);
  });

  it('modification depuis une carte : PATCH puis liste mise à jour', async () => {
    connecter('manager');
    vi.mocked(projectService.update).mockResolvedValue({ ...monProjet, name: 'Budget 2027' });
    rendre(<ProjectsPage />);
    const user = await vueCartes();

    await user.click(menuDe('Budget 2026')!);
    await user.click(screen.getByRole('menuitem', { name: 'Modifier' }));
    await focusInitial();
    const nom = screen.getByLabelText('Nom du projet');
    await user.clear(nom);
    await user.type(nom, 'Budget 2027');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(projectService.update).toHaveBeenCalledWith('p1', expect.objectContaining({ name: 'Budget 2027' }));
    expect(await screen.findByText('Budget 2027')).toBeInTheDocument();
    expect(screen.queryByText('Budget 2026')).not.toBeInTheDocument();
  });

  it('administrateur : peut supprimer', async () => {
    connecter('admin');
    rendre(<ProjectsPage />);
    const user = await vueCartes();
    await user.click(menuDe('Refonte du site')!);
    expect(screen.getAllByRole('menuitem').map((b) => b.textContent)).toEqual(['Voir', 'Modifier', 'Supprimer']);
  });

  it('recherche reçue de l’en-tête (?search=) appliquée à l’ouverture', async () => {
    connecter('member');
    rendre(<ProjectsPage />, { route: '/projects?search=refonte' });
    expect(await screen.findByText('Refonte du site')).toBeInTheDocument();
    expect(screen.queryByText('Budget 2026')).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText('Rechercher un projet...')).toHaveValue('refonte');
  });

  it('erreur de chargement : message du backend affiché', async () => {
    connecter('member');
    vi.mocked(projectService.listAll).mockRejectedValue(new ApiError('Service indisponible.', 503, 'SERVER_ERROR'));
    rendre(<ProjectsPage />);
    expect(await screen.findByText('Service indisponible.')).toBeInTheDocument();
  });
});

describe('UsersPage', () => {
  const moi = utilisateur('moi', 'Admin Moi', 'admin');
  const employe = utilisateur('e1', 'Employé Un');

  it('membre : accès réservé, aucune requête', async () => {
    connecter('member');
    rendre(<UsersPage />);
    expect(screen.getByText('Accès réservé')).toBeInTheDocument();
    expect(userService.listAll).not.toHaveBeenCalled();
  });

  it('responsable : liste visible, sans aucune action d’administration', async () => {
    connecter('manager');
    vi.mocked(userService.listAll).mockResolvedValue([employe]);
    rendre(<UsersPage />);
    expect(await screen.findByText('Employé Un')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Nouvel utilisateur' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Actions pour/)).not.toBeInTheDocument();
  });

  it('administrateur : pas de désactivation ni suppression de son propre compte', async () => {
    connecter('admin');
    vi.mocked(userService.listAll).mockResolvedValue([moi, employe]);
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await user.click(await screen.findByLabelText('Actions pour Admin Moi'));
    expect(screen.getAllByRole('menuitem').map((b) => b.textContent)).toEqual(['Modifier', 'Historique']);
    await user.keyboard('{Escape}');

    await user.click(screen.getByLabelText('Actions pour Employé Un'));
    expect(screen.getAllByRole('menuitem').map((b) => b.textContent)).toEqual([
      'Modifier', 'Historique', 'Désactiver', 'Supprimer',
    ]);
  });

  it('administrateur : changement de rôle envoyé au backend et liste mise à jour', async () => {
    connecter('admin');
    vi.mocked(userService.listAll).mockResolvedValue([moi, employe]);
    vi.mocked(userService.update).mockResolvedValue({ ...employe, role: 'manager' });
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await user.click(await screen.findByLabelText('Actions pour Employé Un'));
    await user.click(screen.getByRole('menuitem', { name: 'Modifier' }));
    await focusInitial();
    await user.selectOptions(screen.getByLabelText('Rôle'), 'manager');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(userService.update).toHaveBeenCalledWith('e1', { name: 'Employé Un', email: 'e1@exemple.com', role: 'manager' });
    // Badge de rôle de la ligne de l'utilisateur (le filtre des rôles contient aussi « Responsable »).
    const ligne = screen.getByText('Employé Un').closest('tr') as HTMLElement;
    await waitFor(() => expect(within(ligne).getByText('Responsable')).toBeInTheDocument());
  });
});
