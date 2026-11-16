# AUD-001 — Vérifications PostgreSQL préalables au rôle applicatif

Statut : **prêt à transmettre au membre responsable de PostgreSQL. Non exécuté.**
Aucun résultat n'a encore été reçu.

| Fichier | Rôle |
|---|---|
| `aud001_verifications.sql` | Script de vérification V0 à V8, en lecture seule (ce dossier) |
| `AUD-001_verifications_postgresql.md` | Ce document : contexte, conditions d'exécution, message au membre BDD |

## 1. Contexte

L'audit AUD-001 a constaté que le backend se connecte à PostgreSQL avec le compte `postgres`
(`backend/.env` et valeur de secours de `application.properties`). Un superutilisateur ignore la
Row-Level Security : l'isolation entre organisations ne repose alors que sur le filtre Hibernate
`@TenantId`.

La solution envisagée est un rôle applicatif dédié, `projectmanager_app`. **Sa création est une
étape ultérieure, qui ne fait pas partie de cette vérification.** Le script ne sert qu'à recueillir
les informations nécessaires pour la décider.

## 2. Ce que vérifie le script

| Section | Vérification |
|---|---|
| V0 | Garde : arrêt si la base n'est pas `projectmanager` ou si la transaction n'est pas en lecture seule ; informations de session (adresse, port, version), qui ne suffisent pas à elles seules à identifier l'instance |
| V0c | Facultatif : identifiant système de l'instance, comparé à la valeur relevée sur l'ordinateur du backend par le journal L1 ; « non disponible » sans faire échouer le script si la lecture est impossible |
| V0b | Le compte utilisé voit-il toutes les lignes de `audit_logs` ? Colonne `comptage_v8_exhaustif` : `t` pour un superutilisateur, un rôle `BYPASSRLS`, une table sans RLS activée, ou le propriétaire si la RLS n'est pas forcée |
| V1 | Attributs de `postgres`, rôles superutilisateurs ou `BYPASSRLS`, existence éventuelle de `projectmanager_app` |
| V2 et V3 | Les 10 tables : existence, propriétaire, RLS activée, RLS forcée ; tables supplémentaires ; propriétaires de la base et du schéma `public` |
| V4 | Politiques RLS ; tables avec RLS mais sans politique |
| V5 | Droits sur la base, le schéma `public`, les tables et les colonnes ; séquences ; extensions |
| V6 | Privilèges par défaut (`ALTER DEFAULT PRIVILEGES`) |
| V7 | Appartenances entre rôles, paramètres permanents, `app.current_organization_id`, `row_security` |
| V8 | Lignes de `audit_logs` sans organisation, et définition des clés étrangères (lien avec AUD-003) |

La vérification V9 (connexions en cours dans `pg_stat_activity`) n'est **pas demandée**. Si elle
devait l'être plus tard, elle ne serait lancée que si le backend tourne déjà : elle ne doit jamais
conduire à démarrer Spring Boot ni à ouvrir une connexion applicative.

## 3. Conditions d'exécution

- **Ordinateur obligatoire :** le backend de Gestion-Suivis se connecte à `localhost:5432` depuis
  l'ordinateur où se trouve `backend/.env` et où tourne le backend. `localhost` désigne toujours la
  machine qui exécute la commande : **psql doit donc être lancé sur ce même ordinateur**, avec
  `-h localhost -p 5432` explicitement.
  Ne **jamais** lancer cette vérification depuis l'ordinateur personnel d'un autre membre pour
  prétendre vérifier l'instance du backend : on interrogerait une autre instance.
  Ne pas ouvrir PostgreSQL au réseau ni modifier sa configuration pour s'y connecter à distance.
  Indiquer dans le retour le **nom de l'ordinateur** utilisé (commande `hostname` dans `cmd.exe`).
