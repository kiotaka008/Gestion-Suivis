import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useState } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileSection } from '../components/settings/ProfileSection';
import { AuthContext, type AuthContextValue } from '../contexts/authContextValue';
import { userService } from '../services/userService';
import { ApiError } from '../services/ApiError';
import type { User } from '../types/models';

/**
 * AUD-002, F8 : le profil n'envoie que les champs modifiés, et une nouvelle version de
 * l'utilisateur connecté (après un changement de photo, par exemple) n'écrase pas une saisie.
 */
vi.mock('../services/userService', () => ({
  userService: { updateMe: vi.fn() },
}));

const moi: User = {
  id: 'moi',
  name: 'Jeanne Martin',
  email: 'jeanne@exemple.com',
  role: 'member',
  isActive: true,
  createdAt: '2026-01-01T00:00:00Z',
};

/**
 * Contexte d'authentification réel : `setUser` (appelé par ProfileSection après un succès)
 * redonne une nouvelle version de l'utilisateur, comme dans l'application. Le bouton
 * « Recevoir » simule l'arrivée d'une version venue du serveur (`suivant`).
 */
function Banc({ initial, suivant }: { initial: User | null; suivant?: User }) {
  const [user, setUser] = useState<User | null>(initial);
  const valeur: AuthContextValue = {
    user,
    isLoading: false,
    isAuthenticated: !!user,
    login: async () => undefined,
    loginWithGoogle: async () => undefined,
    setUser: (u: User) => setUser(u),
    logout: async () => undefined,
    refresh: async () => undefined,
  };
  return (
    <AuthContext.Provider value={valeur}>
      {suivant && <button onClick={() => setUser(suivant)}>Recevoir</button>}
      <ProfileSection />
    </AuthContext.Provider>
  );
}

const nom = () => screen.getByLabelText('Nom complet');
const email = () => screen.getByLabelText('Email');
const enregistrer = () => screen.getByRole('button', { name: 'Enregistrer' });

beforeEach(() => {
  vi.mocked(userService.updateMe).mockReset();
});

describe('ProfileSection : informations personnelles (AUD-002, F8)', () => {
  it('formulaire initialisé avec l’utilisateur connecté', () => {
    render(<Banc initial={moi} />);
    expect(nom()).toHaveValue('Jeanne Martin');
    expect(email()).toHaveValue('jeanne@exemple.com');
  });

  it('première réception de l’utilisateur : formulaire initialisé, aucune requête sans modification', async () => {
    const user = userEvent.setup();
    render(<Banc initial={null} suivant={moi} />);
    expect(nom()).toHaveValue('');

    await user.click(screen.getByRole('button', { name: 'Recevoir' }));
    expect(nom()).toHaveValue('Jeanne Martin');
    expect(email()).toHaveValue('jeanne@exemple.com');

    await user.click(enregistrer());
    expect(userService.updateMe).not.toHaveBeenCalled();
  });

  it('nom seul : seul name est envoyé', async () => {
    vi.mocked(userService.updateMe).mockResolvedValue({ ...moi, name: 'Jeanne Durand' });
    const user = userEvent.setup();
    render(<Banc initial={moi} />);

    await user.clear(nom());
    await user.type(nom(), '  Jeanne Durand ');
    await user.click(enregistrer());

    expect(userService.updateMe).toHaveBeenCalledWith({ name: 'Jeanne Durand' });
    expect(await screen.findByText('Vos informations ont été mises à jour.')).toBeInTheDocument();
  });

  it('email seul : seul email est envoyé, normalisé (trim et minuscules)', async () => {
    vi.mocked(userService.updateMe).mockResolvedValue({ ...moi, email: 'nouvelle@exemple.com' });
    const user = userEvent.setup();
    render(<Banc initial={moi} />);

    await user.clear(email());
    await user.type(email(), ' Nouvelle@Exemple.COM ');
    await user.click(enregistrer());

    expect(userService.updateMe).toHaveBeenCalledWith({ email: 'nouvelle@exemple.com' });
  });

  it('email identique après normalisation : aucune requête, aucun message', async () => {
    const user = userEvent.setup();
    render(<Banc initial={moi} />);

    await user.clear(email());
    await user.type(email(), ' JEANNE@Exemple.com ');
    await user.click(enregistrer());

    expect(userService.updateMe).not.toHaveBeenCalled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByText('Vos informations ont été mises à jour.')).not.toBeInTheDocument();
  });

  it('aucun changement : aucune requête, aucun message', async () => {
    const user = userEvent.setup();
    render(<Banc initial={moi} />);
    await user.click(enregistrer());
    expect(userService.updateMe).not.toHaveBeenCalled();
    expect(screen.queryByText('Vos informations ont été mises à jour.')).not.toBeInTheDocument();
  });

  it('après un enregistrement réussi, un nouvel envoi sans changement ne fait aucune requête', async () => {
    vi.mocked(userService.updateMe).mockResolvedValue({ ...moi, name: 'Jeanne Durand' });
    const user = userEvent.setup();
    render(<Banc initial={moi} />);

    await user.clear(nom());
    await user.type(nom(), 'Jeanne Durand');
    await user.click(enregistrer());
    await screen.findByText('Vos informations ont été mises à jour.');

    await user.click(enregistrer());
    expect(userService.updateMe).toHaveBeenCalledTimes(1);
    expect(nom()).toHaveValue('Jeanne Durand');
  });

  it('409 (email déjà utilisé) : message affiché, saisie conservée', async () => {
    vi.mocked(userService.updateMe).mockRejectedValue(new ApiError('Cet email est déjà utilisé.', 409, 'CONFLICT'));
    const user = userEvent.setup();
    render(<Banc initial={moi} />);

    await user.clear(nom());
    await user.type(nom(), 'Jeanne Durand');
    await user.clear(email());
    await user.type(email(), 'pris@exemple.com');
    await user.click(enregistrer());

    expect(userService.updateMe).toHaveBeenCalledWith({ name: 'Jeanne Durand', email: 'pris@exemple.com' });
    expect(await screen.findByText('Cet email est déjà utilisé.')).toBeInTheDocument();
    expect(nom()).toHaveValue('Jeanne Durand');
    expect(email()).toHaveValue('pris@exemple.com');
  });

  it('422 (nom vide) : nom vidé toujours envoyé, message du backend, saisie conservée', async () => {
    vi.mocked(userService.updateMe).mockRejectedValue(new ApiError('Le nom ne peut pas être vide.', 422, 'VALIDATION_ERROR'));
    const user = userEvent.setup();
    render(<Banc initial={moi} />);

    await user.clear(nom());
    await user.type(nom(), '   ');
    await user.click(enregistrer());

    expect(userService.updateMe).toHaveBeenCalledWith({ name: '' });
    expect(await screen.findByText('Le nom ne peut pas être vide.')).toBeInTheDocument();
    expect(nom()).toHaveValue('   ');
    expect(email()).toHaveValue('jeanne@exemple.com');
  });

  it('nouvelle version reçue : champs en cours de saisie conservés, champs non modifiés mis à jour', async () => {
    const user = userEvent.setup();
    const versionServeur = { ...moi, name: 'Nom venu du serveur', email: 'serveur@exemple.com' };
    vi.mocked(userService.updateMe).mockResolvedValue({ ...versionServeur, name: 'Ma saisie' });
    render(<Banc initial={moi} suivant={versionServeur} />);

    await user.clear(nom());
    await user.type(nom(), 'Ma saisie');
    await user.click(screen.getByRole('button', { name: 'Recevoir' }));

    expect(nom()).toHaveValue('Ma saisie');
    expect(email()).toHaveValue('serveur@exemple.com');

    // La référence a suivi : seul le nom, réellement modifié, est envoyé.
    await user.click(enregistrer());
    expect(userService.updateMe).toHaveBeenCalledWith({ name: 'Ma saisie' });
  });
});

