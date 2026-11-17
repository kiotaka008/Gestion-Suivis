import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateActivityModal } from '../components/activities/CreateActivityModal';
import { CreateProjectModal } from '../components/projects/CreateProjectModal';
import { CreateUserModal } from '../components/users/CreateUserModal';
import type { Activity, Project, User } from '../types/models';
import { ApiError } from '../services/ApiError';

const projet: Project = {
  id: 'p1',
  name: 'Refonte',
  description: 'Ancienne description',
  status: 'active',
  priority: 'medium',
  progress: 40,
  ownerId: 'chef',
  memberIds: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const activite: Activity = {
  id: 'a1',
  projectId: 'p1',
  title: 'Maquettes',
  status: 'todo',
  priority: 'high',
  progress: 10,
  assigneeId: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

/**
 * La fenêtre modale place le focus sur son premier élément 50 ms après l'ouverture
 * (components/ui/Modal.tsx) : on attend ce déplacement avant de saisir, sinon une partie
 * du texte tapé part dans un autre champ.
 */
const focusInitial = () => new Promise((r) => setTimeout(r, 100));

function utilisateur(id: string, nom: string): User {
  return { id, name: nom, email: `${id}@exemple.com`, role: 'member', isActive: true, createdAt: '2026-01-01T00:00:00Z' };
}

describe('CreateActivityModal', () => {
  it('suivi seul : envoie uniquement le statut et la progression', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(
      <CreateActivityModal isOpen activity={activite} trackOnly projects={[]} onClose={vi.fn()} onCreate={onCreate} />
    );
    await focusInitial();
    expect(screen.queryByLabelText("Titre de l'activité")).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Statut'), 'in_progress');
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '60');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ status: 'in_progress', progress: 60 });
  });

  // AUD-002, F4 : suivi seul, uniquement le statut et/ou la progression réellement modifiés.
  it('F4 : progression seule modifiée → pas de statut', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(<CreateActivityModal isOpen activity={activite} trackOnly projects={[]} onClose={vi.fn()} onCreate={onCreate} />);
    await focusInitial();
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '55');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ progress: 55 });
  });

  it('F4 : statut seul modifié → pas de progression', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(<CreateActivityModal isOpen activity={activite} trackOnly projects={[]} onClose={vi.fn()} onCreate={onCreate} />);
    await focusInitial();
    await user.selectOptions(screen.getByLabelText('Statut'), 'blocked');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ status: 'blocked' });
  });

  it('F4 : formulaire ancien (statut changé entre-temps par le chef) → l’ancien statut n’est pas renvoyé', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    const { rerender } = render(
      <CreateActivityModal isOpen activity={activite} trackOnly projects={[]} onClose={vi.fn()} onCreate={onCreate} />
    );
    await focusInitial();
    rerender(
      <CreateActivityModal isOpen activity={{ ...activite, status: 'blocked' }} trackOnly projects={[]} onClose={vi.fn()}
        onCreate={onCreate} />
    );
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '30');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ progress: 30 });
  });

  it('F4 / D1 : aucune modification (progression retapée à l’identique) → fermé sans requête', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    const onClose = vi.fn();
    render(<CreateActivityModal isOpen activity={activite} trackOnly projects={[]} onClose={onClose} onCreate={onCreate} />);
    await focusInitial();
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '10');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('refuse une progression hors de 0..100 sans appeler le backend', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(
      <CreateActivityModal isOpen activity={activite} trackOnly projects={[]} onClose={vi.fn()} onCreate={onCreate} />
    );
    await focusInitial();
    const progression = screen.getByLabelText('Progression (%)');
    await user.clear(progression);
    await user.type(progression, '150');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).not.toHaveBeenCalled();
    expect(screen.getByText(/entre 0 et 100/)).toBeInTheDocument();
  });

  it("modification : n'envoie le responsable que s'il change", async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(
      <CreateActivityModal
        isOpen
        activity={activite}
        projects={[projet]}
        assignees={[utilisateur('u1', 'Alice'), utilisateur('u2', 'Bob')]}
        onClose={vi.fn()}
        onCreate={onCreate}
      />
    );
    await focusInitial();
    // D1 : aucune modification → aucune requête.
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).not.toHaveBeenCalled();

    await user.selectOptions(screen.getByLabelText('Responsable'), 'u2');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ assigneeId: 'u2' });
  });

  // AUD-002, F1 : modification complète, uniquement les champs réellement modifiés.
  it('F1 : titre modifié seul → ni statut, ni progression, ni dates', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(<CreateActivityModal isOpen activity={activite} projects={[projet]} onClose={vi.fn()} onCreate={onCreate} />);
    await focusInitial();
    const titre = screen.getByLabelText("Titre de l'activité");
    await user.clear(titre);
    await user.type(titre, 'Maquettes V2');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ title: 'Maquettes V2' });
  });

  it('F1 : formulaire ancien (progression saisie entre-temps par le responsable) → non écrasée', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    const { rerender } = render(
      <CreateActivityModal isOpen activity={activite} projects={[projet]} onClose={vi.fn()} onCreate={onCreate} />
    );
    await focusInitial();
    rerender(
      <CreateActivityModal isOpen activity={{ ...activite, progress: 80, status: 'in_progress' }} projects={[projet]}
        onClose={vi.fn()} onCreate={onCreate} />
    );
    await user.selectOptions(screen.getByLabelText('Priorité'), 'urgent');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ priority: 'urgent' });
  });

  it('F1 : description volontairement vidée → envoyée vide', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(
      <CreateActivityModal isOpen activity={{ ...activite, description: 'À supprimer' }} projects={[projet]}
        onClose={vi.fn()} onCreate={onCreate} />
    );
    await focusInitial();
    await user.clear(screen.getByLabelText('Description'));
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ description: '' });
  });

  it('F1 / D2 : date vidée → message explicite, aucune requête ; date changée → envoyée seule', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(
      <CreateActivityModal isOpen activity={{ ...activite, startDate: '2026-02-01' }} projects={[projet]}
        onClose={vi.fn()} onCreate={onCreate} />
    );
    await focusInitial();
    const debut = screen.getByLabelText('Date de début');
    await user.clear(debut);
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).not.toHaveBeenCalled();
    expect(screen.getByText("L'effacement d'une date n'est pas encore pris en charge.")).toBeInTheDocument();

    await user.type(debut, '2026-03-01');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ startDate: '2026-03-01' });
  });

  it('création inchangée : tous les champs de création envoyés', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(<CreateActivityModal isOpen projects={[projet]} onClose={vi.fn()} onCreate={onCreate} />);
    await focusInitial();
    await user.type(screen.getByLabelText("Titre de l'activité"), 'Nouvelle');
    await user.click(screen.getByRole('button', { name: "Créer l'activité" }));
    expect(onCreate).toHaveBeenCalledWith({
      title: 'Nouvelle', description: undefined, projectId: 'p1', priority: 'medium', startDate: undefined,
      dueDate: undefined, assigneeId: undefined, status: 'todo', progress: 0,
    });
  });

  it('création : ne propose que les projets en cours', async () => {
    render(
      <CreateActivityModal
        isOpen
        projects={[projet, { ...projet, id: 'p2', name: 'Archivé', status: 'archived' }]}
        onClose={vi.fn()}
        onCreate={vi.fn()}
      />
    );
    await focusInitial();
    const options = Array.from((screen.getByLabelText('Projet') as HTMLSelectElement).options).map((o) => o.text);
    expect(options).toEqual(['Refonte']);
  });
});

