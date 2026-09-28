import { useState, type FormEvent } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import type { Project, Priority } from '../../types/models';

export interface CreateProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (data: Partial<Project>) => Promise<void> | void;
}

interface FormState {
  name: string;
  description: string;
  priority: Priority;
  dueDate: string;
}

const initialState: FormState = {
  name: '',
  description: '',
  priority: 'medium',
  dueDate: '',
};

export function CreateProjectModal({ isOpen, onClose, onCreate }: CreateProjectModalProps) {
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
      setError('Le nom du projet est obligatoire.');
      return;
    }

    setIsSubmitting(true);
    try {
      await onCreate({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        priority: form.priority,
        dueDate: form.dueDate || undefined,
        status: 'active',
        progress: 0,
      });
      setForm(initialState);
      onClose();
    } catch {
      setError('Impossible de créer le projet. Veuillez réessayer.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Nouveau projet"
      description="Remplissez les informations du projet."
      footer={
        <>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Annuler
          </Button>
          <Button
            type="submit"
            form="create-project-form"
            isLoading={isSubmitting}
          >
            Créer le projet
          </Button>
        </>
      }
    >
      {error && (
        <Alert variant="danger" className="mb-4">
          {error}
        </Alert>
      )}

      <form id="create-project-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Input
          label="Nom du projet"
          name="name"
          placeholder="Ex : Refonte du site web"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />

        <Textarea
          label="Description"
          name="description"
          placeholder="Décrivez brièvement le projet..."
          value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
        />

        <Select
          label="Priorité"
          value={form.priority}
          onChange={(e) => setForm({ ...form, priority: e.target.value as Priority })}
          options={[
            { value: 'low', label: 'Basse' },
            { value: 'medium', label: 'Moyenne' },
            { value: 'high', label: 'Haute' },
            { value: 'urgent', label: 'Urgent' },
          ]}
        />

        <Input
          label="Échéance"
          name="dueDate"
          type="date"
          value={form.dueDate}
          onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
        />
      </form>
    </Modal>
  );
}