- **Confirmation de l'instance :** elle repose d'abord sur l'exécution depuis cet ordinateur, puis
  sur la comparaison de l'identifiant système (V0c) lorsqu'il est disponible. La valeur de
  comparaison `7703171660279105004` provient du journal de la campagne L1 du 15/11 sur cet
  ordinateur ; elle n'est pas vérifiée par la présente campagne. L'adresse, le port et la version
  affichés par V0 **ne suffisent pas** : via `localhost`, l'adresse vaut `127.0.0.1` ou `::1` sur
  n'importe quel ordinateur, et le port et la version peuvent être identiques ailleurs.
  L'adresse vaut `NULL` lors d'une connexion par socket Unix.
  V0c ne demande aucun droit supplémentaire. D'après les fichiers de PostgreSQL 18 installés sur
  cet ordinateur, `pg_control_system()` n'est pas restreinte par défaut. Si elle l'était
  localement, V0c afficherait « non disponible » sans arrêter le script ; aucun `GRANT` ni
  changement de rôle n'est demandé.
- **Base :** `projectmanager` uniquement. La garde V0 arrête le script sur toute autre base.
- **Lecture seule :** le script s'ouvre par `BEGIN TRANSACTION READ ONLY` et se termine par
  `ROLLBACK`. Il ne contient que des `SELECT` et deux blocs `DO` de contrôle (garde V0, identifiant
  V0c), sans écriture.
- **Compte :** un **superutilisateur**, ou un rôle disposant de **`BYPASSRLS`**.
  Un simple propriétaire des tables **ne suffit pas** si la RLS est forcée
  (`FORCE ROW LEVEL SECURITY`) : il est alors filtré comme les autres rôles, et les comptages de V8
  seraient incomplets sans erreur visible.
  La ligne `comptage_v8_exhaustif` de V0b doit valoir `t`. Si elle vaut `f`, les chiffres de V8 ne
  sont pas exploitables.
- **Commande conseillée (psql) :**

  ```
  psql -X -a -h localhost -p 5432 -v ON_ERROR_STOP=1 -d projectmanager -f aud001_verifications.sql -L aud001_resultats.txt
  ```

  `-X` ignore tout fichier `psqlrc` personnel ; `-a` affiche chaque commentaire `-- Vx` et chaque
  requête avant son résultat, ce qui permet de rattacher chaque résultat à sa section ;
  `-h localhost -p 5432` vise l'instance locale de cet ordinateur, celle du backend ;
  `ON_ERROR_STOP` arrête net à la première erreur ; `-L` enregistre les requêtes et leurs résultats
  dans `aud001_resultats.txt`. Les messages d'erreur et la ligne `NOTICE` de V0c s'affichent à
  l'écran : les copier tels quels.
  Ajouter seulement l'option de compte (`-U`) si nécessaire, sans modifier `-h` ni `-p`, et sans
  jamais écrire de mot de passe dans un fichier partagé.
- **Variante depuis `cmd.exe`** (capture aussi les erreurs et la ligne `NOTICE` de V0c dans le
  fichier) :

  ```
  psql -X -a -h localhost -p 5432 -v ON_ERROR_STOP=1 -d projectmanager -f aud001_verifications.sql > aud001_resultats.txt 2>&1
  ```

  Ne pas utiliser cette redirection dans Windows PowerShell 5.1 : le fichier y serait écrit en
  UTF-16 et les erreurs de psql y seraient déformées.
- **pgAdmin :** psql est à privilégier. Exécuté d'un seul bloc, le script n'afficherait dans
  pgAdmin que le résultat de la dernière instruction (`ROLLBACK`, sans données). Avec pgAdmin, il
  faut donc exécuter les sections **une par une**, dans la même session de l'outil de requête :
  d'abord `BEGIN TRANSACTION READ ONLY;` et le bloc V0, puis chaque section en exportant son
  résultat, et enfin `ROLLBACK;`. pgAdmin doit lui aussi tourner sur l'ordinateur du backend et se
  connecter à `localhost`, port `5432` ; le résultat de V0c apparaît dans l'onglet « Messages ».
- **Valeurs sensibles :** V7 affiche tous les paramètres permanents (`setconfig`). Avant de
  transmettre les résultats, masquer toute valeur sensible qui y figurerait.
- Remarque : le script est enregistré en UTF-8. Si les libellés accentués apparaissent mal dans la
  sortie, définir `PGCLIENTENCODING=UTF8` avant d'appeler `psql`. Cela n'a aucun effet sur les
  vérifications.

## 4. Interdictions