describe('CreateProjectModal', () => {
  it('modification : statut modifiable, chef envoyé seulement s’il change', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(
      <CreateProjectModal
        isOpen
        project={projet}
        owners={[utilisateur('chef', 'Chef'), utilisateur('u2', 'Bob')]}
        onClose={vi.fn()}
        onCreate={onCreate}
      />
    );
    await focusInitial();
    await user.selectOptions(screen.getByLabelText('Statut'), 'archived');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    // AUD-002 : seul le statut modifié est envoyé, pas le chef inchangé.
    expect(onCreate).toHaveBeenCalledWith({ status: 'archived' });

    await user.selectOptions(screen.getByLabelText('Chef de projet'), 'u2');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate.mock.calls[1][0].ownerId).toBe('u2');
  });

  // AUD-002, F3 : le statut n'est jamais envoyé s'il n'a pas été changé.
  it('F3 : nom modifié seul → ni statut ni autre champ', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(<CreateProjectModal isOpen project={projet} onClose={vi.fn()} onCreate={onCreate} />);
    await focusInitial();
    const nom = screen.getByLabelText('Nom du projet');
    await user.clear(nom);
    await user.type(nom, 'Refonte 2027');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ name: 'Refonte 2027' });
    expect(onCreate.mock.calls[0][0]).not.toHaveProperty('status');
    expect(onCreate.mock.calls[0][0]).not.toHaveProperty('progress');
  });

  it('F3 : formulaire ancien (projet archivé entre-temps) → le statut « en cours » n’est pas renvoyé', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    const { rerender } = render(<CreateProjectModal isOpen project={projet} onClose={vi.fn()} onCreate={onCreate} />);
    await focusInitial();
    rerender(<CreateProjectModal isOpen project={{ ...projet, status: 'archived' }} onClose={vi.fn()} onCreate={onCreate} />);
    await user.selectOptions(screen.getByLabelText('Priorité'), 'high');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ priority: 'high' });
  });

  it('description volontairement vidée → envoyée vide (le backend l’efface)', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(<CreateProjectModal isOpen project={projet} onClose={vi.fn()} onCreate={onCreate} />);
    await focusInitial();
    await user.clear(screen.getByLabelText('Description'));
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ description: '' });
  });

  it('D2 : date vidée → message explicite et aucune requête ; date changée → envoyée seule', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(
      <CreateProjectModal isOpen project={{ ...projet, dueDate: '2026-12-31' }} onClose={vi.fn()} onCreate={onCreate} />
    );
    await focusInitial();
    const echeance = screen.getByLabelText('Échéance');
    await user.clear(echeance);
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).not.toHaveBeenCalled();
    expect(screen.getByText("L'effacement d'une date n'est pas encore pris en charge.")).toBeInTheDocument();

    await user.type(echeance, '2027-01-15');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenCalledWith({ dueDate: '2027-01-15' });
  });

  it('D1 : aucune modification → formulaire fermé sans requête', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    const onClose = vi.fn();
    render(<CreateProjectModal isOpen project={projet} onClose={onClose} onCreate={onCreate} />);
    await focusInitial();
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('création inchangée : nom, priorité, statut « en cours » envoyés', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(<CreateProjectModal isOpen onClose={vi.fn()} onCreate={onCreate} />);
    await user.type(screen.getByLabelText('Nom du projet'), 'Nouveau');
    await user.click(screen.getByRole('button', { name: 'Créer le projet' }));
    expect(onCreate).toHaveBeenCalledWith({
      name: 'Nouveau', description: undefined, priority: 'medium', startDate: undefined, dueDate: undefined,
      status: 'active', progress: 0,
    });
  });

  it("refuse une échéance antérieure à la date de début", async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(<CreateProjectModal isOpen onClose={vi.fn()} onCreate={onCreate} />);
    await user.type(screen.getByLabelText('Nom du projet'), 'Nouveau');
    await user.type(screen.getByLabelText('Date de début'), '2026-05-10');
    await user.type(screen.getByLabelText('Échéance'), '2026-05-01');
    await user.click(screen.getByRole('button', { name: 'Créer le projet' }));
    expect(onCreate).not.toHaveBeenCalled();
    expect(screen.getByText(/postérieure ou égale/)).toBeInTheDocument();
  });
});

