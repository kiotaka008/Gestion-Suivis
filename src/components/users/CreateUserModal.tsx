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

export interface CreateUserModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (data: CreateUserPayload) => Promise<void> | void;
  /** Utilisateur à modifier (nom, email, rôle) ; le parent fournit une `key` par utilisateur. */
  user?: User;
  /** Enregistrement d'une modification (mode édition). */
  onUpdate?: (data: Pick<User, 'name' | 'email' | 'role'>) => Promise<void> | void;
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

export function CreateUserModal({ isOpen, onClose, onCreate, user, onUpdate }: CreateUserModalProps) {
  const isEdit = !!user;
  const [form, setForm] = useState<FormState>(() => etatInitial(user));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = () => {
    if (isSubmitting) return;
    setForm(etatInitial(user));
    setError(null);
    onClose();
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

    setIsSubmitting(true);
    try {
      if (isEdit) {
        await onUpdate?.({ name: form.name.trim(), email: form.email.trim().toLowerCase(), role: form.role });
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
      onClose();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : isEdit
          ? "Impossible de modifier l'utilisateur. Veuillez réessayer."
          : "Impossible de créer l'utilisateur. Veuillez réessayer."
      );
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