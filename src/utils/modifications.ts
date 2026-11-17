/**
 * Comparaison des champs d'un formulaire de modification (AUD-002).
 *
 * Un formulaire garde une « référence » : les valeurs lues à son ouverture. À l'envoi, seuls les
 * champs dont la valeur actuelle diffère de cette référence sont transmis au backend (PATCH :
 * un champ absent n'est pas modifié). Un champ que l'utilisateur n'a pas touché n'est donc jamais
 * renvoyé et ne peut pas écraser une modification faite entre-temps par quelqu'un d'autre.
 *
 * La comparaison porte sur des valeurs normalisées, pour qu'une différence sans effet (espaces,
 * casse d'un email, « 60 » contre 60, date vide contre absente) ne compte pas comme une modification.
 */

/** D2 : le backend ne sait pas encore effacer une date (une valeur absente signifie « inchangé »). */
export const MESSAGE_DATE_NON_EFFACABLE = "L'effacement d'une date n'est pas encore pris en charge.";

/** D2 : le backend ne sait pas encore retirer le responsable d'une activité. */
export const MESSAGE_RESPONSABLE_NON_RETIRABLE = "Le retrait du responsable n'est pas encore pris en charge.";

/** Transforme une valeur de formulaire en valeur comparable. */
export type Normaliseur = (valeur: unknown) => unknown;

/** Normalisations disponibles. */
export const normaliser = {
  /** Texte : espaces de début et de fin ignorés ; absent (null, undefined) équivaut à vide. */
  texte: (valeur: unknown): unknown => (valeur == null ? '' : typeof valeur === 'string' ? valeur.trim() : valeur),
  /** Email : espaces ignorés et casse ignorée (le backend enregistre l'email en minuscules). */
  email: (valeur: unknown): unknown =>
    valeur == null ? '' : typeof valeur === 'string' ? valeur.trim().toLowerCase() : valeur,
  /** Nombre : « 60 » et 60 sont égaux ; vide ou absent ne vaut pas 0. */
  nombre: (valeur: unknown): unknown => {
    if (valeur == null || (typeof valeur === 'string' && valeur.trim() === '')) return null;
    return Number(valeur);
  },
  /** Date (AAAA-MM-JJ) : vide et absente sont égales. */
  date: (valeur: unknown): unknown => (valeur == null ? '' : typeof valeur === 'string' ? valeur.trim() : valeur),
  /** Valeur exacte (statut, priorité, rôle, identifiant) ; absent équivaut à vide. */
  exacte: (valeur: unknown): unknown => (valeur == null ? '' : valeur),
};

/**
 * Noms des champs de `actuel` dont la valeur normalisée diffère de celle de `reference`,
 * dans l'ordre des clés de `actuel`. Sans normaliseur indiqué, un champ est comparé tel quel
 * ({@link normaliser.exacte}).
 */
export function champsModifies<T extends object>(
  reference: T,
  actuel: T,
  normaliseurs: Partial<Record<keyof T, Normaliseur>> = {}
): Array<keyof T> {
  return (Object.keys(actuel) as Array<keyof T>).filter((cle) => {
    const normaliserChamp = normaliseurs[cle] ?? normaliser.exacte;
    return !Object.is(normaliserChamp(reference[cle]), normaliserChamp(actuel[cle]));
  });
}

/**
 * Vrai si l'utilisateur a vidé un champ qui avait une valeur : le backend ne sait pas encore
 * effacer une date ni retirer un responsable (une valeur absente signifie « inchangé »),
 * la soumission doit alors être bloquée avec un message explicite.
 */
export function aEteVide(valeurReference: unknown, valeurActuelle: unknown): boolean {
  return normaliser.texte(valeurReference) !== '' && normaliser.texte(valeurActuelle) === '';
}
