import { useState, type FormEvent } from 'react';
import { ApiError } from '../../services/ApiError';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import type { Activity, ActivityStatus, Priority, Project, User } from '../../types/models';

export interface CreateActivityModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Création, ou modification quand `activity` est fourni. */
  onCreate: (data: Partial<Activity>) => Promise<void> | void;
  projects: Project[];
  defaultProjectId?: string;
  /** Activité à modifier (le parent fournit une `key` par activité). */
  activity?: Activity;
  /**
   * Modification limitée au suivi (statut et progression) : cas du responsable de
   * l'activité qui ne gère pas le projet. Les autres champs sont refusés par le backend.
   */
  trackOnly?: boolean;
  /** Responsables possibles ; liste vide ou absente : champ non affiché. */
  assignees?: User[];
}

interface FormState {
  title: string;
  description: string;
  projectId: string;
  priority: Priority;
  status: ActivityStatus;
  progress: string;
  assigneeId: string;
  startDate: string;
  dueDate: string;
}

const ACTIVITY_STATUS_OPTIONS: { value: ActivityStatus; label: string }[] = [
  { value: 'todo', label: 'À faire' },
  { value: 'in_progress', label: 'En cours' },
  { value: 'blocked', label: 'Bloquée' },
  { value: 'done', label: 'Terminée' },
];

const PRIORITY_OPTIONS = [
  { value: 'low', label: 'Basse' },
  { value: 'medium', label: 'Moyenne' },
  { value: 'high', label: 'Haute' },
  { value: 'urgent', label: 'Urgent' },
];

function etatInitial(activity?: Activity, defaultProjectId?: string): FormState {
  return {
    title: activity?.title ?? '',
    description: activity?.description ?? '',
    projectId: activity?.projectId ?? defaultProjectId ?? '',
    priority: activity?.priority ?? 'medium',
    status: activity?.status ?? 'todo',
    progress: String(activity?.progress ?? 0),
    assigneeId: activity?.assigneeId ?? '',
    startDate: activity?.startDate ?? '',
    dueDate: activity?.dueDate ?? '',
  };
}

export function CreateActivityModal({
  isOpen,
  onClose,
  onCreate,
  projects,
  defaultProjectId,
  activity,
  trackOnly = false,
  assignees,
}: CreateActivityModalProps) {
  const isEdit = !!activity;
  const [form, setForm] = useState<FormState>(() => etatInitial(activity, defaultProjectId));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Projets ouverts uniquement : le backend refuse une activité dans un projet terminé ou archivé.
  const projetsOuverts = projects.filter((p) => p.status !== 'completed' && p.status !== 'archived');
  // Les projets peuvent arriver après l'ouverture de la page : premier projet ouvert par défaut.
  const projetChoisi = form.projectId || defaultProjectId || projetsOuverts[0]?.id || '';
  const avecResponsable = !trackOnly && !!assignees && assignees.length > 0;

  const handleClose = () => {
    if (isSubmitting) return;
    setForm(etatInitial(activity, defaultProjectId));
    setError(null);
    onClose();
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);

    const progression = Number(form.progress);
    if (!Number.isInteger(progression) || progression < 0 || progression > 100) {
      setError('La progression doit être un nombre entier entre 0 et 100.');
      return;
    }
    if (!trackOnly) {
      if (!form.title.trim()) {
        setError("Le titre de l'activité est obligatoire.");
        return;
      }
      if (!isEdit && !projetChoisi) {
        setError('Veuillez sélectionner un projet en cours.');
        return;
      }
      if (form.startDate && form.dueDate && form.dueDate < form.startDate) {
        setError("L'échéance doit être postérieure ou égale à la date de début.");
        return;
      }
    }

    setIsSubmitting(true);
    try {
      if (trackOnly) {
        await onCreate({ status: form.status, progress: progression });
      } else if (isEdit) {
        await onCreate({
          title: form.title.trim(),
          // Chaîne vide : le backend efface la description.
          description: form.description.trim(),
          priority: form.priority,
          status: form.status,
          progress: progression,
          startDate: form.startDate || undefined,
          dueDate: form.dueDate || undefined,
          assigneeId: form.assigneeId && form.assigneeId !== activity.assigneeId ? form.assigneeId : undefined,
        });
      } else {
        await onCreate({
          title: form.title.trim(),
          description: form.description.trim() || undefined,
          projectId: projetChoisi,
          priority: form.priority,
          startDate: form.startDate || undefined,
          dueDate: form.dueDate || undefined,
          assigneeId: form.assigneeId || undefined,
          status: 'todo',
          progress: 0,
        });
        setForm(etatInitial(undefined, defaultProjectId));
      }
      onClose();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : isEdit
          ? "Impossible de modifier l'activité. Veuillez réessayer."
          : "Impossible de créer l'activité. Veuillez réessayer."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const formId = isEdit ? 'edit-activity-form' : 'create-activity-form';
  const titre = trackOnly ? "Mettre à jour l'avancement" : isEdit ? "Modifier l'activité" : 'Nouvelle activité';

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={titre}
      description={
        trackOnly
          ? `Activité « ${activity?.title ?? ''} » : statut et progression.`
          : isEdit
          ? "Mettez à jour les informations de l'activité."
          : "Remplissez les informations de l'activité."
      }
      footer={
        <>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Annuler
          </Button>
          <Button type="submit" form={formId} isLoading={isSubmitting}>
            {isEdit ? 'Enregistrer' : "Créer l'activité"}
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
        {!trackOnly && (
          <Input
            label="Titre de l'activité"
            name="title"
            placeholder="Ex : Refonte de la page d'accueil"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            required
          />
        )}

        {!isEdit && (
          projetsOuverts.length > 0 ? (
            <Select
              label="Projet"
              name="projectId"
              value={projetChoisi}
              onChange={(e) => setForm({ ...form, projectId: e.target.value })}
              options={projetsOuverts.map((p) => ({ value: p.id, label: p.name }))}
            />
          ) : (
            <Alert variant="warning">
              Aucun projet en cours sur lequel vous pouvez ajouter une activité.
            </Alert>
          )
        )}

        {!trackOnly && (
          <Textarea
            label="Description"
            name="description"
            placeholder="Décrivez l'activité..."
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        )}

        {(isEdit || trackOnly) && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label="Statut"
              name="status"
              value={form.status}
              onChange={(e) => setForm({ ...form, status: e.target.value as ActivityStatus })}
              options={ACTIVITY_STATUS_OPTIONS}
            />
            <Input
              label="Progression (%)"
              name="progress"
              type="number"
              min={0}
              max={100}
              step={5}
              value={form.progress}
              onChange={(e) => setForm({ ...form, progress: e.target.value })}
            />
          </div>
        )}

        {!trackOnly && (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <Select
                label="Priorité"
                name="priority"
                value={form.priority}
                onChange={(e) => setForm({ ...form, priority: e.target.value as Priority })}
                options={PRIORITY_OPTIONS}
              />
              {avecResponsable && (
                <Select
                  label="Responsable"
                  name="assigneeId"
                  value={form.assigneeId}
                  onChange={(e) => setForm({ ...form, assigneeId: e.target.value })}
                  options={[
                    // Le backend ne permet pas de retirer un responsable : « non affectée » seulement à la création
                    // ou tant qu'aucun responsable n'est défini.
                    ...(!activity?.assigneeId ? [{ value: '', label: 'Non affectée' }] : []),
                    ...assignees
                      .filter((a) => a.isActive || a.id === activity?.assigneeId)
                      .map((a) => ({ value: a.id, label: a.name })),
                  ]}
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
          </>
        )}
      </form>
    </Modal>
  );
}
