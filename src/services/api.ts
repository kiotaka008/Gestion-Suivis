import axios, { AxiosError, type AxiosInstance } from 'axios';
import { ApiError } from './ApiError';
import type { ValidationError } from '../types/api';

/**
 * Client HTTP de l'API Spring Boot.
 * - `VITE_API_URL=/api` (défaut) : les appels passent par le proxy Vite (même origine), ce qui
 *   permet au cookie de session HttpOnly et au jeton CSRF de fonctionner sans configuration.
 * - Le backend pose le cookie `XSRF-TOKEN` ; Axios le renvoie dans l'en-tête `X-XSRF-TOKEN`
 *   pour les requêtes POST/PUT/PATCH/DELETE (`withXSRFToken` couvre aussi le cas d'une URL absolue).
 */
const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 70000, // l'assistant IA peut répondre en plusieurs dizaines de secondes
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

/** Routes pour lesquelles un 401 signifie « identifiants refusés », pas « session expirée ». */
const ROUTES_CONNEXION = ['/auth/login', '/auth/google', '/auth/me'];

interface CorpsErreur {
  message?: string;
  code?: string;
  errors?: ValidationError[];
}

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<CorpsErreur>) => {
    if (!error.response) {
      return Promise.reject(
        new ApiError('Connexion au serveur impossible. Vérifiez que le backend est démarré.', 0, 'NETWORK_ERROR')
      );
    }

    const { status, data } = error.response;
    const url = error.config?.url ?? '';
    const messageServeur = data && typeof data === 'object' && typeof data.message === 'string' ? data.message : undefined;

    // 401 hors connexion : la session a expiré → l'application revient à la page de connexion.
    if (status === 401 && !ROUTES_CONNEXION.some((r) => url.endsWith(r))) {
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
    }

    // 422 : erreurs de validation par champ (ex. { field: 'organization', message: '...' }).
    if (status === 422) {
      const validation = data && typeof data === 'object' ? data.errors : undefined;
      return Promise.reject(
        new ApiError(
          validation?.[0]?.message ?? messageServeur ?? 'Certaines informations sont invalides.',
          422,
          'VALIDATION_ERROR',
          { validation, raw: data }
        )
      );
    }

    // Les messages du backend sont rédigés pour l'utilisateur (aucun détail technique) :
    // on les affiche, sauf pour les erreurs serveur.
    const erreur = ApiError.fromStatus(status);
    if (messageServeur && status < 500) {
      return Promise.reject(new ApiError(messageServeur, status, erreur.code, { raw: data }));
    }
    return Promise.reject(erreur);
  }
);

/** Lit toutes les pages d'une liste paginée (`perPage` ≤ 100 côté backend). */
export async function lireTout<T>(url: string, params: Record<string, unknown> = {}): Promise<T[]> {
  const resultat: T[] = [];
  let page = 1;
  for (;;) {
    const { data } = await api.get<{ data: T[]; meta: { totalPages: number } }>(url, {
      params: { ...params, page, perPage: 100 },
    });
    resultat.push(...data.data);
    if (page >= data.meta.totalPages || data.data.length === 0) {
      return resultat;
    }
    page += 1;
  }
}

export default api;
