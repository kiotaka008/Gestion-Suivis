import { useEffect, useRef, useState } from 'react';
import { authService } from '../../services/authService';

/** API minimale de Google Identity Services (https://accounts.google.com/gsi/client). */
interface GoogleIdentity {
  accounts: {
    id: {
      initialize: (options: {
        client_id: string;
        callback: (response: { credential?: string }) => void;
        ux_mode?: 'popup' | 'redirect';
      }) => void;
      renderButton: (element: HTMLElement, options: Record<string, unknown>) => void;
    };
  };
}

declare global {
  interface Window {
    google?: GoogleIdentity;
  }
}

const URL_SCRIPT = 'https://accounts.google.com/gsi/client';
let chargement: Promise<void> | null = null;

function chargerScriptGoogle(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!chargement) {
    chargement = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = URL_SCRIPT;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => {
        chargement = null;
        reject(new Error('Script Google indisponible'));
      };
      document.head.appendChild(script);
    });
  }
  return chargement;
}

function GoogleIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
      <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
      <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
      <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
    </svg>
  );
}

export interface GoogleSignInButtonProps {
  /** Reçoit le jeton d'identité Google à envoyer au backend (POST /api/auth/google). */
  onCredential: (credential: string) => void;
  onError?: (message: string) => void;
}

/**
 * Bouton « Continuer avec Google ». L'identifiant client est fourni par le backend
 * (GET /api/auth/google/config, variable GOOGLE_CLIENT_ID). Sans configuration, le bouton
 * reste visible mais désactivé avec une explication.
 */
export function GoogleSignInButton({ onCredential, onError }: GoogleSignInButtonProps) {
  const conteneur = useRef<HTMLDivElement>(null);
  const [etat, setEtat] = useState<'chargement' | 'pret' | 'indisponible'>('chargement');
  const [raison, setRaison] = useState<string>('');
  const rappel = useRef(onCredential);
  const rappelErreur = useRef(onError);
  useEffect(() => {
    rappel.current = onCredential;
    rappelErreur.current = onError;
  });

  useEffect(() => {
    let annule = false;
    (async () => {
      try {
        const config = await authService.googleConfig();
        if (!config.enabled || !config.clientId) {
          throw new Error("La connexion avec Google n'est pas configurée sur le serveur (GOOGLE_CLIENT_ID).");
        }
        await chargerScriptGoogle();
        if (annule || !conteneur.current || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: config.clientId,
          ux_mode: 'popup',
          callback: (reponse) => {
            if (reponse.credential) rappel.current(reponse.credential);
            else rappelErreur.current?.('Connexion Google annulée.');
          },
        });
        window.google.accounts.id.renderButton(conteneur.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          text: 'continue_with',
          shape: 'pill',
          width: conteneur.current.offsetWidth || 360,
          locale: 'fr',
        });
        setEtat('pret');
      } catch (e) {
        if (annule) return;
        setRaison(e instanceof Error ? e.message : 'Connexion avec Google indisponible.');
        setEtat('indisponible');
      }
    })();
    return () => {
      annule = true;
    };
  }, []);

  return (
    <div>
      <div ref={conteneur} className={etat === 'pret' ? 'flex w-full justify-center' : 'hidden'} />
      {etat !== 'pret' && (
        <button
          type="button"
          disabled
          title={raison}
          className="flex h-12 w-full cursor-not-allowed items-center justify-center gap-3 rounded-xl border border-slate-200 bg-white text-sm font-semibold text-slate-700 opacity-60 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
        >
          <GoogleIcon className="h-5 w-5" />
          Continuer avec Google
        </button>
      )}
      {etat === 'indisponible' && <p className="mt-2 text-center text-xs text-slate-400">{raison}</p>}
    </div>
  );
}