describe('CreateUserModal', () => {
  it('modification : pas de mot de passe ; rôle modifié seul envoyé', async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    render(
      <CreateUserModal
        isOpen
        user={utilisateur('u1', 'Alice')}
        onClose={vi.fn()}
        onCreate={vi.fn()}
        onUpdate={onUpdate}
      />
    );
    await focusInitial();
    expect(screen.queryByLabelText('Mot de passe initial')).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Rôle'), 'manager');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onUpdate).toHaveBeenCalledWith({ role: 'manager' });
  });

  // AUD-002, F2 : le rôle n'est jamais envoyé s'il n'a pas été changé.
  it('F2 : nom modifié seul → ni rôle ni email dans la requête', async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    render(
      <CreateUserModal isOpen user={{ ...utilisateur('u1', 'Alice'), role: 'admin' }} onClose={vi.fn()}
        onCreate={vi.fn()} onUpdate={onUpdate} />
    );
    await focusInitial();
    const nom = screen.getByLabelText('Nom complet');
    await user.clear(nom);
    await user.type(nom, 'Alice Martin');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onUpdate).toHaveBeenCalledWith({ name: 'Alice Martin' });
    expect(onUpdate.mock.calls[0][0]).not.toHaveProperty('role');
  });

  it('F2 : email en majuscules ou entouré d’espaces = inchangé ; nouvel email normalisé', async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    render(<CreateUserModal isOpen user={utilisateur('u1', 'Alice')} onClose={vi.fn()} onCreate={vi.fn()} onUpdate={onUpdate} />);
    await focusInitial();
    const email = screen.getByLabelText('Email');
    await user.clear(email);
    await user.type(email, '  U1@Exemple.COM ');
    const nom = screen.getByLabelText('Nom complet');
    await user.clear(nom);
    await user.type(nom, 'Alicia');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onUpdate).toHaveBeenLastCalledWith({ name: 'Alicia' });

    await user.clear(email);
    await user.type(email, ' Nouvel@Exemple.com');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onUpdate).toHaveBeenLastCalledWith({ name: 'Alicia', email: 'nouvel@exemple.com' });
  });

  it('F2 : formulaire ancien (rétrogradé entre-temps) → le rôle d’administrateur n’est pas renvoyé', async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    const ouvertEnAdmin = { ...utilisateur('u1', 'Alice'), role: 'admin' as const };
    const { rerender } = render(
      <CreateUserModal isOpen user={ouvertEnAdmin} onClose={vi.fn()} onCreate={vi.fn()} onUpdate={onUpdate} />
    );
    await focusInitial();
    // Rétrogradé par un autre administrateur pendant que le formulaire est ouvert.
    rerender(
      <CreateUserModal isOpen user={{ ...ouvertEnAdmin, role: 'member' }} onClose={vi.fn()} onCreate={vi.fn()}
        onUpdate={onUpdate} />
    );
    const nom = screen.getByLabelText('Nom complet');
    await user.clear(nom);
    await user.type(nom, 'Alice M.');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onUpdate).toHaveBeenCalledWith({ name: 'Alice M.' });
    expect(onUpdate.mock.calls[0][0]).not.toHaveProperty('role');
  });

  it('D1 : aucune modification → formulaire fermé sans requête', async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    const onClose = vi.fn();
    render(<CreateUserModal isOpen user={utilisateur('u1', 'Alice')} onClose={onClose} onCreate={vi.fn()} onUpdate={onUpdate} />);
    await focusInitial();
    const nom = screen.getByLabelText('Nom complet');
    await user.type(nom, '   ');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onUpdate).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('création inchangée : nom, email, rôle, mot de passe et compte actif envoyés', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(<CreateUserModal isOpen onClose={vi.fn()} onCreate={onCreate} />);
    await user.type(screen.getByLabelText('Nom complet'), 'Nouveau');
    await user.type(screen.getByLabelText('Email'), 'N@Exemple.com');
    await user.type(screen.getByLabelText('Mot de passe initial'), 'MotDePasse123');
    await user.click(screen.getByRole('button', { name: "Ajouter l'utilisateur" }));
    expect(onCreate).toHaveBeenCalledWith({
      name: 'Nouveau', email: 'n@exemple.com', role: 'member', password: 'MotDePasse123', isActive: true,
    });
  });
});

