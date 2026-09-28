import type { ApiErrorCode, ValidationError } from '../types/api';

export class ApiError extends Error {
  public readonly status: number;
  public readonly code: ApiErrorCode;
  public readonly validation?: ValidationError[];
  public readonly raw?: unknown;

  constructor(
    message: string,
    status: number,
    code: ApiErrorCode,
    options?: { validation?: ValidationError[]; raw?: unknown }
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.validation = options?.validation;
    this.raw = options?.raw;
  }

  /**
   * Message utilisateur-friendly. Ne jamais afficher `raw` à l'utilisateur.
   */
  static fromStatus(status: number, fallback = 'Une erreur est survenue.'): ApiError {
    const map: Record<number, { code: ApiErrorCode; message: string }> = {
      401: { code: 'UNAUTHORIZED', message: 'Votre session a expiré. Veuillez vous reconnecter.' },
      403: { code: 'FORBIDDEN', message: "Vous n'avez pas les droits nécessaires pour cette action." },
      404: { code: 'NOT_FOUND', message: 'La ressource demandée est introuvable.' },
      409: { code: 'CONFLICT', message: 'Cette action entre en conflit avec des données existantes.' },
      422: { code: 'VALIDATION_ERROR', message: 'Certaines informations sont invalides.' },
      429: { code: 'RATE_LIMITED', message: 'Trop de requêtes. Veuillez patienter un instant.' },
    };

    if (status >= 500) {
      return new ApiError('Erreur serveur. Veuillez réessayer plus tard.', status, 'SERVER_ERROR');
    }

    const entry = map[status];
    if (entry) return new ApiError(entry.message, status, entry.code);
    return new ApiError(fallback, status, 'UNKNOWN');
  }
}