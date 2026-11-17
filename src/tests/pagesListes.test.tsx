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
  projectService: { listAll: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), getById: vi.fn() },
}));
vi.mock('../services/userService', () => ({
  userService: {
    listAll: vi.fn(), create: vi.fn(), update: vi.fn(), activate: vi.fn(), deactivate: vi.fn(),
    remove: vi.fn(), history: vi.fn(), getById: vi.fn(),
  },
}));

const monProjet = projet('p1', 'Budget 2026', 'moi');
const projetDAutrui = projet('p2', 'Refonte du site', 'autre');

beforeEach(() => {
  vi.mocked(projectService.listAll).mockReset().mockResolvedValue([monProjet, projetDAutrui]);
  vi.mocked(projectService.update).mockReset();
  vi.mocked(userService.listAll).mockReset();
  vi.mocked(userService.update).mockReset();
  vi.mocked(projectService.getById).mockReset();
  vi.mocked(userService.getById).mockReset();
  vi.mocked(userService.activate).mockReset();
  vi.mocked(userService.deactivate).mockReset();
  vi.mocked(userService.remove).mockReset();
  vi.mocked(projectService.remove).mockReset();
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

    // AUD-002, F2 : seul le champ modifié est envoyé.
    expect(userService.update).toHaveBeenCalledWith('e1', { role: 'manager' });
    // Badge de rôle de la ligne de l'utilisateur (le filtre des rôles contient aussi « Responsable »).
    const ligne = screen.getByText('Employé Un').closest('tr') as HTMLElement;
    await waitFor(() => expect(within(ligne).getByText('Responsable')).toBeInTheDocument());
  });

  // AUD-002, F6 : conflit 409, l'utilisateur est relu et le formulaire garde la saisie.
  it('conflit 409 : utilisateur relu, liste mise à jour, saisie conservée dans le formulaire', async () => {
    connecter('admin');
    vi.mocked(userService.listAll).mockResolvedValue([moi, employe]);
    vi.mocked(userService.update).mockRejectedValue(new ApiError('Conflit sur ce compte.', 409, 'CONFLICT'));
    vi.mocked(userService.getById).mockResolvedValue({ ...employe, name: 'Employé Renommé' });
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await user.click(await screen.findByLabelText('Actions pour Employé Un'));
    await user.click(screen.getByRole('menuitem', { name: 'Modifier' }));
    await focusInitial();
    await user.selectOptions(screen.getByLabelText('Rôle'), 'manager');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Conflit sur ce compte.')).toBeInTheDocument();
    await waitFor(() => expect(userService.getById).toHaveBeenCalledWith('e1'));
    expect(await screen.findByRole('button', { name: 'Recharger les valeurs actuelles' })).toBeInTheDocument();
    expect(screen.getByLabelText('Rôle')).toHaveValue('manager');
    expect(screen.getByLabelText('Nom complet')).toHaveValue('Employé Un');
  });
});

describe('ProjectsPage : conflit 409 (AUD-002, F6)', () => {
  it('projet relu, saisie conservée', async () => {
    connecter('manager');
    vi.mocked(projectService.update).mockRejectedValue(new ApiError('Modifié entre-temps.', 409, 'CONFLICT'));
    vi.mocked(projectService.getById).mockResolvedValue({ ...monProjet, name: 'Budget relu' });
    rendre(<ProjectsPage />);
    const user = await vueCartes();

    await user.click(menuDe('Budget 2026')!);
    await user.click(screen.getByRole('menuitem', { name: 'Modifier' }));
    await focusInitial();
    const nom = screen.getByLabelText('Nom du projet');
    await user.clear(nom);
    await user.type(nom, 'Ma saisie');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Modifié entre-temps.')).toBeInTheDocument();
    await waitFor(() => expect(projectService.getById).toHaveBeenCalledWith('p1'));
    expect(screen.getByLabelText('Nom du projet')).toHaveValue('Ma saisie');
  });
});

