import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Dropdown } from '../components/ui/Dropdown';
import { ProjectCard } from '../components/projects/ProjectCard';
import type { Project } from '../types/models';

const projet: Project = {
  id: 'p1',
  name: 'Refonte',
  status: 'active',
  priority: 'medium',
  progress: 40,
  ownerId: 'u1',
  memberIds: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('Dropdown', () => {
  it('ignore les entrées vides', async () => {
    const user = userEvent.setup();
    render(
      <Dropdown
        trigger={<button>Menu</button>}
        items={[{ label: 'Voir' }, false, null, undefined, { label: 'Supprimer', variant: 'danger' }]}
      />
    );
    await user.click(screen.getByText('Menu'));
    expect(screen.getAllByRole('menuitem').map((b) => b.textContent)).toEqual(['Voir', 'Supprimer']);
  });

  it("n'affiche pas le bouton de menu sans aucune action", () => {
    render(<Dropdown trigger={<button>Menu</button>} items={[false, undefined]} />);
    expect(screen.queryByText('Menu')).not.toBeInTheDocument();
  });
});

describe('ProjectCard : plus de faux bouton « Modifier »', () => {
  it("ne propose que les actions fournies par la page", async () => {
    const user = userEvent.setup();
    render(<ProjectCard project={projet} onView={vi.fn()} />);
    await user.click(screen.getByLabelText('Actions du projet'));
    expect(screen.getAllByRole('menuitem').map((b) => b.textContent)).toEqual(['Voir']);
  });

  it('appelle onEdit avec le projet', async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    render(<ProjectCard project={projet} onView={vi.fn()} onEdit={onEdit} />);
    await user.click(screen.getByLabelText('Actions du projet'));
    await user.click(screen.getByRole('menuitem', { name: 'Modifier' }));
    expect(onEdit).toHaveBeenCalledWith(projet);
  });
});
