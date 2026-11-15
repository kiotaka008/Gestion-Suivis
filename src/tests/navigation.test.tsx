import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Sidebar } from '../components/layout/Sidebar';
import { GlobalSearch } from '../components/layout/GlobalSearch';
import { projectService } from '../services/projectService';
import { activityService } from '../services/activityService';
import { authEtat, connecter, page, projet, activite, rendre } from './aide';

vi.mock('../hooks/useAuth', async () => {
  const aide = await import('./aide');
  return { useAuth: () => aide.authEtat };
});
vi.mock('../services/projectService', () => ({ projectService: { list: vi.fn() } }));
vi.mock('../services/activityService', () => ({ activityService: { list: vi.fn() } }));

beforeEach(() => {
  vi.mocked(projectService.list).mockReset();
  vi.mocked(activityService.list).mockReset();
  authEtat.user = null;
});

describe('Sidebar : liens selon le rôle', () => {
  it('membre : pas de lien Utilisateurs', () => {
    connecter('member');
    rendre(<Sidebar isOpen onClose={vi.fn()} />);
    expect(screen.getByRole('link', { name: /Projets/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Utilisateurs/ })).not.toBeInTheDocument();
  });

  it('responsable et administrateur : lien Utilisateurs visible', () => {
    connecter('manager');
    const { unmount } = rendre(<Sidebar isOpen onClose={vi.fn()} />);
    expect(screen.getByRole('link', { name: /Utilisateurs/ })).toBeInTheDocument();
    unmount();
    connecter('admin');
    rendre(<Sidebar isOpen onClose={vi.fn()} />);
    expect(screen.getByRole('link', { name: /Utilisateurs/ })).toBeInTheDocument();
  });
});

describe('Recherche globale', () => {
  const champ = () => screen.getByRole('searchbox', { name: /Rechercher un projet ou une activité/ });

  it('moins de 2 caractères : aucune requête', async () => {
    const user = userEvent.setup();
    rendre(<GlobalSearch />);
    await user.type(champ(), 'a');
    await new Promise((r) => setTimeout(r, 400));
    expect(projectService.list).not.toHaveBeenCalled();
  });

  it('recherche dans les projets et activités via le backend, puis ouvre le résultat choisi', async () => {
    const user = userEvent.setup();
    vi.mocked(projectService.list).mockResolvedValue(page([projet('p1', 'Budget 2026', 'u1')]));
    vi.mocked(activityService.list).mockResolvedValue(
      page([activite('a1', 'Saisie du budget', 'p1', { projectName: 'Budget 2026' })])
    );
    rendre(<GlobalSearch />);

    await user.type(champ(), 'budget');

    expect(await screen.findByText('Saisie du budget')).toBeInTheDocument();
    expect(projectService.list).toHaveBeenCalledTimes(1);
    expect(projectService.list).toHaveBeenCalledWith({ search: 'budget', perPage: 5, page: 1 });
    expect(activityService.list).toHaveBeenCalledWith({ search: 'budget', perPage: 5, page: 1 });

    await user.click(screen.getByRole('option', { name: /Saisie du budget/ }));
    expect(screen.getByTestId('emplacement')).toHaveTextContent('/activities/a1');
    expect(champ()).toHaveValue('');
  });

  it('Entrée ouvre le premier résultat', async () => {
    const user = userEvent.setup();
    vi.mocked(projectService.list).mockResolvedValue(page([projet('p1', 'Budget 2026', 'u1')]));
    vi.mocked(activityService.list).mockResolvedValue(page([]));
    rendre(<GlobalSearch />);

    await user.type(champ(), 'budget');
    await screen.findByText('Budget 2026');
    await user.keyboard('{Enter}');

    expect(screen.getByTestId('emplacement')).toHaveTextContent('/projects/p1');
  });

  it('« Tous les projets » transmet la recherche à la page Projets', async () => {
    const user = userEvent.setup();
    vi.mocked(projectService.list).mockResolvedValue(page([projet('p1', 'Budget 2026', 'u1')]));
    vi.mocked(activityService.list).mockResolvedValue(page([]));
    rendre(<GlobalSearch />);

    await user.type(champ(), 'bud get');
    await user.click(await screen.findByRole('button', { name: 'Tous les projets' }));

    expect(screen.getByTestId('emplacement')).toHaveTextContent('/projects?search=bud%20get');
  });

  it('aucun résultat, puis erreur du backend', async () => {
    const user = userEvent.setup();
    vi.mocked(projectService.list).mockResolvedValue(page([]));
    vi.mocked(activityService.list).mockResolvedValue(page([]));
    rendre(<GlobalSearch />);
    await user.type(champ(), 'zz');
    expect(await screen.findByText('Aucun résultat pour « zz ».')).toBeInTheDocument();

    vi.mocked(projectService.list).mockRejectedValue(new Error('panne'));
    await user.type(champ(), 'z');
    await waitFor(() => expect(screen.getByText('Recherche impossible pour le moment.')).toBeInTheDocument());
  });
});
