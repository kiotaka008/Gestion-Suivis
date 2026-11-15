import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { AxiosError } from 'axios';
import api, { lireTout } from '../services/api';
import { ApiError } from '../services/ApiError';
import { projectService } from '../services/projectService';
import { activityService } from '../services/activityService';
import { userService } from '../services/userService';

/** Adaptateur HTTP simulé : chaque test décrit les réponses, aucun serveur n'est contacté. */
let repondre: (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>;
const adaptateurOriginal = api.defaults.adapter;
const requetes: InternalAxiosRequestConfig[] = [];

function reponse(config: InternalAxiosRequestConfig, status: number, data: unknown): Promise<AxiosResponse> {
  const r = { data, status, statusText: '', headers: {}, config } as AxiosResponse;
  if (status >= 400) {
    return Promise.reject(new AxiosError('erreur', String(status), config, null, r));
  }
  return Promise.resolve(r);
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
  requetes.length = 0;
  api.defaults.adapter = ((config: InternalAxiosRequestConfig) => {
    requetes.push(config);
    return repondre(config);
  }) as AxiosAdapter;
});

afterEach(() => {
  api.defaults.adapter = adaptateurOriginal;
});

describe('Client API : traduction des erreurs du backend', () => {
  it('422 : premier message de champ et liste des erreurs de validation', async () => {
    repondre = (c) =>
      reponse(c, 422, {
        message: 'Certaines informations sont invalides.',
        errors: [{ field: 'organization', message: "Précisez l'organisation à rejoindre." }],
      });
    const err = await erreurDe(api.post('/auth/register', {}));
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.message).toBe("Précisez l'organisation à rejoindre.");
    expect(err.validation?.[0].field).toBe('organization');
  });

  it('4xx : le message du backend est affiché tel quel (dont 429 du limiteur)', async () => {
    repondre = (c) => reponse(c, 429, { message: 'Trop de tentatives de connexion échouées. Réessayez dans 15 minutes.' });
    const err = await erreurDe(api.post('/auth/login', {}));
    expect(err.status).toBe(429);
    expect(err.code).toBe('RATE_LIMITED');
    expect(err.message).toBe('Trop de tentatives de connexion échouées. Réessayez dans 15 minutes.');
  });

  it("5xx : message générique, jamais le détail technique du serveur", async () => {
    repondre = (c) => reponse(c, 500, { message: 'NullPointerException at ...' });
    const err = await erreurDe(api.get('/projects'));
    expect(err.message).toBe('Erreur serveur. Veuillez réessayer plus tard.');
  });

  it('erreur réseau : message explicite', async () => {
    repondre = (c) => Promise.reject(new AxiosError('Network Error', 'ERR_NETWORK', c));
    const err = await erreurDe(api.get('/projects'));
    expect(err.code).toBe('NETWORK_ERROR');
  });

  it("401 : session expirée signalée à l'application, sauf pour la connexion elle-même", async () => {
    const ecouteur = vi.fn();
    window.addEventListener('auth:unauthorized', ecouteur);
    repondre = (c) => reponse(c, 401, { message: 'Session expirée.' });

    await erreurDe(api.get('/projects'));
    expect(ecouteur).toHaveBeenCalledTimes(1);

    await erreurDe(api.post('/auth/login', {}));
    await erreurDe(api.get('/auth/me'));
    expect(ecouteur).toHaveBeenCalledTimes(1);
    window.removeEventListener('auth:unauthorized', ecouteur);
  });
});

describe('Client API : appels', () => {
  it('lireTout parcourt toutes les pages par paquets de 100', async () => {
    repondre = (c) => {
      const numero = c.params.page as number;
      return reponse(c, 200, { data: [numero], meta: { page: numero, perPage: 100, total: 3, totalPages: 3 } });
    };
    expect(await lireTout<number>('/projects', { sortBy: 'createdAt' })).toEqual([1, 2, 3]);
    expect(requetes.map((r) => r.params)).toEqual([
      { sortBy: 'createdAt', page: 1, perPage: 100 },
      { sortBy: 'createdAt', page: 2, perPage: 100 },
      { sortBy: 'createdAt', page: 3, perPage: 100 },
    ]);
  });

  it("historique : bonne route et pagination pour projets, activités et utilisateurs", async () => {
    repondre = (c) => reponse(c, 200, { data: [], meta: { page: 2, perPage: 10, total: 0, totalPages: 0 } });
    await projectService.history('p1', 2, 10);
    await activityService.history('a1');
    await userService.history('u1', 3, 5);
    expect(requetes.map((r) => [r.url, r.params])).toEqual([
      ['/projects/p1/history', { page: 2, perPage: 10 }],
      ['/activities/a1/history', { page: 1, perPage: 20 }],
      ['/users/u1/history', { page: 3, perPage: 5 }],
    ]);
  });

  it('modifications : PATCH sur la bonne ressource avec les seuls champs fournis', async () => {
    repondre = (c) => reponse(c, 200, {});
    await projectService.update('p1', { status: 'archived' });
    await activityService.update('a1', { status: 'done', progress: 100 });
    await userService.update('u1', { role: 'manager' });
    expect(requetes.map((r) => [r.method, r.url, JSON.parse(r.data as string)])).toEqual([
      ['patch', '/projects/p1', { status: 'archived' }],
      ['patch', '/activities/a1', { status: 'done', progress: 100 }],
      ['patch', '/users/u1', { role: 'manager' }],
    ]);
  });

  it('membres : ajout et retrait sur la route du projet', async () => {
    repondre = (c) => reponse(c, 200, {});
    await projectService.addMember('p1', 'u2');
    await projectService.removeMember('p1', 'u2');
    expect(requetes.map((r) => [r.method, r.url])).toEqual([
      ['post', '/projects/p1/members'],
      ['delete', '/projects/p1/members/u2'],
    ]);
    expect(JSON.parse(requetes[0].data as string)).toEqual({ userId: 'u2' });
  });
});
