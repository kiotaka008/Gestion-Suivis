import { useState, type FormEvent } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import { Mail, User as UserIcon } from 'lucide-react';
import type { User, Role } from '../../types/models';

export interface CreateUserModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (data: Partial<User>) => Promise<void> | void;
}

interface FormState {
  name: string;
  email: string;
  role: Role;
}

const initialState: FormState = {
  name: '',
  email: '',
  role: 'member',
};

export function CreateUserModal({ isOpen, onClose, onCreate }: CreateUserModalProps) {
  const [form, setForm] = useState<FormState>(initialState);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = () => {
    if (isSubmitting) return;
    setForm(initialState);
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

    setIsSubmitting(true);
    try {
      await onCreate({
        name: form.name.trim(),
        email: form.email.trim().toLowerCase(),
        role: form.role,
        isActive: true,
      });
      setForm(initialState);
      onClose();
    } catch {
      setError("Impossible de créer l'utilisateur. Veuillez réessayer.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Nouvel utilisateur"
      description="Ajoutez un membre à votre organisation."
      footer={
        <>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Annuler
          </Button>
          <Button type="submit" form="create-user-form" isLoading={isSubmitting}>
            Ajouter l'utilisateur
          </Button>
        </>
      }
    >
      {error && (
        <Alert variant="danger" className="mb-4">
          {error}
        </Alert>
      )}

      <form id="create-user-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
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

        <Select
          label="Rôle"
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