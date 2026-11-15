import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { AxiosError } from 'axios';
import api from '../services/api';
import { ApiError } from '../services/ApiError';

/**
 * Messages des conflits de verrou du backend (sous-lot 4.1) : 409 (ressource modifiée entre-temps)
 * et 503 (service occupé, avec Retry-After) doivent atteindre l'utilisateur. Aucun serveur contacté.
 */
let repondre: (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>;
const adaptateurOriginal = api.defaults.adapter;

function echec(config: InternalAxiosRequestConfig, status: number, data: unknown): Promise<AxiosResponse> {
  const r = { data, status, statusText: '', headers: {}, config } as AxiosResponse;
  return Promise.reject(new AxiosError('erreur', String(status), config, null, r));
}

async function erreurDe(promesse: Promise<unknown>): Promise<ApiError> {
  try {
    await promesse;
  } catch (e) {
    return e as ApiError;
  }
  throw new Error('La requête aurait dû échouer');
}

beforeEach(() => {
  api.defaults.adapter = ((config: InternalAxiosRequestConfig) => repondre(config)) as AxiosAdapter;
});

afterEach(() => {
  api.defaults.adapter = adaptateurOriginal;
});

describe('Client API : conflits de verrou', () => {
  it('409 : message « ressource modifiée » du backend affiché', async () => {
    const message = "Cette ressource a été modifiée par quelqu'un d'autre. Rechargez-la avant de réessayer.";
    repondre = (c) => echec(c, 409, { status: 409, code: 'CONFLICT', message });
    const err = await erreurDe(api.patch('/projects/p1', { name: 'Nouveau' }));
    expect(err.status).toBe(409);
    expect(err.code).toBe('CONFLICT');
    expect(err.message).toBe(message);
  });

  it('503 du backend : message « service occupé » affiché', async () => {
    const message = 'Le service est temporairement occupé. Réessayez dans quelques instants.';
    repondre = (c) => echec(c, 503, { status: 503, code: 'SERVER_ERROR', message });
    const err = await erreurDe(api.patch('/activities/a1', { progress: 50 }));
    expect(err.status).toBe(503);
    expect(err.code).toBe('SERVER_ERROR');
    expect(err.message).toBe(message);
  });

  it('503 sans corps JSON (proxy, serveur arrêté) : message générique', async () => {
    repondre = (c) => echec(c, 503, '<html><body>Service Unavailable</body></html>');
    const err = await erreurDe(api.get('/projects'));
    expect(err.status).toBe(503);
    expect(err.message).toBe('Erreur serveur. Veuillez réessayer plus tard.');
  });

  it('500 et 502 : le message du serveur reste masqué', async () => {
    for (const status of [500, 502]) {
      repondre = (c) => echec(c, status, { message: 'java.sql.SQLException: détail interne' });
      const err = await erreurDe(api.get('/projects'));
      expect(err.message).toBe('Erreur serveur. Veuillez réessayer plus tard.');
    }
  });
});
