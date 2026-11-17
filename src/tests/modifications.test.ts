import { describe, it, expect } from 'vitest';
import { aEteVide, champsModifies, normaliser } from '../utils/modifications';

// AUD-002 : seuls les champs réellement modifiés sont envoyés dans les requêtes de modification.
describe('champsModifies', () => {
  it('aucune modification : liste vide', () => {
    const reference = { nom: 'Budget', statut: 'active', avancement: '40' };
    expect(champsModifies(reference, { ...reference })).toEqual([]);
  });

  it('renvoie seulement les champs modifiés, dans l’ordre des clés', () => {
    const reference = { nom: 'Budget', statut: 'active', priorite: 'medium' };
    expect(champsModifies(reference, { nom: 'Budget 2027', statut: 'active', priorite: 'high' })).toEqual([
      'nom',
      'priorite',
    ]);
  });

  it('espaces : ignorés avec le normaliseur texte, comptés sans normaliseur', () => {
    const reference = { titre: 'Saisie', code: 'A' };
    const actuel = { titre: '  Saisie  ', code: ' A' };
    expect(champsModifies(reference, actuel, { titre: normaliser.texte, code: normaliser.texte })).toEqual([]);
    expect(champsModifies(reference, actuel)).toEqual(['titre', 'code']);
  });

  it('texte absent et texte vide sont égaux ; texte vidé est une modification', () => {
    const n = { description: normaliser.texte };
    expect(champsModifies({ description: undefined as string | undefined }, { description: '' }, n)).toEqual([]);
    expect(champsModifies({ description: 'Ancienne' }, { description: '   ' }, n)).toEqual(['description']);
  });

  it('email : casse et espaces ignorés', () => {
    const n = { email: normaliser.email };
    expect(champsModifies({ email: 'alice@exemple.com' }, { email: '  Alice@Exemple.COM ' }, n)).toEqual([]);
    expect(champsModifies({ email: 'alice@exemple.com' }, { email: 'bob@exemple.com' }, n)).toEqual(['email']);
  });

  it('nombres : « 60 » et 60 égaux ; vide ne vaut pas 0', () => {
    const n = { avancement: normaliser.nombre };
    expect(champsModifies({ avancement: 60 as number | string }, { avancement: '60' }, n)).toEqual([]);
    expect(champsModifies({ avancement: '040' }, { avancement: '40' }, n)).toEqual([]);
    expect(champsModifies({ avancement: '40' }, { avancement: '80' }, n)).toEqual(['avancement']);
    expect(champsModifies({ avancement: 0 as number | string }, { avancement: '' }, n)).toEqual(['avancement']);
  });

  it('dates : vide et absente égales ; date changée ou vidée détectée', () => {
    const n = { echeance: normaliser.date };
    expect(champsModifies({ echeance: undefined as string | undefined }, { echeance: '' }, n)).toEqual([]);
    expect(champsModifies({ echeance: '2026-05-01' }, { echeance: '2026-05-01' }, n)).toEqual([]);
    expect(champsModifies({ echeance: '2026-05-01' }, { echeance: '2026-06-01' }, n)).toEqual(['echeance']);
    expect(champsModifies({ echeance: '2026-05-01' }, { echeance: '' }, n)).toEqual(['echeance']);
  });

  it('valeurs exactes (rôle, statut) : toute différence compte, la casse aussi', () => {
    expect(champsModifies({ role: 'admin' }, { role: 'admin' })).toEqual([]);
    expect(champsModifies({ role: 'admin' }, { role: 'member' })).toEqual(['role']);
    expect(champsModifies({ statut: 'done' }, { statut: 'Done' })).toEqual(['statut']);
  });
});

describe('aEteVide', () => {
  it('vrai seulement si une valeur existante a été vidée', () => {
    expect(aEteVide('2026-05-01', '')).toBe(true);
    expect(aEteVide('u1', '  ')).toBe(true);
    expect(aEteVide('', '')).toBe(false);
    expect(aEteVide(undefined, '')).toBe(false);
    expect(aEteVide('2026-05-01', '2026-06-01')).toBe(false);
    expect(aEteVide('', '2026-06-01')).toBe(false);
  });
});