describe('ProfileSection : photo de profil pendant une saisie (AUD-002, F8)', () => {
  const avecPhoto: User = { ...moi, avatarUrl: 'data:image/jpeg;base64,ANCIENNE' };

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('suppression de la photo : requête inchangée, nom et email saisis non écrasés', async () => {
    vi.mocked(userService.updateMe).mockResolvedValue({ ...avecPhoto, avatarUrl: undefined });
    const user = userEvent.setup();
    render(<Banc initial={avecPhoto} />);

    await user.clear(nom());
    await user.type(nom(), 'Nom en cours');
    await user.clear(email());
    await user.type(email(), 'encours@exemple.com');
    await user.click(screen.getByRole('button', { name: 'Supprimer' }));

    expect(userService.updateMe).toHaveBeenCalledWith({ avatarUrl: '' });
    expect(await screen.findByText('Votre photo de profil a été supprimée.')).toBeInTheDocument();
    expect(screen.queryByAltText('Photo de profil')).not.toBeInTheDocument();
    expect(nom()).toHaveValue('Nom en cours');
    expect(email()).toHaveValue('encours@exemple.com');

    await user.click(enregistrer());
    expect(userService.updateMe).toHaveBeenLastCalledWith({ name: 'Nom en cours', email: 'encours@exemple.com' });
  });

  it('changement de photo : seule avatarUrl est envoyée, nom et email saisis non écrasés', async () => {
    // jsdom ne décode pas les images : chargement et canvas simulés.
    class ImageFactice {
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      width = 200;
      height = 100;
      set src(_valeur: string) {
        setTimeout(() => this.onload?.(), 0);
      }
    }
    vi.stubGlobal('Image', ImageFactice);
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: vi.fn() } as never);
    vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockReturnValue('data:image/jpeg;base64,NOUVELLE');
    vi.mocked(userService.updateMe).mockResolvedValue({ ...moi, avatarUrl: 'data:image/jpeg;base64,NOUVELLE' });
    const user = userEvent.setup();
    const { container } = render(<Banc initial={moi} />);

    await user.clear(nom());
    await user.type(nom(), 'Nom en cours');
    const fichier = new File(['image'], 'photo.png', { type: 'image/png' });
    await user.upload(container.querySelector('input[type="file"]') as HTMLInputElement, fichier);

    await waitFor(() => expect(userService.updateMe).toHaveBeenCalledWith({ avatarUrl: 'data:image/jpeg;base64,NOUVELLE' }));
    expect(await screen.findByText('Votre photo de profil a été mise à jour.')).toBeInTheDocument();
    expect(screen.getByAltText('Photo de profil')).toHaveAttribute('src', 'data:image/jpeg;base64,NOUVELLE');
    expect(nom()).toHaveValue('Nom en cours');
    expect(email()).toHaveValue('jeanne@exemple.com');
  });

  it('fichier qui n’est pas une image : message inchangé, aucune requête', async () => {
    // Fichier choisi malgré le filtre accept="image/*" du sélecteur (« Tous les fichiers »).
    const user = userEvent.setup({ applyAccept: false });
    const { container } = render(<Banc initial={moi} />);
    const fichier = new File(['texte'], 'notes.txt', { type: 'text/plain' });
    await user.upload(container.querySelector('input[type="file"]') as HTMLInputElement, fichier);

    expect(await screen.findByText('Veuillez sélectionner une image.')).toBeInTheDocument();
    expect(userService.updateMe).not.toHaveBeenCalled();
  });
});