describe('Modal : le focus reste dans le champ en cours de saisie', () => {
  it('ne replace pas le focus au début de la fenêtre après chaque caractère', async () => {
    const user = userEvent.setup();
    render(<CreateProjectModal isOpen onClose={vi.fn()} onCreate={vi.fn()} />);
    await focusInitial();
    expect(document.activeElement).toBe(screen.getByLabelText('Nom du projet'));

    const description = screen.getByLabelText('Description');
    await user.click(description);
    await user.type(description, 'abc');
    await focusInitial();
    await user.type(description, 'def');
    await focusInitial();
    expect(document.activeElement).toBe(description);
    expect(description).toHaveValue('abcdef');
  });
});

// AUD-002, F6 : conflit 409 en modification, saisie conservée, rechargement uniquement sur clic.
describe('Conflit 409 en modification', () => {
  const conflit = (message: string) => new ApiError(message, 409, 'CONFLICT');

  it('utilisateur : message, saisie conservée, relecture demandée, valeurs remplacées seulement après clic', async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn().mockRejectedValue(conflit('Impossible de retirer le rôle du dernier administrateur actif.'));
    const onConflit = vi.fn().mockResolvedValue(undefined);
    const alice = { ...utilisateur('u1', 'Alice'), role: 'admin' as const };
    const { rerender } = render(
      <CreateUserModal isOpen user={alice} onClose={vi.fn()} onCreate={vi.fn()} onUpdate={onUpdate} onConflit={onConflit} />
    );
    await focusInitial();
    await user.selectOptions(screen.getByLabelText('Rôle'), 'member');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Impossible de retirer le rôle du dernier administrateur actif.')).toBeInTheDocument();
    expect(screen.getByText(/Votre saisie est conservée/)).toBeInTheDocument();
    expect(onConflit).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole('button', { name: 'Recharger les valeurs actuelles' })).toBeInTheDocument();

    // Le parent a relu l'utilisateur : la saisie n'est pas remplacée automatiquement.
    rerender(
      <CreateUserModal isOpen user={{ ...alice, name: 'Alice Martin' }} onClose={vi.fn()} onCreate={vi.fn()}
        onUpdate={onUpdate} onConflit={onConflit} />
    );
    expect(screen.getByLabelText('Rôle')).toHaveValue('member');
    expect(screen.getByLabelText('Nom complet')).toHaveValue('Alice');

    await user.click(screen.getByRole('button', { name: 'Recharger les valeurs actuelles' }));
    expect(screen.getByLabelText('Nom complet')).toHaveValue('Alice Martin');
    expect(screen.getByLabelText('Rôle')).toHaveValue('admin');
    expect(screen.queryByText(/Votre saisie est conservée/)).not.toBeInTheDocument();

    // Nouvelle référence : sans modification, aucune nouvelle requête.
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onUpdate).toHaveBeenCalledTimes(1);
  });

  it('projet : saisie conservée après relecture ; après rechargement, seuls les nouveaux changements partent', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn().mockRejectedValueOnce(conflit('Cette ressource a été modifiée par quelqu’un d’autre.'));
    const onConflit = vi.fn().mockResolvedValue(undefined);
    const { rerender } = render(
      <CreateProjectModal isOpen project={projet} onClose={vi.fn()} onCreate={onCreate} onConflit={onConflit} />
    );
    await focusInitial();
    const nom = screen.getByLabelText('Nom du projet');
    await user.clear(nom);
    await user.type(nom, 'Ma version');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await screen.findByRole('button', { name: 'Recharger les valeurs actuelles' });

    rerender(
      <CreateProjectModal isOpen project={{ ...projet, name: 'Version d’un collègue', status: 'on_hold' }}
        onClose={vi.fn()} onCreate={onCreate} onConflit={onConflit} />
    );
    expect(screen.getByLabelText('Nom du projet')).toHaveValue('Ma version');

    await user.click(screen.getByRole('button', { name: 'Recharger les valeurs actuelles' }));
    expect(screen.getByLabelText('Nom du projet')).toHaveValue('Version d’un collègue');
    expect(screen.getByLabelText('Statut')).toHaveValue('on_hold');
    await user.selectOptions(screen.getByLabelText('Priorité'), 'urgent');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate).toHaveBeenLastCalledWith({ priority: 'urgent' });
  });

  it('activité : relecture impossible → message, pas de bouton de rechargement, saisie conservée', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn().mockRejectedValue(conflit('Impossible d’ajouter une activité à un projet terminé ou archivé.'));
    const onConflit = vi.fn().mockRejectedValue(new Error('réseau'));
    render(
      <CreateActivityModal isOpen activity={activite} projects={[projet]} onClose={vi.fn()} onCreate={onCreate}
        onConflit={onConflit} />
    );
    await focusInitial();
    const titre = screen.getByLabelText("Titre de l'activité");
    await user.clear(titre);
    await user.type(titre, 'Ma saisie');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText('Impossible de relire les valeurs actuelles.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Recharger les valeurs actuelles' })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Titre de l'activité")).toHaveValue('Ma saisie');
  });

  it.each([
    [403, 'FORBIDDEN', "Vous n'avez pas les droits nécessaires pour modifier ce projet."],
    [422, 'VALIDATION_ERROR', 'Le nom du projet ne peut pas être vide.'],
  ] as const)('erreur %s : message affiché, aucune relecture automatique', async (status, code, message) => {
    const user = userEvent.setup();
    const onCreate = vi.fn().mockRejectedValue(new ApiError(message, status, code));
    const onConflit = vi.fn().mockResolvedValue(undefined);
    render(<CreateProjectModal isOpen project={projet} onClose={vi.fn()} onCreate={onCreate} onConflit={onConflit} />);
    await focusInitial();
    await user.selectOptions(screen.getByLabelText('Priorité'), 'low');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(onConflit).not.toHaveBeenCalled();
    expect(screen.queryByText(/Votre saisie est conservée/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Recharger les valeurs actuelles' })).not.toBeInTheDocument();
  });
});

