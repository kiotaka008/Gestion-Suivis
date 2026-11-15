import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HistoryTimeline } from '../components/history/HistoryTimeline';
import { ApiError } from '../services/ApiError';
import type { HistoryEvent } from '../types/models';
import type { PaginatedResponse } from '../types/api';

function evenement(n: number, auteur: string | null = 'Alice'): HistoryEvent {
  return {
    id: `e${n}`,
    action: 'project.updated',
    entityType: 'project',
    entityId: 'p1',
    description: `Action ${n}`,
    author: auteur ? { id: 'u1', name: auteur } : null,
    fields: [],
    status: null,
    createdAt: '2026-11-12T09:00:00Z',
  };
}

function page(data: HistoryEvent[], numero: number, totalPages: number): PaginatedResponse<HistoryEvent> {
  return { data, meta: { page: numero, perPage: 20, total: data.length, totalPages } };
}

describe('HistoryTimeline', () => {
  it('affiche les actions avec leur auteur, ou « Action automatique »', async () => {
    const load = vi.fn().mockResolvedValue(page([evenement(1), evenement(2, null)], 1, 1));
    render(<HistoryTimeline load={load} />);

    expect(await screen.findByText('Action 1')).toBeInTheDocument();
    expect(screen.getByText(/Alice ·/)).toBeInTheDocument();
    expect(screen.getByText(/Action automatique ·/)).toBeInTheDocument();
    expect(load).toHaveBeenCalledWith(1, 20);
    expect(screen.queryByRole('button', { name: 'Afficher plus' })).not.toBeInTheDocument();
  });

  it('charge la page suivante à la demande et conserve les précédentes', async () => {
    const user = userEvent.setup();
    const load = vi
      .fn()
      .mockResolvedValueOnce(page([evenement(1)], 1, 2))
      .mockResolvedValueOnce(page([evenement(2)], 2, 2));
    render(<HistoryTimeline load={load} />);

    await user.click(await screen.findByRole('button', { name: 'Afficher plus' }));

    expect(await screen.findByText('Action 2')).toBeInTheDocument();
    expect(screen.getByText('Action 1')).toBeInTheDocument();
    expect(load).toHaveBeenLastCalledWith(2, 20);
    expect(screen.queryByRole('button', { name: 'Afficher plus' })).not.toBeInTheDocument();
  });

  it('état vide', async () => {
    render(<HistoryTimeline load={vi.fn().mockResolvedValue(page([], 1, 0))} emptyText="Rien ici." />);
    expect(await screen.findByText('Historique vide')).toBeInTheDocument();
    expect(screen.getByText('Rien ici.')).toBeInTheDocument();
  });

  it('erreur du backend affichée, puis nouvel essai', async () => {
    const user = userEvent.setup();
    const load = vi
      .fn()
      .mockRejectedValueOnce(new ApiError('Accès refusé.', 403, 'FORBIDDEN'))
      .mockResolvedValueOnce(page([evenement(1)], 1, 1));
    render(<HistoryTimeline load={load} />);

    expect(await screen.findByText('Accès refusé.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('Action 1')).toBeInTheDocument();
  });

  it("ne recharge pas l'historique quand le parent se ré-affiche avec la même source", async () => {
    const load = vi.fn().mockResolvedValue(page([evenement(1)], 1, 1));
    const { rerender } = render(<HistoryTimeline load={load} />);
    await screen.findByText('Action 1');
    rerender(<HistoryTimeline load={load} />);
    expect(load).toHaveBeenCalledTimes(1);
  });
});