- Ne créer aucun rôle, en particulier pas `projectmanager_app`.
- Ne modifier aucun droit, aucune politique RLS, aucune table ni aucune donnée.
- Ne lancer aucune migration.
- Ne pas utiliser `gs_test_app`.
- Ne toucher à aucune autre base, notamment `gestion_suivis_pg18_validation` et `gestion_suivis_test`.
- Ne pas démarrer l'application.
- Ne transmettre aucun mot de passe ni aucun secret.

## 5. Message à transmettre au membre BDD

---

**Objet : AUD-001, vérifications PostgreSQL en lecture seule sur `projectmanager`**

Bonjour,

Dans le cadre de l'audit de sécurité AUD-001, nous avons constaté que le backend se connecte avec le compte `postgres`, ce qui empêche la RLS de s'appliquer. Avant de décider de la suite, nous avons besoin de quelques informations sur la base `projectmanager`. **Il s'agit uniquement de les recueillir.**

**Ce que nous te demandons :**
- Exécuter le script joint `aud001_verifications.sql` (V0 à V8), **uniquement sur la base `projectmanager`** de l'instance PostgreSQL 18 **utilisée par le backend**.
- **Le lancer sur l'ordinateur où se trouve et tourne le backend de Gestion-Suivis.** Le backend se connecte à `localhost:5432` depuis cet ordinateur, et `localhost` désigne toujours la machine qui exécute la commande : lancé depuis ton propre ordinateur, le script interrogerait une autre instance. Merci donc de ne pas l'exécuter depuis ton ordinateur personnel pour vérifier l'instance du backend, et de ne pas ouvrir PostgreSQL au réseau ni modifier sa configuration pour t'y connecter à distance.
- Nous indiquer le **nom de l'ordinateur** utilisé (commande `hostname` dans `cmd.exe`).
- Conserver la sortie complète dans un fichier et nous la transmettre.

**Ce qu'il ne faut pas faire :**
- ne créer aucun rôle, et en particulier pas `projectmanager_app` : c'est une étape ultérieure, qui sera décidée ensemble ;
- ne modifier aucun droit, aucune politique RLS, aucune table ni aucune donnée ;
- ne lancer aucune migration ;
- ne pas utiliser `gs_test_app` ;
- ne toucher à aucune autre base ;
- ne pas démarrer l'application ;
- ne nous transmettre aucun mot de passe.

**Compte à utiliser :** un compte **superutilisateur**, ou un rôle disposant de **`BYPASSRLS`**, dans le respect de tes autorisations. Un simple propriétaire des tables ne suffit pas si la RLS est forcée. La ligne `comptage_v8_exhaustif` de la section V0b doit valoir `t`. Si elle vaut `f`, les comptes de V8 sont incomplets : merci de nous le signaler.

**Exécution conseillée :**

```
psql -X -a -h localhost -p 5432 -v ON_ERROR_STOP=1 -d projectmanager -f aud001_verifications.sql -L aud001_resultats.txt
```

Garde `-h localhost -p 5432` tels quels ; ajoute seulement l'option de compte (`-U`) si nécessaire. Ne nous transmets aucun mot de passe. L'option `-a` affiche chaque section et chaque requête avant son résultat ; `-L` enregistre requêtes et résultats dans le fichier. Les éventuels messages d'erreur et la ligne `NOTICE` de V0c (identifiant système) s'affichent à l'écran : copie-les tels quels. Depuis `cmd.exe`, tu peux aussi remplacer `-L aud001_resultats.txt` par `> aud001_resultats.txt 2>&1`, qui capture aussi les erreurs et cette ligne ; évite cette redirection dans Windows PowerShell 5.1.

**Identification de l'instance :** elle repose d'abord sur l'exécution depuis l'ordinateur du backend, puis sur la ligne V0c, qui compare l'identifiant système à la valeur relevée sur cet ordinateur le 15/11 (`7703171660279105004`, issue de notre journal de campagne L1 et non revérifiée). Si V0c affiche « non disponible », ce n'est pas bloquant : ne demande ni ne modifie aucun droit pour l'obtenir. L'adresse, le port et la version affichés par V0 ne suffisent pas à eux seuls.

Le script s'ouvre en transaction en lecture seule, s'arrête si la base n'est pas `projectmanager`, et se termine par `ROLLBACK` : il ne peut rien modifier.