// AUD-002, F6 : projet clôturé pendant la modification, formulaire verrouillé (lecture seule).
describe('CreateActivityModal : formulaire verrouillé (AUD-002, F6)', () => {
  const VERROU = 'Ce projet est terminé : ses activités sont en lecture seule.';
  const responsables: User[] = [
    { id: 'u1', name: 'Alice', email: 'alice@exemple.com', role: 'member', isActive: true, createdAt: '2026-01-01T00:00:00Z' },
  ];
  const datee: Activity = { ...activite, description: 'Texte', startDate: '2026-02-01', dueDate: '2026-03-01' };
  const formulaire = () => document.getElementById('edit-activity-form') as HTMLFormElement;
  const pied = () => screen.getByRole('button', { name: 'Enregistrer' }).parentElement as HTMLElement;

  it('modification complète : message, champs en lecture seule mais sélectionnables, « Fermer » seul actif', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    const onClose = vi.fn();
    render(
      <CreateActivityModal isOpen activity={datee} projects={[projet]} assignees={responsables}
        onClose={onClose} onCreate={onCreate} verrou={VERROU} />
    );
    await focusInitial();

    expect(screen.getByText(VERROU)).toBeInTheDocument();
    expect(screen.getByText(/ne peut plus être enregistrée/)).toBeInTheDocument();
    for (const libelle of ["Titre de l'activité", 'Description', 'Progression (%)', 'Date de début', 'Échéance']) {
      const champ = screen.getByLabelText(libelle);
      expect(champ).toHaveAttribute('readonly');
      // Lecture seule et non désactivé : le texte reste sélectionnable et copiable.
      expect(champ).toBeEnabled();
    }
    for (const libelle of ['Statut', 'Priorité', 'Responsable']) {
      expect(screen.getByLabelText(libelle)).toBeDisabled();
    }
    await user.type(screen.getByLabelText("Titre de l'activité"), ' ajout');
    expect(screen.getByLabelText("Titre de l'activité")).toHaveValue('Maquettes');

    expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
    expect(within(pied()).queryByRole('button', { name: 'Annuler' })).not.toBeInTheDocument();
    const fermer = within(pied()).getByRole('button', { name: 'Fermer' });
    expect(fermer).toBeEnabled();

    // Envoi déclenché autrement (touche Entrée, script) : aucun appel.
    fireEvent.submit(formulaire());
    expect(onCreate).not.toHaveBeenCalled();

    await user.click(fermer);
    expect(onClose).toHaveBeenCalled();
  });

  it('suivi seul : statut désactivé, progression en lecture seule, aucun envoi', async () => {
    const onCreate = vi.fn();
    render(
      <CreateActivityModal isOpen trackOnly activity={activite} projects={[projet]}
        onClose={vi.fn()} onCreate={onCreate} verrou={VERROU} />
    );
    await focusInitial();
    expect(screen.getByText(VERROU)).toBeInTheDocument();
    expect(screen.getByLabelText('Statut')).toBeDisabled();
    expect(screen.getByLabelText('Progression (%)')).toHaveAttribute('readonly');
    expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
    fireEvent.submit(formulaire());
    expect(onCreate).not.toHaveBeenCalled();
  });

  it('verrou reçu après un 409 : saisie conservée, bouton « Recharger » masqué', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn().mockRejectedValue(new ApiError('Projet terminé.', 409, 'CONFLICT'));
    const onConflit = vi.fn().mockResolvedValue(undefined);
    const props = {
      isOpen: true, activity: activite, projects: [projet], onClose: vi.fn(), onCreate, onConflit,
    };
    const { rerender } = render(<CreateActivityModal {...props} />);
    await focusInitial();
    const titre = screen.getByLabelText("Titre de l'activité");
    await user.clear(titre);
    await user.type(titre, 'Ma saisie');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByRole('button', { name: 'Recharger les valeurs actuelles' })).toBeInTheDocument();

    // Le parent a relu le projet : il est clôturé.
    rerender(<CreateActivityModal {...props} verrou={VERROU} />);

    expect(screen.getByText(VERROU)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Recharger les valeurs actuelles' })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Titre de l'activité")).toHaveValue('Ma saisie');
    expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
    fireEvent.submit(formulaire());
    expect(onCreate).toHaveBeenCalledTimes(1);
  });

  it('création : verrou sans effet (formulaire de création inchangé)', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn().mockResolvedValue(undefined);
    render(<CreateActivityModal isOpen projects={[projet]} onClose={vi.fn()} onCreate={onCreate} verrou={VERROU} />);
    await focusInitial();
    expect(screen.queryByText(VERROU)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("Titre de l'activité"), 'Nouvelle');
    await user.click(screen.getByRole('button', { name: "Créer l'activité" }));
    expect(onCreate).toHaveBeenCalledWith(expect.objectContaining({ title: 'Nouvelle', projectId: 'p1' }));
  });
});
