
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Mail, ShieldCheck } from 'lucide-react';
import { Input } from '../components/ui/Input';
import { Alert } from '../components/ui/Alert';
import { ThemeToggle } from '../components/ui/ThemeToggle';
import { ROUTES } from '../constants/routes';
import arriere from '../assets/arriere.png';

function AppLogo() {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-blue-600 shadow-lg shadow-indigo-500/30">
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="white"
          strokeWidth="2.5"
        >
          <rect x="3" y="3" width="7" height="7" rx="1.5" />
          <rect x="14" y="3" width="7" height="7" rx="1.5" />
          <rect x="3" y="14" width="7" height="7" rx="1.5" />
          <rect x="14" y="14" width="7" height="7" rx="1.5" />
        </svg>
      </div>

      <span className="text-lg font-bold text-white">
        E-<span className="text-indigo-300">prest</span>
      </span>
    </div>
  );
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [showNotice, setShowNotice] = useState(false);

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setShowNotice(true);
  };

  return (
    <div className="relative flex min-h-screen bg-[#0a0a1a]">
      {/* COLONNE GAUCHE */}
      <div className="relative hidden flex-1 lg:block">
        <img
          src={arriere}
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          aria-hidden="true"
        />

        <div
          className="absolute inset-0 bg-gradient-to-r from-black/60 via-black/30 to-transparent"
          aria-hidden="true"
        />

        <div className="relative z-10 flex h-full flex-col justify-between p-10">
          <AppLogo />

          <div className="max-w-xl">
            <h1 className="text-5xl font-bold leading-[1.1] tracking-tight text-white xl:text-6xl">
              Retrouvez l'accès à votre espace de travail.
            </h1>

            <p className="mt-5 max-w-md text-base leading-relaxed text-white/70">
              La sécurité de votre compte et de vos projets reste notre priorité.
            </p>
          </div>

          <div className="text-xs text-white/50">
            © {new Date().getFullYear()} E-prest Project Manager
          </div>
        </div>
      </div>

      {/* COLONNE DROITE */}
      <div className="flex w-full items-center justify-center bg-[#f5f5fa] p-6 lg:w-[560px] lg:p-12 dark:bg-slate-950">
        <div className="relative w-full max-w-md">
          <div className="absolute -left-3 top-0 h-16 w-1.5 rounded-full bg-gradient-to-b from-indigo-500 to-purple-500" />

          <div className="relative rounded-2xl bg-white p-8 shadow-xl shadow-slate-200/50 dark:bg-slate-900 dark:shadow-slate-900/50">
            <div className="absolute right-3 top-3">
              <ThemeToggle />
            </div>

            <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-blue-600 shadow-lg shadow-indigo-500/30">
              <Mail className="h-6 w-6 text-white" />
            </div>

            <h2 className="text-3xl font-bold text-slate-900 dark:text-white">
              Mot de passe oublié ?
            </h2>

            <p className="mt-2 text-sm leading-relaxed text-slate-500 dark:text-slate-400">
              Saisissez l'adresse e-mail associée à votre compte. Vous pourrez
              ensuite recevoir les instructions de réinitialisation lorsque
              ce service sera activé.
            </p>

            {showNotice && (
              <Alert variant="warning" className="mt-5">
                La réinitialisation du mot de passe n'est pas encore disponible.
                Le service sera activé après son intégration au backend.
              </Alert>
            )}

            <form onSubmit={onSubmit} className="mt-6 space-y-5">
              <Input
                label="Adresse e-mail"
                name="email"
                type="email"
                autoComplete="email"
                placeholder="vous@email.com"
                leftIcon={<Mail className="h-4 w-4" />}
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  setShowNotice(false);
                }}
                required
              />

              <button
                type="submit"
                className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-indigo-500 via-indigo-500 to-blue-600 text-sm font-semibold text-white shadow-lg shadow-indigo-500/30 transition-all hover:from-indigo-600 hover:to-blue-700"
              >
                Demander la réinitialisation
              </button>
            </form>

            <div className="mt-6 flex items-start justify-center gap-2 text-center text-xs text-slate-400">
              <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              <span>
                Ne partagez jamais vos informations de connexion avec une autre personne.
              </span>
            </div>

            <div className="mt-6 border-t border-slate-200 pt-5 dark:border-slate-700">
              <Link
                to={ROUTES.LOGIN}
                className="flex items-center justify-center gap-2 text-sm font-semibold text-indigo-600 hover:underline dark:text-indigo-400"
              >
                <ArrowLeft className="h-4 w-4" />
                Retour à la connexion
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
