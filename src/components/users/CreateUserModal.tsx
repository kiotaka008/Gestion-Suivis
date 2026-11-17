import { useState, type FormEvent } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import { Lock, Mail, User as UserIcon } from 'lucide-react';
import type { Role, User } from '../../types/models';
import type { CreateUserPayload } from '../../services/userService';
import { ApiError } from '../../services/ApiError';
import { champsModifies, normaliser } from '../../utils/modifications';

/** Champs modifiables d'un utilisateur : seuls ceux réellement modifiés sont envoyés. */
export type UserUpdatePayload = Partial<Pick<User, 'name' | 'email' | 'role'>>;

export interface CreateUserModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (data: CreateUserPayload) => Promise<void> | void;
  /** Utilisateur à modifier (nom, email, rôle) ; le parent fournit une `key` par utilisateur. */
  user?: User;
  /**
   * Enregistrement d'une modification (mode édition) : ne contient que les champs modifiés.
   * Le rôle n'y figure que s'il a été explicitement changé (AUD-002, F2).
   */
  onUpdate?: (data: UserUpdatePayload) => Promise<void> | void;
  /**
   * Conflit 409 en modification : le parent relit l'utilisateur et met à jour `user`.
   * La saisie n'est jamais remplacée automatiquement (bouton « Recharger les valeurs actuelles »).
   */
  onConflit?: () => Promise<void>;
}

interface FormState {
  name: string;
  email: string;
  role: Role;
  password: string;
}

function etatInitial(user?: User): FormState {
  return { name: user?.name ?? '', email: user?.email ?? '', role: user?.role ?? 'member', password: '' };
}

export function CreateUserModal({ isOpen, onClose, onCreate, user, onUpdate, onConflit }: CreateUserModalProps) {
  const isEdit = !!user;
  const [form, setForm] = useState<FormState>(() => etatInitial(user));
  // Valeurs lues à l'ouverture : la comparaison se fait toujours avec elles, jamais avec `user`,
  // qui peut être relu entre-temps (conflit 409).
  const [reference, setReference] = useState<FormState>(() => etatInitial(user));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflit, setConflit] = useState<'aucun' | 'rechargement' | 'pret' | 'echec'>('aucun');

  const handleClose = () => {
    if (isSubmitting) return;
    setForm(etatInitial(user));
    setReference(etatInitial(user));
    setError(null);
    setConflit('aucun');
    onClose();
  };

  /** Remplace la saisie par les valeurs actuelles, uniquement sur demande de l'utilisateur. */
  const rechargerValeursActuelles = () => {
    setForm(etatInitial(user));
    setReference(etatInitial(user));
    setError(null);
    setConflit('aucun');
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);

    if (!form.name.trim()) {
      setError('Le nom est obligatoire.');
      return;
    }
    if (!form.email.trim() || !form.email.includes('@')) {
      setError('Veuillez saisir un email valide.');
      return;
    }
    if (!isEdit && form.password.length < 8) {
      setError('Le mot de passe initial doit contenir au moins 8 caractères.');
      return;
    }

    // Modification : seuls les champs réellement modifiés sont envoyés (le rôle compris).
    let donnees: UserUpdatePayload = {};
    if (isEdit) {
      const modifies = champsModifies(
        { name: reference.name, email: reference.email, role: reference.role },
        { name: form.name, email: form.email, role: form.role },
        { name: normaliser.texte, email: normaliser.email }
      );
      if (modifies.length === 0) {
        handleClose();
        return;
      }
      if (modifies.includes('name')) donnees = { ...donnees, name: form.name.trim() };
      if (modifies.includes('email')) donnees = { ...donnees, email: form.email.trim().toLowerCase() };
      if (modifies.includes('role')) donnees = { ...donnees, role: form.role };
    }

    setIsSubmitting(true);
    try {
      if (isEdit) {
        await onUpdate?.(donnees);
      } else {
        await onCreate({
          name: form.name.trim(),
          email: form.email.trim().toLowerCase(),
          role: form.role,
          password: form.password,
          isActive: true,
        });
        setForm(etatInitial());
      }
      setConflit('aucun');
      onClose();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : isEdit
          ? "Impossible de modifier l'utilisateur. Veuillez réessayer."
          : "Impossible de créer l'utilisateur. Veuillez réessayer."
      );
      // Conflit 409 : la saisie est conservée ; le parent relit l'utilisateur.
      if (isEdit && err instanceof ApiError && err.status === 409 && onConflit) {
        setConflit('rechargement');
        try {
          await onConflit();
          setConflit('pret');
        } catch {
          setConflit('echec');
        }
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={isEdit ? "Modifier l'utilisateur" : 'Nouvel utilisateur'}
      description={isEdit ? 'Nom, email et rôle du compte.' : 'Ajoutez un membre à votre organisation.'}
      footer={
        <>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Annuler
          </Button>
          <Button type="submit" form={isEdit ? 'edit-user-form' : 'create-user-form'} isLoading={isSubmitting}>
            {isEdit ? 'Enregistrer' : "Ajouter l'utilisateur"}
          </Button>
        </>
      }
    >
      {error && (
        <Alert variant="danger" className="mb-4">
          {error}
        </Alert>
      )}

      {conflit !== 'aucun' && (
        <Alert variant="warning" className="mb-4">
          <p>Les données ont pu être modifiées entre-temps. Votre saisie est conservée.</p>
          {conflit === 'echec' && <p className="mt-1">Impossible de relire les valeurs actuelles.</p>}
          {conflit === 'pret' && (
            <Button type="button" size="sm" variant="outline" className="mt-2" onClick={rechargerValeursActuelles}>
              Recharger les valeurs actuelles
            </Button>
          )}
        </Alert>
      )}

      <form id={isEdit ? 'edit-user-form' : 'create-user-form'} onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Input
          label="Nom complet"
          name="name"
          placeholder="Ex : Miora Rakotoarisoa"
          leftIcon={<UserIcon className="h-4 w-4" />}
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />

        <Input
          label="Email"
          name="email"
          type="email"
          placeholder="exemple@company.com"
          leftIcon={<Mail className="h-4 w-4" />}
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
          required
        />

        {!isEdit && (
          <Input
            label="Mot de passe initial"
            name="password"
            isPassword
            autoComplete="new-password"
            placeholder="8 caractères minimum"
            leftIcon={<Lock className="h-4 w-4" />}
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            required
          />
        )}

        <Select
          label="Rôle"
          name="role"
          value={form.role}
          onChange={(e) => setForm({ ...form, role: e.target.value as Role })}
          options={[
            { value: 'member', label: 'Membre' },
            { value: 'manager', label: 'Responsable' },
            { value: 'admin', label: 'Administrateur' },
          ]}
        />
      </form>
    </Modal>
  );
}