import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button } from '../components/ui/Button';

describe('Button', () => {
  it('affiche le texte correctement', () => {
    render(<Button>Cliquer ici</Button>);
    expect(screen.getByText('Cliquer ici')).toBeInTheDocument();
  });

  it('appelle onClick quand on clique', async () => {
    const handleClick = vi.fn();
    const user = userEvent.setup();
    
    render(<Button onClick={handleClick}>Cliquer</Button>);
    
    await user.click(screen.getByText('Cliquer'));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('est désactivé quand disabled est true', () => {
    render(<Button disabled>Désactivé</Button>);
    expect(screen.getByText('Désactivé')).toBeDisabled();
  });

  it('affiche un spinner quand isLoading est true', () => {
    const { container } = render(<Button isLoading>Chargement</Button>);
    // Le spinner est un SVG avec la classe animate-spin
    const spinner = container.querySelector('.animate-spin');
    expect(spinner).toBeInTheDocument();
  });

  it('applique les bonnes classes pour la variante danger', () => {
    render(<Button variant="danger">Supprimer</Button>);
    const button = screen.getByText('Supprimer');
    expect(button).toHaveClass('bg-danger');
  });
});