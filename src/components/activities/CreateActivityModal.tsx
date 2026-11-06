import { useState, type FormEvent } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import type { Activity, Priority, Project } from '../../types/models';

export interface CreateActivityModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (data: Partial<Activity>) => Promise<void> | void;
  projects: Project[];
  defaultProjectId?: string;
}

interface FormState {
  title: string;
  description: string;
  projectId: string;
  priority: Priority;
  dueDate: string;
}

export function CreateActivityModal({
  isOpen,
  onClose,
  onCreate,
  projects,
  defaultProjectId,
}: CreateActivityModalProps) {
  const initialState: FormState = {
    title: '',
    description: '',
    projectId: defaultProjectId ?? projects[0]?.id ?? '',
    priority: 'medium',
    dueDate: '',
  };

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

    if (!form.title.trim()) {
      setError("Le titre de l'activité est obligatoire.");
      return;
    }
    if (!form.projectId) {
      setError('Veuillez sélectionner un projet.');
      return;
    }

    setIsSubmitting(true);
    try {
      await onCreate({
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        projectId: form.projectId,
        priority: form.priority,
        dueDate: form.dueDate || undefined,
        status: 'todo',
        progress: 0,
      });
      setForm(initialState);
      onClose();
    } catch {
      setError("Impossible de créer l'activité. Veuillez réessayer.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Nouvelle activité"
      description="Remplissez les informations de l'activité."
      footer={
        <>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Annuler
          </Button>
          <Button type="submit" form="create-activity-form" isLoading={isSubmitting}>
            Créer l'activité
          </Button>
        </>
      }
    >
      {error && (
        <Alert variant="danger" className="mb-4">
          {error}
        </Alert>
      )}

      <form id="create-activity-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Input
          label="Titre de l'activité"
          name="title"
          placeholder="Ex : Refonte de la page d'accueil"
          value={form.title}
          onChange={(e) => setForm({ ...form, title: e.target.value })}
          required
        />

        <Select
          label="Projet"
          value={form.projectId}
          onChange={(e) => setForm({ ...form, projectId: e.target.value })}
          options={projects.map((p) => ({ value: p.id, label: p.name }))}
        />

        <Textarea
          label="Description"
          name="description"
          placeholder="Décrivez l'activité..."
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