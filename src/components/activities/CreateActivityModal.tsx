import { useState, type FormEvent } from 'react';
import { ApiError } from '../../services/ApiError';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import type { Activity, ActivityStatus, Priority, Project, User } from '../../types/models';
import {
  aEteVide,
  champsModifies,
  MESSAGE_DATE_NON_EFFACABLE,
  MESSAGE_RESPONSABLE_NON_RETIRABLE,
  normaliser,
} from '../../utils/modifications';

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
  /**
   * Conflit 409 en modification : le parent relit l'activité (et son projet) et met à jour `activity`.
   * La saisie n'est jamais remplacée automatiquement (bouton « Recharger les valeurs actuelles »).
   */
  onConflit?: () => Promise<void>;
  /**
   * AUD-002, F6 : projet clôturé pendant la modification (raison affichée). La saisie reste
   * visible en lecture seule pour pouvoir être copiée, mais ne peut plus être enregistrée.
   * Sans effet en création.
   */
  verrou?: string;
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
  onConflit,
  verrou,
}: CreateActivityModalProps) {
  const isEdit = !!activity;
  const verrouille = isEdit && !!verrou;
  const [form, setForm] = useState<FormState>(() => etatInitial(activity, defaultProjectId));
  // Valeurs lues à l'ouverture : la comparaison se fait toujours avec elles, jamais avec `activity`,
  // qui peut être relue entre-temps (conflit 409).
  const [reference, setReference] = useState<FormState>(() => etatInitial(activity, defaultProjectId));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflit, setConflit] = useState<'aucun' | 'rechargement' | 'pret' | 'echec'>('aucun');

  // Projets ouverts uniquement : le backend refuse une activité dans un projet terminé ou archivé.
  const projetsOuverts = projects.filter((p) => p.status !== 'completed' && p.status !== 'archived');
  // Les projets peuvent arriver après l'ouverture de la page : premier projet ouvert par défaut.
  const projetChoisi = form.projectId || defaultProjectId || projetsOuverts[0]?.id || '';
  const avecResponsable = !trackOnly && !!assignees && assignees.length > 0;

  const handleClose = () => {
    if (isSubmitting) return;
    setForm(etatInitial(activity, defaultProjectId));
    setReference(etatInitial(activity, defaultProjectId));
    setError(null);
    setConflit('aucun');
    onClose();
  };

  /** Remplace la saisie par les valeurs actuelles, uniquement sur demande de l'utilisateur. */
  const rechargerValeursActuelles = () => {
    setForm(etatInitial(activity, defaultProjectId));
    setReference(etatInitial(activity, defaultProjectId));
    setError(null);
    setConflit('aucun');
  };

  /** Suivi seul (AUD-002, F4) : uniquement le statut et/ou la progression réellement modifiés. */
  const donneesSuivi = (progression: number): Partial<Activity> => {
    const modifies = champsModifies(
      { status: reference.status, progress: reference.progress },
      { status: form.status, progress: form.progress },
      { progress: normaliser.nombre }
    );
    let donnees: Partial<Activity> = {};
    if (modifies.includes('status')) donnees = { ...donnees, status: form.status };
    if (modifies.includes('progress')) donnees = { ...donnees, progress: progression };
    return donnees;
  };

  /** Modification complète (AUD-002, F1) : uniquement les champs changés depuis l'ouverture. */
  const donneesModifiees = (progression: number): Partial<Activity> => {
    const modifies = champsModifies(
      { title: reference.title, description: reference.description, priority: reference.priority,
        status: reference.status, progress: reference.progress, startDate: reference.startDate,
        dueDate: reference.dueDate },
      { title: form.title, description: form.description, priority: form.priority,
        status: form.status, progress: form.progress, startDate: form.startDate, dueDate: form.dueDate },
      { title: normaliser.texte, description: normaliser.texte, progress: normaliser.nombre,
        startDate: normaliser.date, dueDate: normaliser.date }
    );
    let donnees: Partial<Activity> = {};
    if (modifies.includes('title')) donnees = { ...donnees, title: form.title.trim() };
    // Chaîne vide : le backend efface la description.
    if (modifies.includes('description')) donnees = { ...donnees, description: form.description.trim() };
    if (modifies.includes('priority')) donnees = { ...donnees, priority: form.priority };
    if (modifies.includes('status')) donnees = { ...donnees, status: form.status };
    if (modifies.includes('progress')) donnees = { ...donnees, progress: progression };
    if (modifies.includes('startDate')) donnees = { ...donnees, startDate: form.startDate };
    if (modifies.includes('dueDate')) donnees = { ...donnees, dueDate: form.dueDate };
    // Responsable : envoyé seulement s'il change (règle existante), comparé à la valeur d'ouverture.
    if (form.assigneeId && form.assigneeId !== reference.assigneeId) {
      donnees = { ...donnees, assigneeId: form.assigneeId };
    }
    return donnees;
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    // AUD-002, F6 : projet clôturé, aucun enregistrement possible, quelle que soit l'origine de l'envoi.
    if (verrouille) return;
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

    let donnees: Partial<Activity> = {};
    if (isEdit && !trackOnly) {
      // D2 : date vidée ou responsable retiré, non pris en charge par le backend : on bloque explicitement.
      if (aEteVide(reference.startDate, form.startDate) || aEteVide(reference.dueDate, form.dueDate)) {
        setError(MESSAGE_DATE_NON_EFFACABLE);
        return;
      }
      if (aEteVide(reference.assigneeId, form.assigneeId)) {
        setError(MESSAGE_RESPONSABLE_NON_RETIRABLE);
        return;
      }
    }
    if (isEdit) {
      donnees = trackOnly ? donneesSuivi(progression) : donneesModifiees(progression);
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
      setConflit('aucun');
      onClose();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : isEdit
          ? "Impossible de modifier l'activité. Veuillez réessayer."
          : "Impossible de créer l'activité. Veuillez réessayer."
      );
      // Conflit 409 : la saisie est conservée ; le parent relit l'activité et son projet.
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
            {verrouille ? 'Fermer' : 'Annuler'}
          </Button>
          <Button type="submit" form={formId} isLoading={isSubmitting} disabled={verrouille}>
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

      {verrouille && (
        <Alert variant="warning" className="mb-4">
          <p>{verrou}</p>
          <p className="mt-1">
            Votre saisie reste affichée pour que vous puissiez la copier, mais elle ne peut plus être enregistrée.
          </p>
        </Alert>
      )}

      {/* Projet clôturé : pas de rechargement proposé, aucune modification n'est plus possible. */}
      {conflit !== 'aucun' && !verrouille && (
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
        {!trackOnly && (
          <Input
            label="Titre de l'activité"
            name="title"
            placeholder="Ex : Refonte de la page d'accueil"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            readOnly={verrouille}
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
            readOnly={verrouille}
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
              // <select> n'accepte pas readOnly : désactivé, la valeur choisie reste affichée.
              disabled={verrouille}
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
              readOnly={verrouille}
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
                disabled={verrouille}
              />
              {avecResponsable && (
                <Select
                  label="Responsable"
                  name="assigneeId"
                  value={form.assigneeId}
                  onChange={(e) => setForm({ ...form, assigneeId: e.target.value })}
                  disabled={verrouille}
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
                readOnly={verrouille}
              />
              <Input
                label="Échéance"
                name="dueDate"
                type="date"
                value={form.dueDate}
                onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
                readOnly={verrouille}
              />
            </div>
          </>
        )}
      </form>
    </Modal>
  );
}
