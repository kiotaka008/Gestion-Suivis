import { useState, type FormEvent } from 'react';
import { ApiError } from '../../services/ApiError';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import type { Project, Priority, ProjectStatus, User } from '../../types/models';

export interface CreateProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Création, ou modification quand `project` est fourni. */
  onCreate: (data: Partial<Project>) => Promise<void> | void;
  /** Projet à modifier : le formulaire passe en mode édition (le parent fournit une `key` par projet). */
  project?: Project;
  /** Chefs de projet possibles (mode édition) ; liste vide ou absente : champ non affiché. */
  owners?: User[];
}

interface FormState {
  name: string;
  description: string;
  priority: Priority;
  status: ProjectStatus;
  startDate: string;
  dueDate: string;
  ownerId: string;
}

function etatInitial(project?: Project): FormState {
  return {
    name: project?.name ?? '',
    description: project?.description ?? '',
    priority: project?.priority ?? 'medium',
    status: project?.status ?? 'active',
    startDate: project?.startDate ?? '',
    dueDate: project?.dueDate ?? '',
    ownerId: project?.ownerId ?? '',
  };
}

const PROJECT_STATUS_OPTIONS: { value: ProjectStatus; label: string }[] = [
  { value: 'active', label: 'En cours' },
  { value: 'on_hold', label: 'En attente' },
  { value: 'completed', label: 'Terminé' },
  { value: 'archived', label: 'Archivé' },
];

export function CreateProjectModal({ isOpen, onClose, onCreate, project, owners }: CreateProjectModalProps) {
  const isEdit = !!project;
  const [form, setForm] = useState<FormState>(() => etatInitial(project));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = () => {
    if (isSubmitting) return;
    setForm(etatInitial(project));
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
    if (form.startDate && form.dueDate && form.dueDate < form.startDate) {
      setError("L'échéance doit être postérieure ou égale à la date de début.");
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEdit) {
        await onCreate({
          name: form.name.trim(),
          // Chaîne vide : le backend efface la description.
          description: form.description.trim(),
          priority: form.priority,
          status: form.status,
          startDate: form.startDate || undefined,
          dueDate: form.dueDate || undefined,
          ownerId: form.ownerId && form.ownerId !== project.ownerId ? form.ownerId : undefined,
        });
      } else {
        await onCreate({
          name: form.name.trim(),
          description: form.description.trim() || undefined,
          priority: form.priority,
          startDate: form.startDate || undefined,
          dueDate: form.dueDate || undefined,
          status: 'active',
          progress: 0,
        });
        setForm(etatInitial());
      }
      onClose();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : isEdit
          ? 'Impossible de modifier le projet. Veuillez réessayer.'
          : 'Impossible de créer le projet. Veuillez réessayer.'
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const formId = isEdit ? 'edit-project-form' : 'create-project-form';

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={isEdit ? 'Modifier le projet' : 'Nouveau projet'}
      description={isEdit ? 'Mettez à jour les informations du projet.' : 'Remplissez les informations du projet.'}
      footer={
        <>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Annuler
          </Button>
          <Button type="submit" form={formId} isLoading={isSubmitting}>
            {isEdit ? 'Enregistrer' : 'Créer le projet'}
          </Button>
        </>
      }
    >
      {error && (
        <Alert variant="danger" className="mb-4">
          {error}
        </Alert>
      )}

      <form id={formId} onSubmit={handleSubmit} className="space-y-4" noValidate>
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

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Priorité"
            name="priority"
            value={form.priority}
            onChange={(e) => setForm({ ...form, priority: e.target.value as Priority })}
            options={[
              { value: 'low', label: 'Basse' },
              { value: 'medium', label: 'Moyenne' },
              { value: 'high', label: 'Haute' },
              { value: 'urgent', label: 'Urgent' },
            ]}
          />

          {isEdit && (
            <Select
              label="Statut"
              name="status"
              value={form.status}
              onChange={(e) => setForm({ ...form, status: e.target.value as ProjectStatus })}
              options={PROJECT_STATUS_OPTIONS}
            />
          )}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Date de début"
            name="startDate"
            type="date"
            value={form.startDate}
            onChange={(e) => setForm({ ...form, startDate: e.target.value })}
          />
          <Input
            label="Échéance"
            name="dueDate"
            type="date"
            value={form.dueDate}
            onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
          />
        </div>

        {isEdit && owners && owners.length > 0 && (
          <Select
            label="Chef de projet"
            name="ownerId"
            value={form.ownerId}
            onChange={(e) => setForm({ ...form, ownerId: e.target.value })}
            options={owners
              .filter((o) => o.isActive || o.id === project.ownerId)
              .map((o) => ({ value: o.id, label: o.name }))}
          />
        )}
      </form>
    </Modal>
  );
}