**pgAdmin :** utilise psql de préférence. Lancé d'un seul bloc dans pgAdmin, le script n'afficherait que le résultat de la dernière instruction, sans données. Si tu utilises pgAdmin, sur ce même ordinateur et connecté à `localhost`, port `5432`, exécute les sections une par une dans la même session : `BEGIN TRANSACTION READ ONLY;` et V0 d'abord, puis chaque section en exportant son résultat, et enfin `ROLLBACK;`. Le résultat de V0c apparaît dans l'onglet « Messages ».

**Valeurs sensibles :** la section V7 affiche les paramètres permanents (`setconfig`) : masque toute valeur sensible qui y figurerait avant de nous transmettre les résultats.

**Script :** `backend/docs/audit/aud001_verifications.sql`.

**Ce que nous attendons en retour :** le fichier `aud001_resultats.txt` complet, sans aucun mot de passe ni valeur sensible ; le nom de l'ordinateur utilisé ; la ligne `NOTICE` de V0c (identifiant système, ou « non disponible »). En cas d'erreur, le message d'erreur exact et la section où il est apparu.

Merci !

---

## 6. Résultats à recevoir en retour

| Section | Résultat attendu en retour |
|---|---|
| Ordinateur | Nom de l'ordinateur sur lequel psql a été lancé ; ce doit être celui du backend |
| V0 | Base, compte, adresse et port du serveur (adresse `NULL` si socket Unix), version et version complète, `lecture_seule` (doit valoir `on`) ; à titre indicatif seulement |
| V0c | Ligne `NOTICE` : identifiant système et comparaison (`IDENTIQUE` attendu) avec la valeur de référence du journal L1, ou « non disponible » |
| V0b | `rolsuper`, `rolbypassrls`, `rls_activee_audit_logs`, propriétaire, `rls_forcee_audit_logs` et surtout **`comptage_v8_exhaustif`** (doit valoir `t`) |
| V1 | Attributs complets de `postgres` ; liste des rôles superutilisateurs ou `BYPASSRLS` ; existence éventuelle de `projectmanager_app` |
| V2 et V3 | Pour les 10 tables : existence, propriétaire, RLS activée, RLS forcée. Tables supplémentaires éventuelles. Propriétaires de la base et du schéma `public`. |
| V4 | Les politiques (table, nom, type, rôles, commande, expressions `USING` et `WITH CHECK`) ; tables avec RLS mais sans politique |
| V5 | Droits sur la base (`CONNECT`, `TEMPORARY`), sur le schéma `public`, sur chaque table et par colonne ; nombre de séquences ; extensions |
| V6 | Privilèges par défaut : rôle créateur, schéma, type d'objet, bénéficiaire, droit |
| V7 | Appartenances entre rôles ; paramètres permanents de base et de rôle (valeurs sensibles masquées) ; présence de `app.current_organization_id` ; valeur dans la session ; `row_security` |
| V8 | Les 4 comptages d'`audit_logs`, la répartition par `entity_type` et `action`, la définition des clés étrangères |
| En cas d'erreur | Message exact et section concernée |

## 7. Points à décider après réception des résultats

1. **Confirmation d'AUD-001** : `postgres` est-il superutilisateur (V1) ?
2. **Propriétaire des tables et rôle de migration** (V2), qui conditionnent les privilèges par défaut.
3. **Activer ou non `FORCE ROW LEVEL SECURITY`**, compte tenu de son effet sur les seeds et les scripts joués par le propriétaire.
4. **Liste définitive des droits du futur rôle**, à partir de V5 : `CONNECT` explicite ou non, CRUD sur les 10 tables, aucun `TRUNCATE`.
5. **Stratégie pour les futures tables** (V6).
6. **Historiques sans organisation** (V8) : les laisser, les rattacher ou les archiver, et corriger AUD-003 avant ou en même temps que l'activation réelle de la RLS.
7. **Anomalies éventuelles** de V4 et V7 (politique non conforme, paramètre permanent), à traiter **avant** toute bascule.
8. **Nom, attributs et mode de transmission du mot de passe** de `projectmanager_app`. La création reste une étape ultérieure, soumise à autorisation.