// AUD-002, F7 : activation et désactivation sans double requête ni inversion locale.
describe('UsersPage : activation et désactivation (AUD-002, F7)', () => {
  const moi = utilisateur('moi', 'Admin Moi', 'admin');
  const employe = utilisateur('e1', 'Employé Un');
  const inactif = { ...utilisateur('e2', 'Employé Deux'), isActive: false };

  function ligneDe(nom: string) {
    return screen.getByText(nom).closest('tr') as HTMLElement;
  }

  async function ouvrirMenu(user: ReturnType<typeof userEvent.setup>, nom: string) {
    await user.click(await screen.findByLabelText(`Actions pour ${nom}`));
  }

  function differe<T>() {
    let resoudre!: (v: T) => void;
    const promesse = new Promise<T>((r) => {
      resoudre = r;
    });
    return { promesse, resoudre };
  }

  beforeEach(() => {
    connecter('admin');
    vi.mocked(userService.listAll).mockResolvedValue([moi, employe, inactif]);
  });

  it('double clic (tableau) : une seule requête, entrée désactivée pendant la requête, état relu', async () => {
    const attente = differe<void>();
    vi.mocked(userService.deactivate).mockReturnValue(attente.promesse);
    vi.mocked(userService.getById).mockResolvedValue({ ...employe, isActive: false });
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));
    await ouvrirMenu(user, 'Employé Un');
    const enCours = screen.getByRole('menuitem', { name: 'Désactivation…' });
    expect(enCours).toBeDisabled();
    await user.click(enCours);
    expect(userService.deactivate).toHaveBeenCalledTimes(1);

    attente.resoudre();
    await waitFor(() => expect(within(ligneDe('Employé Un')).getByText('Inactif')).toBeInTheDocument());
    expect(userService.getById).toHaveBeenCalledWith('e1');
    expect(userService.deactivate).toHaveBeenCalledTimes(1);
    expect(userService.activate).not.toHaveBeenCalled();
  });

  it('double clic (cartes) : une seule requête', async () => {
    const attente = differe<void>();
    vi.mocked(userService.deactivate).mockReturnValue(attente.promesse);
    vi.mocked(userService.getById).mockResolvedValue({ ...employe, isActive: false });
    const user = userEvent.setup();
    rendre(<UsersPage />);
    await user.click(await screen.findByLabelText('Vue cartes'));

    const carte = () => screen.getByText('Employé Un').closest('div.group') as HTMLElement;
    await user.click(within(carte()).getByLabelText("Actions de l'utilisateur"));
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));
    await user.click(within(carte()).getByLabelText("Actions de l'utilisateur"));
    const enCours = screen.getByRole('menuitem', { name: 'Désactivation…' });
    expect(enCours).toBeDisabled();
    await user.click(enCours);
    expect(userService.deactivate).toHaveBeenCalledTimes(1);

    attente.resoudre();
    await waitFor(() => expect(within(carte()).getByText('Inactif')).toBeInTheDocument());
  });

  it('activation : route activate, état relu du serveur', async () => {
    vi.mocked(userService.activate).mockResolvedValue();
    vi.mocked(userService.getById).mockResolvedValue({ ...inactif, isActive: true });
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Deux');
    await user.click(screen.getByRole('menuitem', { name: 'Activer' }));

    await waitFor(() => expect(within(ligneDe('Employé Deux')).getByText('Actif')).toBeInTheDocument());
    expect(userService.activate).toHaveBeenCalledWith('e2');
    expect(userService.deactivate).not.toHaveBeenCalled();
  });

  it("état affiché = état relu, même s'il diffère de la valeur demandée (autre administrateur)", async () => {
    vi.mocked(userService.deactivate).mockResolvedValue();
    // Un autre administrateur a réactivé et renommé le compte entre-temps.
    vi.mocked(userService.getById).mockResolvedValue({ ...employe, name: 'Employé Renommé', isActive: true });
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));

    expect(await screen.findByText('Employé Renommé')).toBeInTheDocument();
    expect(within(ligneDe('Employé Renommé')).getByText('Actif')).toBeInTheDocument();
  });

  it('relecture impossible après le 204 : la valeur demandée est affichée', async () => {
    vi.mocked(userService.deactivate).mockResolvedValue();
    vi.mocked(userService.getById).mockRejectedValue(new ApiError('Service indisponible.', 503, 'SERVER_ERROR'));
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));

    await waitFor(() => expect(within(ligneDe('Employé Un')).getByText('Inactif')).toBeInTheDocument());
    expect(screen.queryByText('Service indisponible.')).not.toBeInTheDocument();
  });

  it('409 : message du backend conservé et utilisateur relu', async () => {
    vi.mocked(userService.deactivate).mockRejectedValue(
      new ApiError('Impossible de désactiver le dernier administrateur actif.', 409, 'CONFLICT')
    );
    vi.mocked(userService.getById).mockResolvedValue({ ...employe, role: 'admin' });
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));

    expect(await screen.findByText('Impossible de désactiver le dernier administrateur actif.')).toBeInTheDocument();
    await waitFor(() => expect(within(ligneDe('Employé Un')).getByText('Administrateur')).toBeInTheDocument());
    expect(userService.getById).toHaveBeenCalledTimes(1);
    expect(within(ligneDe('Employé Un')).getByText('Actif')).toBeInTheDocument();
    expect(screen.getByText('Impossible de désactiver le dernier administrateur actif.')).toBeInTheDocument();
  });

  it('409 puis relecture impossible : message initial conservé, ligne inchangée', async () => {
    vi.mocked(userService.deactivate).mockRejectedValue(new ApiError('Conflit.', 409, 'CONFLICT'));
    vi.mocked(userService.getById).mockRejectedValue(new ApiError('Erreur.', 500, 'SERVER_ERROR'));
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));

    expect(await screen.findByText('Conflit.')).toBeInTheDocument();
    await waitFor(() => expect(userService.getById).toHaveBeenCalled());
    expect(within(ligneDe('Employé Un')).getByText('Actif')).toBeInTheDocument();
    expect(screen.getByText('Conflit.')).toBeInTheDocument();
  });

  it('404 : utilisateur retiré de la liste, sans relecture', async () => {
    vi.mocked(userService.deactivate).mockRejectedValue(new ApiError('Utilisateur introuvable.', 404, 'NOT_FOUND'));
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));

    expect(await screen.findByText('Utilisateur introuvable.')).toBeInTheDocument();
    expect(screen.queryByText('Employé Un')).not.toBeInTheDocument();
    expect(userService.getById).not.toHaveBeenCalled();
  });

  it('403 : message affiché, aucune relecture, ligne inchangée', async () => {
    vi.mocked(userService.deactivate).mockRejectedValue(
      new ApiError('Action réservée aux administrateurs.', 403, 'FORBIDDEN')
    );
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));

    expect(await screen.findByText('Action réservée aux administrateurs.')).toBeInTheDocument();
    expect(userService.getById).not.toHaveBeenCalled();
    expect(within(ligneDe('Employé Un')).getByText('Actif')).toBeInTheDocument();
  });

  it('erreur réseau puis relecture réussie : état réel affiché, message conservé', async () => {
    vi.mocked(userService.deactivate).mockRejectedValue(
      new ApiError('Connexion au serveur impossible.', 0, 'NETWORK_ERROR')
    );
    // La requête était passée avant la coupure : le serveur a bien désactivé le compte.
    vi.mocked(userService.getById).mockResolvedValue({ ...employe, isActive: false });
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));

    await waitFor(() => expect(within(ligneDe('Employé Un')).getByText('Inactif')).toBeInTheDocument());
    expect(screen.getByText('Connexion au serveur impossible.')).toBeInTheDocument();
    expect(screen.queryByText(/État du compte incertain/)).not.toBeInTheDocument();
  });

  it('erreur réseau puis relecture impossible : état incertain signalé, ligne inchangée', async () => {
    vi.mocked(userService.deactivate).mockRejectedValue(
      new ApiError('Connexion au serveur impossible.', 0, 'NETWORK_ERROR')
    );
    vi.mocked(userService.getById).mockRejectedValue(
      new ApiError('Connexion au serveur impossible.', 0, 'NETWORK_ERROR')
    );
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));

    expect(
      await screen.findByText('Connexion au serveur impossible. État du compte incertain : actualisez la page.')
    ).toBeInTheDocument();
    expect(within(ligneDe('Employé Un')).getByText('Actif')).toBeInTheDocument();
  });

  it("fin de requête : l'entrée redevient disponible avec le libellé à jour", async () => {
    vi.mocked(userService.deactivate).mockResolvedValue();
    vi.mocked(userService.getById).mockResolvedValue({ ...employe, isActive: false });
    const user = userEvent.setup();
    rendre(<UsersPage />);

    await ouvrirMenu(user, 'Employé Un');
    await user.click(screen.getByRole('menuitem', { name: 'Désactiver' }));
    await waitFor(() => expect(within(ligneDe('Employé Un')).getByText('Inactif')).toBeInTheDocument());

    await ouvrirMenu(user, 'Employé Un');
    expect(screen.getByRole('menuitem', { name: 'Activer' })).toBeEnabled();
  });
});

