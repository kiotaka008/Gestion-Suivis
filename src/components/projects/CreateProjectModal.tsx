import { useState, type FormEvent } from 'react';
import { ApiError } from '../../services/ApiError';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import type { Project, Priority, ProjectStatus, User } from '../../types/models';
import { aEteVide, champsModifies, MESSAGE_DATE_NON_EFFACABLE, normaliser } from '../../utils/modifications';

export interface CreateProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
  /**
   * Création, ou modification quand `project` est fourni. En modification, seuls les champs
   * modifiés sont transmis ; le statut n'y figure que s'il a été changé (AUD-002, F3).
   */
  onCreate: (data: Partial<Project>) => Promise<void> | void;
  /** Projet à modifier : le formulaire passe en mode édition (le parent fournit une `key` par projet). */
  project?: Project;
  /** Chefs de projet possibles (mode édition) ; liste vide ou absente : champ non affiché. */
  owners?: User[];
  /**
   * Conflit 409 en modification : le parent relit le projet et met à jour `project`.
   * La saisie n'est jamais remplacée automatiquement (bouton « Recharger les valeurs actuelles »).
   */
  onConflit?: () => Promise<void>;
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

export function CreateProjectModal({ isOpen, onClose, onCreate, project, owners, onConflit }: CreateProjectModalProps) {
  const isEdit = !!project;
  const [form, setForm] = useState<FormState>(() => etatInitial(project));
  // Valeurs lues à l'ouverture : la comparaison se fait toujours avec elles, jamais avec `project`,
  // qui peut être relu entre-temps (conflit 409).
  const [reference, setReference] = useState<FormState>(() => etatInitial(project));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflit, setConflit] = useState<'aucun' | 'rechargement' | 'pret' | 'echec'>('aucun');

  const handleClose = () => {
    if (isSubmitting) return;
    setForm(etatInitial(project));
    setReference(etatInitial(project));
    setError(null);
    setConflit('aucun');
    onClose();
  };

  /** Remplace la saisie par les valeurs actuelles, uniquement sur demande de l'utilisateur. */
  const rechargerValeursActuelles = () => {
    setForm(etatInitial(project));
    setReference(etatInitial(project));
    setError(null);
    setConflit('aucun');
  };

  /** Modification : uniquement les champs changés depuis l'ouverture (jamais l'avancement). */
  const donneesModifiees = (): Partial<Project> => {
    const modifies = champsModifies(
      { name: reference.name, description: reference.description, priority: reference.priority,
        status: reference.status, startDate: reference.startDate, dueDate: reference.dueDate },
      { name: form.name, description: form.description, priority: form.priority,
        status: form.status, startDate: form.startDate, dueDate: form.dueDate },
      { name: normaliser.texte, description: normaliser.texte, startDate: normaliser.date, dueDate: normaliser.date }
    );
    let donnees: Partial<Project> = {};
    if (modifies.includes('name')) donnees = { ...donnees, name: form.name.trim() };
    // Chaîne vide : le backend efface la description.
    if (modifies.includes('description')) donnees = { ...donnees, description: form.description.trim() };
    if (modifies.includes('priority')) donnees = { ...donnees, priority: form.priority };
    if (modifies.includes('status')) donnees = { ...donnees, status: form.status };
    if (modifies.includes('startDate')) donnees = { ...donnees, startDate: form.startDate };
    if (modifies.includes('dueDate')) donnees = { ...donnees, dueDate: form.dueDate };
    if (form.ownerId && form.ownerId !== reference.ownerId) donnees = { ...donnees, ownerId: form.ownerId };
    return donnees;
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

    let donnees: Partial<Project> = {};
    if (isEdit) {
      // D2 : une date vidée ne peut pas encore être effacée par le backend, on bloque explicitement.
      if (aEteVide(reference.startDate, form.startDate) || aEteVide(reference.dueDate, form.dueDate)) {
        setError(MESSAGE_DATE_NON_EFFACABLE);
        return;
      }
      donnees = donneesModifiees();
      // D1 : rien n'a changé, aucune requête.
      if (Object.keys(donnees).length === 0) {
        handleClose();
        return;
      }
    }

    setIsSubmitting(true);
    try {
      if (isEdit) {
        await onCreate(donnees);
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
      setConflit('aucun');
      onClose();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : isEdit
          ? 'Impossible de modifier le projet. Veuillez réessayer.'
          : 'Impossible de créer le projet. Veuillez réessayer.'
      );
      // Conflit 409 : la saisie est conservée ; le parent relit le projet.
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
