import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, Link } from 'react-router-dom';
import { Mail, Lock, User as UserIcon, ArrowRight, Shield } from 'lucide-react';
import { Input } from '../components/ui/Input';
import { Checkbox } from '../components/ui/Checkbox';
import { Alert } from '../components/ui/Alert';
import { Spinner } from '../components/ui/Spinner';
import { ThemeToggle } from '../components/ui/ThemeToggle';
import { useAuth } from '../hooks/useAuth';
import { ROUTES } from '../constants/routes';
import { ApiError } from '../services/ApiError';
import arriere from '../assets/arriere.png';

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

function AppLogo() {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-blue-600 shadow-lg shadow-indigo-500/30">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5">
          <rect x="3" y="3" width="7" height="7" rx="1.5" />
          <rect x="14" y="3" width="7" height="7" rx="1.5" />
          <rect x="3" y="14" width="7" height="7" rx="1.5" />
          <rect x="14" y="14" width="7" height="7" rx="1.5" />
        </svg>
      </div>
      <span className="text-lg font-bold text-white">
        Project<span className="text-indigo-300">Manager</span>
      </span>
    </div>
  );
}

function FeatureChip({ icon, title, subtitle }: { icon: React.ReactNode; title: string; subtitle: string }) {
  return (
    <div className="flex items-center gap-3">
      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-indigo-400/40 bg-indigo-500/20 text-white backdrop-blur-sm">
        {icon}
      </div>
      <div className="flex flex-col leading-tight">
        <span className="text-sm font-semibold text-white">{title}</span>
        <span className="text-sm font-semibold text-white">{subtitle}</span>
      </div>
    </div>
  );
}

export function RegisterPage() {
  const { isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#0a0a1a]">
        <Spinner size="lg" />
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to={ROUTES.DASHBOARD} replace />;
  }

  const onSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    if (!acceptTerms) {
      setError("Veuillez accepter les conditions d'utilisation.");
      return;
    }
    if (password.length < 8) {
      setError('Le mot de passe doit contenir au moins 8 caractères.');
      return;
    }
    setIsSubmitting(true);
    try {
      await new Promise((r) => setTimeout(r, 800));
      navigate(ROUTES.LOGIN, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Une erreur est survenue.');
    } finally {
      setIsSubmitting(false);
      setPassword('');
    }
  };

  return (
    <div className="relative flex min-h-screen bg-[#0a0a1a]">
      {/* COLONNE GAUCHE */}
      <div className="relative hidden flex-1 lg:block">
        <img src={arriere} alt="" className="absolute inset-0 h-full w-full object-cover" aria-hidden="true" />
        <div className="absolute inset-0 bg-gradient-to-r from-black/60 via-black/30 to-transparent" aria-hidden="true" />

        <div className="relative z-10 flex h-full flex-col justify-between p-10">
          <div className="flex items-center gap-10">
            <AppLogo />
            <div className="hidden items-center gap-2 text-sm text-white/70 xl:flex">
              <span>Planifier</span>
              <span className="text-white/30">·</span>
              <span>Collaborer</span>
              <span className="text-white/30">·</span>
              <span>Réussir</span>
            </div>
          </div>

          <div className="max-w-xl">
            <h1 className="text-5xl font-bold leading-[1.1] tracking-tight text-white xl:text-6xl">
              Rejoignez des milliers<br />
              d'équipes <span className="bg-gradient-to-r from-indigo-400 to-blue-400 bg-clip-text text-transparent">organisées</span>.
            </h1>
            <p className="mt-5 max-w-md text-base leading-relaxed text-white/70">
              Créez votre compte gratuitement et commencez à organiser votre travail en quelques secondes.
            </p>

            <div className="mt-10 flex flex-wrap gap-8">
              <FeatureChip
                icon={
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M13 2 3 14h7l-1 8 10-12h-7l1-8z" />
                  </svg>
                }
                title="Gratuit"
                subtitle="pour toujours"
              />
              <FeatureChip
                icon={
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                    <circle cx="9" cy="7" r="3" />
                    <path d="M2 21v-1a5 5 0 0 1 5-5h4a5 5 0 0 1 5 5v1" />
                    <circle cx="17" cy="8" r="2.5" />
                    <path d="M22 21v-1a4 4 0 0 0-3-3.87" />
                  </svg>
                }
                title="Équipes"
                subtitle="illimitées"
              />
              <FeatureChip
                icon={
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                  </svg>
                }
                title="Données"
                subtitle="sécurisées"
              />
            </div>
          </div>

          <div className="text-xs text-white/50">
            © {new Date().getFullYear()} ProjectManager
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
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5">
                <rect x="3" y="3" width="7" height="7" rx="1.5" />
                <rect x="14" y="3" width="7" height="7" rx="1.5" />
                <rect x="3" y="14" width="7" height="7" rx="1.5" />
                <rect x="14" y="14" width="7" height="7" rx="1.5" />
              </svg>
            </div>

            <h2 className="text-3xl font-bold text-slate-900 dark:text-white">Créer un compte</h2>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Commencez gratuitement, aucune carte requise.</p>

            {error && (
              <Alert variant="danger" className="mt-4">
                {error}
              </Alert>
            )}

            <form onSubmit={onSubmit} className="mt-6 space-y-5" noValidate>
              <Input
                label="Nom complet"
                name="name"
                type="text"
                autoComplete="name"
                placeholder="Marie Dupont"
                leftIcon={<UserIcon className="h-4 w-4" />}
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />

              <Input
                label="Email"
                name="email"
                type="email"
                autoComplete="email"
                placeholder="vous@email.com"
                leftIcon={<Mail className="h-4 w-4" />}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />

              <Input
                label="Mot de passe"
                name="password"
                isPassword
                autoComplete="new-password"
                placeholder="Au moins 8 caractères"
                leftIcon={<Lock className="h-4 w-4" />}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />

              <Checkbox
                label="J'accepte les conditions d'utilisation"
                checked={acceptTerms}
                onChange={(e) => setAcceptTerms(e.target.checked)}
              />

              <button
                type="submit"
                disabled={isSubmitting}
                className="group flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-indigo-500 via-indigo-500 to-blue-600 text-sm font-semibold text-white shadow-lg shadow-indigo-500/30 transition-all hover:from-indigo-600 hover:to-blue-700 disabled:opacity-60"
              >
                {isSubmitting ? (
                  <Spinner size="sm" className="text-white" />
                ) : (
                  <>
                    <ArrowRight className="h-4 w-4" />
                    Créer mon compte
                  </>
                )}
              </button>
            </form>

            <div className="my-6 flex items-center gap-3">
              <div className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
              <span className="text-xs font-medium uppercase tracking-wider text-slate-400">ou</span>
              <div className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
            </div>

            <button
              type="button"
              className="flex h-12 w-full items-center justify-center gap-3 rounded-xl border border-slate-200 bg-white text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
            >
              <GoogleIcon className="h-5 w-5" />
              S'inscrire avec Google
            </button>

            <p className="mt-6 text-center text-sm text-slate-600 dark:text-slate-400">
              Déjà un compte ?{' '}
              <Link to={ROUTES.LOGIN} className="font-semibold text-indigo-600 hover:underline dark:text-indigo-400">
                Se connecter
              </Link>
            </p>

            <div className="mt-5 flex items-start justify-center gap-2 text-center text-xs text-slate-400">
              <Shield className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              <span>Vos données sont chiffrées et sécurisées.</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}