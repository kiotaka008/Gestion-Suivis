import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import { ApiError } from './ApiError';
import type { ValidationError } from '../types/api';

const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
  // À activer côté backend : cookies HttpOnly / SameSite
  withCredentials: true,
});

// ─── Intercepteur de requête ─────────────────────────────
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Si le backend utilise un CSRF token via cookie, il peut être ajouté ici :
    // const csrf = readCookie('csrf_token');
    // if (csrf) config.headers['X-CSRF-Token'] = csrf;
    return config;
  },
  (error) => Promise.reject(error)
);

// ─── Intercepteur de réponse ─────────────────────────────
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    // Erreur réseau (pas de réponse)
    if (!error.response) {
      return Promise.reject(
        new ApiError(
          'Connexion impossible. Vérifiez votre réseau.',
          0,
          'NETWORK_ERROR'
        )
      );
    }

    const { status, data } = error.response;

    // 401 → on notifie l'app pour redirection éventuelle
    if (status === 401) {
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
    }

    // 422 → remonter les erreurs de validation structurées
    if (status === 422 && data && typeof data === 'object' && 'errors' in data) {
      const validation = (data as { errors?: ValidationError[] }).errors;
      return Promise.reject(
        new ApiError(
          'Certaines informations sont invalides.',
          422,
          'VALIDATION_ERROR',
          { validation, raw: data }
        )
      );
    }

    // Cas générique
    return Promise.reject(ApiError.fromStatus(status));
  }
);

export default api;