// AUD-002, F9 : relectures ciblées après 404/409 et actualisation manuelle (listes).
describe('Listes : relectures après erreur et actualisation (AUD-002, F9)', () => {
  const moi = utilisateur('moi', 'Admin Moi', 'admin');
  const employe = utilisateur('e1', 'Employé Un');
  const nouveau = utilisateur('e3', 'Employé Trois');

  function differe<T>() {
    let resoudre!: (v: T) => void;
    const promesse = new Promise<T>((r) => {
      resoudre = r;
    });
    return { promesse, resoudre };
  }

  async function supprimerUtilisateur(user: ReturnType<typeof userEvent.setup>, nom: string) {
    await user.click(await screen.findByLabelText(`Actions pour ${nom}`));
    await user.click(screen.getByRole('menuitem', { name: 'Supprimer' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Supprimer' }));
  }

  describe('UsersPage', () => {
    beforeEach(() => {
      connecter('admin');
    });

    it('« Actualiser » relit la liste (comptes modifiés et ajoutés)', async () => {
      vi.mocked(userService.listAll)
        .mockResolvedValueOnce([moi, employe])
        .mockResolvedValueOnce([moi, { ...employe, name: 'Employé Renommé' }, nouveau]);
      const user = userEvent.setup();
      rendre(<UsersPage />);
      expect(await screen.findByText('Employé Un')).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'Actualiser' }));

      expect(await screen.findByText('Employé Renommé')).toBeInTheDocument();
      expect(screen.getByText('Employé Trois')).toBeInTheDocument();
      expect(screen.queryByText('Employé Un')).not.toBeInTheDocument();
      expect(userService.listAll).toHaveBeenCalledTimes(2);
    });

    it('« Actualiser » cliqué deux fois pendant la relecture : une seule requête', async () => {
      const attente = differe<typeof moi[]>();
      vi.mocked(userService.listAll).mockResolvedValueOnce([moi, employe]).mockReturnValueOnce(attente.promesse);
      const user = userEvent.setup();
      rendre(<UsersPage />);
      await screen.findByText('Employé Un');

      await user.click(screen.getByRole('button', { name: 'Actualiser' }));
      expect(screen.getByRole('button', { name: 'Actualiser' })).toBeDisabled();
      await user.click(screen.getByRole('button', { name: 'Actualiser' }));
      expect(userService.listAll).toHaveBeenCalledTimes(2);

      attente.resoudre([moi, employe]);
      await waitFor(() => expect(screen.getByRole('button', { name: 'Actualiser' })).toBeEnabled());
    });

    it('« Actualiser » en échec : message affiché, liste conservée', async () => {
      vi.mocked(userService.listAll)
        .mockResolvedValueOnce([moi, employe])
        .mockRejectedValueOnce(new ApiError('Service indisponible.', 503, 'SERVER_ERROR'));
      const user = userEvent.setup();
      rendre(<UsersPage />);
      await screen.findByText('Employé Un');

      await user.click(screen.getByRole('button', { name: 'Actualiser' }));

      expect(await screen.findByText('Service indisponible.')).toBeInTheDocument();
      expect(screen.getByText('Employé Un')).toBeInTheDocument();
    });

    it('« Actualiser » avec le formulaire ouvert : saisie et cible de modification inchangées', async () => {
      vi.mocked(userService.listAll)
        .mockResolvedValueOnce([moi, employe])
        .mockResolvedValueOnce([moi, { ...employe, role: 'manager', email: 'autre@exemple.com' }]);
      vi.mocked(userService.update).mockResolvedValue({ ...employe, name: 'Ma saisie' });
      const user = userEvent.setup();
      rendre(<UsersPage />);

      await user.click(await screen.findByLabelText('Actions pour Employé Un'));
      await user.click(screen.getByRole('menuitem', { name: 'Modifier' }));
      await focusInitial();
      const nom = screen.getByLabelText('Nom complet');
      await user.clear(nom);
      await user.type(nom, 'Ma saisie');

      await user.click(screen.getByRole('button', { name: 'Actualiser' }));
      await waitFor(() => expect(userService.listAll).toHaveBeenCalledTimes(2));

      // Le formulaire n'est pas resynchronisé : saisie et valeurs d'ouverture conservées.
      expect(screen.getByLabelText('Nom complet')).toHaveValue('Ma saisie');
      expect(screen.getByLabelText('Email')).toHaveValue('e1@exemple.com');
      expect(screen.getByLabelText('Rôle')).toHaveValue('member');
      await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
      expect(userService.update).toHaveBeenCalledWith('e1', { name: 'Ma saisie' });
    });

    it('suppression en 404 : compte retiré de la liste, message affiché, aucune relecture', async () => {
      vi.mocked(userService.listAll).mockResolvedValue([moi, employe]);
      vi.mocked(userService.remove).mockRejectedValue(new ApiError('Utilisateur introuvable.', 404, 'NOT_FOUND'));
      const user = userEvent.setup();
      rendre(<UsersPage />);

      await supprimerUtilisateur(user, 'Employé Un');

      expect(await screen.findByText('Utilisateur introuvable.')).toBeInTheDocument();
      expect(screen.queryByText('Employé Un')).not.toBeInTheDocument();
      expect(userService.getById).not.toHaveBeenCalled();
    });

    it('suppression en 409 : message conservé, compte relu une seule fois', async () => {
      vi.mocked(userService.listAll).mockResolvedValue([moi, employe]);
      vi.mocked(userService.remove).mockRejectedValue(
        new ApiError('Cet utilisateur est encore chef de projet.', 409, 'CONFLICT')
      );
      vi.mocked(userService.getById).mockResolvedValue({ ...employe, name: 'Employé Relu' });
      const user = userEvent.setup();
      rendre(<UsersPage />);

      await supprimerUtilisateur(user, 'Employé Un');

      expect(await screen.findByText('Employé Relu')).toBeInTheDocument();
      expect(screen.getByText('Cet utilisateur est encore chef de projet.')).toBeInTheDocument();
      expect(userService.getById).toHaveBeenCalledTimes(1);
    });
  });

  describe('ProjectsPage', () => {
    async function supprimerProjet(user: ReturnType<typeof userEvent.setup>, nom: string) {
      await user.click(menuDe(nom)!);
      await user.click(screen.getByRole('menuitem', { name: 'Supprimer' }));
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Supprimer' }));
    }

    it('« Actualiser » relit la liste des projets', async () => {
      connecter('member');
      vi.mocked(projectService.listAll)
        .mockResolvedValueOnce([monProjet])
        .mockResolvedValueOnce([{ ...monProjet, name: 'Budget relu' }, projetDAutrui]);
      const user = userEvent.setup();
      rendre(<ProjectsPage />);
      expect(await screen.findByText('Budget 2026')).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'Actualiser' }));

      expect(await screen.findByText('Budget relu')).toBeInTheDocument();
      expect(screen.getByText('Refonte du site')).toBeInTheDocument();
    });

    it('suppression en 404 : projet retiré de la liste, aucune relecture', async () => {
      connecter('admin');
      vi.mocked(projectService.remove).mockRejectedValue(new ApiError('Projet introuvable.', 404, 'NOT_FOUND'));
      rendre(<ProjectsPage />);
      const user = await vueCartes();

      await supprimerProjet(user, 'Refonte du site');

      expect(await screen.findByText('Projet introuvable.')).toBeInTheDocument();
      expect(screen.queryByText('Refonte du site')).not.toBeInTheDocument();
      expect(projectService.getById).not.toHaveBeenCalled();
    });

    it('suppression en 409 : message conservé, projet relu une seule fois', async () => {
      connecter('admin');
      vi.mocked(projectService.remove).mockRejectedValue(new ApiError('Suppression refusée.', 409, 'CONFLICT'));
      vi.mocked(projectService.getById).mockResolvedValue({ ...projetDAutrui, name: 'Refonte relue' });
      rendre(<ProjectsPage />);
      const user = await vueCartes();

      await supprimerProjet(user, 'Refonte du site');

      expect(await screen.findByText('Refonte relue')).toBeInTheDocument();
      expect(screen.getByText('Suppression refusée.')).toBeInTheDocument();
      expect(projectService.getById).toHaveBeenCalledTimes(1);
    });

    it('suppression en 409 puis projet disparu à la relecture : retiré de la liste', async () => {
      connecter('admin');
      vi.mocked(projectService.remove).mockRejectedValue(new ApiError('Suppression refusée.', 409, 'CONFLICT'));
      vi.mocked(projectService.getById).mockRejectedValue(new ApiError('Projet introuvable.', 404, 'NOT_FOUND'));
      rendre(<ProjectsPage />);
      const user = await vueCartes();

      await supprimerProjet(user, 'Refonte du site');

      expect(await screen.findByText('Suppression refusée.')).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByText('Refonte du site')).not.toBeInTheDocument());
    });
  });
});
