import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateActivityModal } from '../components/activities/CreateActivityModal';
import { CreateProjectModal } from '../components/projects/CreateProjectModal';
import { CreateUserModal } from '../components/users/CreateUserModal';
import type { Activity, Project, User } from '../types/models';

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
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate.mock.calls[0][0].assigneeId).toBeUndefined();

    await user.selectOptions(screen.getByLabelText('Responsable'), 'u2');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate.mock.calls[1][0].assigneeId).toBe('u2');
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
    expect(onCreate).toHaveBeenCalledWith(expect.objectContaining({ status: 'archived', ownerId: undefined }));

    await user.selectOptions(screen.getByLabelText('Chef de projet'), 'u2');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(onCreate.mock.calls[1][0].ownerId).toBe('u2');
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
  it('modification : pas de mot de passe, nom, email et rôle envoyés', async () => {
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
    expect(onUpdate).toHaveBeenCalledWith({ name: 'Alice', email: 'u1@exemple.com', role: 'manager' });
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
