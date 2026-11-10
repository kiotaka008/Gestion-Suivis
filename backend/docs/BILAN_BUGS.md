# Bilan des bugs et des problèmes de connexion entre les parties

Version analysée : branche `develop`, commit `b290fff` (frontend, service IA et base de données
tels que livrés par leurs responsables).

État des corrections :
- **Backend** : corrigé pour être compatible avec le frontend et le service IA (section 1).
- **Frontend** : les 17 points de la section 2 ont été **corrigés** à la demande du chef de projet
  (toutes les pages branchées sur le backend, dont les boutons « ajouter »). Vérifié dans le
  navigateur : 20 vérifications sur 20, inscription comprise.
- **Service IA** : les 8 points de la section 3 ont été **corrigés** (78 tests réussis). Chat,
  analyse de risques et rapport testés à travers le backend.
- **Base de données** : aucun bug (section 4).

Gravité : **Bloquant** (empêche une fonctionnalité de marcher), **Majeur** (fonctionnalité fausse
ou trompeuse), **Mineur** (qualité, confort, documentation).

---

## 1. Ce que le backend a corrigé de son côté

| Problème de connexion | Correction dans le backend |
|---|---|
| Le frontend vise deux ports différents : `.env.example` appelle `http://localhost:3000/api`, le proxy de `vite.config.ts` envoie `/api` vers `http://localhost:8080`. | L'API écoute sur **3000** (port principal) **et 8080** (`SERVER_SECONDARY_PORTS`, défaut `8080`). Les deux réglages du frontend fonctionnent. |
| Le frontend ne lit pas le jeton CSRF. | Connexion, déconnexion, inscription et connexion Google sont exemptées du jeton CSRF : les seuls appels faits par le frontend de `b290fff` (`/auth/login`, `/auth/me`, `/auth/logout`) passent même sans jeton. |
| Appel cross-origin avec cookies (5173 → 3000). | CORS autorise `http://localhost:5173` avec cookies ; cookie de session `SameSite=Lax` (5173 et 3000 sont le même site). |
| Le tri des projets par progression (`sortBy=progress`) était refusé. | Accepté (`progress` / `avancement`). |
| Inscription : un identifiant d'organisation mal tapé donnait « Aucune organisation n'est disponible ». | Message corrigé : « Organisation introuvable : vérifiez son identifiant. » |
| Formats de réponse. | Vérifiés conformes au frontend : `POST /auth/login` → `{ user }`, listes → `{ data, meta: { page, perPage, total, totalPages } }`, erreurs → `{ code, message, errors[] }`. |

Contrat backend ↔ service IA vérifié : noms de champs (`messages`, `user_id`, `conversation_id`,
`project_name`, `report_type`…), en-tête `X-Internal-API-Key`, tailles (description tronquée à
5000 caractères, message limité à 8000). Le chat a été testé avec le vrai service IA. Si le service IA
est arrêté ou en erreur, le backend répond 503 avec un message clair au lieu de planter.

Base de données vérifiée : migrations, jeu de données, test d'isolation entre organisations
(`PASS`), rollback puis réinstallation ; le backend démarre sur le schéma officiel avec
`ddl-auto=validate`.

---

## 2. Frontend (React) — bugs trouvés dans `b290fff` (tous corrigés depuis)

Ce qui a été fait : services d'API pour toutes les ressources, proxy Vite `/api` vers le port 3000
(jeton CSRF envoyé automatiquement), routes protégées, vraie déconnexion, champ « Organisation »
à la connexion et à l'inscription, mot de passe initial à la création d'utilisateur, messages
d'erreur du backend affichés, assistant IA relié avec conservation de la conversation, 5ᵉ couleur
pour le statut archivé, `models.ts` réparé, configuration Vitest rétablie, ESLint sans erreur,
bouton de connexion Google (actif dès que `GOOGLE_CLIENT_ID` est configuré).

### Bloquants

1. **Le projet ne compile pas** — `src/types/models.ts` est tronqué (accolade fermante manquante
   à la fin de l'interface `Notification`). `npm run build` et `npx tsc -b` échouent.
   *Correction* : terminer l'interface (`}`) et vérifier les types qui suivaient.

2. **Modifications refusées par le backend (403) en appel direct** — `src/services/api.ts`
   n'envoie pas le jeton CSRF. Avec `VITE_API_URL=http://localhost:3000/api`, l'appel est
   cross-origin et Axios n'envoie pas l'en-tête `X-XSRF-TOKEN` : toute création, modification ou
   suppression (projets, activités, utilisateurs…) sera refusée dès qu'elle sera branchée.
   *Correction* (au choix) : passer par le proxy Vite (`VITE_API_URL=/api`, proxy vers
   `http://localhost:3000`) — même origine, Axios envoie alors le jeton tout seul ; ou ajouter
   `withXSRFToken: true` dans `axios.create`.

3. **La plupart des pages n'utilisent pas le backend** — elles affichent les données fictives de
   `src/mocks/` : projets, détail d'un projet, activités, utilisateurs, notifications, rapports,
   graphiques et listes du tableau de bord, assistant IA. Les services existent
   (`projectService`, `activityService`, `userService`) mais ne sont appelés par aucune page.
   Routes backend disponibles : voir `backend/docs/API.md`.

4. **Création d'utilisateur impossible** — `CreateUserModal` n'a pas de champ mot de passe ;
   `POST /api/users` exige un mot de passe initial (8 à 100 caractères) et répondra 422.

5. **Connexion impossible pour un email présent dans deux organisations** — le backend répond
   422 sur le champ `organization` (ex. `jean.rakoto@company.com`) ; `LoginPage` n'a pas de champ
   « Organisation » et affiche seulement « Certaines informations sont invalides ».
   *Correction* : afficher un champ `organization` (slug, ex. `demo-corp`) quand
   `errors[].field === 'organization'` et le renvoyer dans `POST /auth/login`.

### Majeurs

6. **Pages accessibles sans être connecté** — `src/routes/ProtectedRoute.tsx` existe mais
   n'entoure pas les routes du tableau de bord dans `App.tsx`.

7. **La déconnexion ne déconnecte pas** — `Header.tsx` redirige vers `/login` sans appeler
   `authService.logout()` (TODO dans le code) : le cookie de session reste valide, un simple
   rechargement reconnecte l'utilisateur.

8. **Changement de mot de passe factice** — `SecuritySection.tsx` attend 600 ms puis affiche
   « Votre mot de passe a été modifié » sans appeler le backend (`POST /api/auth/change-password`
   avec `currentPassword` et `newPassword`). L'utilisateur croit avoir changé son mot de passe.

9. **Inscription factice** — `RegisterPage.tsx` redirige vers `/login` sans appeler
   `POST /api/auth/register` (`name`, `email`, `password`, `organization`). Note : l'inscription
   libre est désactivée par défaut côté backend (`AUTH_REGISTRATION_ENABLED=false`).

10. **Profil et photo non enregistrés** — `ProfileSection.tsx` : envoi de l'avatar en TODO ;
    route disponible `PATCH /api/users/me` (`name`, `avatarUrl`).

11. **Messages d'erreur du backend masqués** — `api.ts` remplace le message renvoyé par le
    backend par un texte générique (`ApiError.fromStatus`) ; ex. « Email ou mot de passe
    incorrect. » ou « Cet email est déjà utilisé. » ne sont jamais affichés.

12. **Assistant IA non relié** — `AIAssistantPage` utilise `src/mocks/aiMessages.ts`. Route :
    `POST /api/ai/chat` avec `{ content, conversationId }` ; renvoyer le `conversationId` reçu
    pour garder le fil de la conversation.

13. **Statut « archivé » sans couleur** — `StatusDoughnut` et `StatusReportDoughnut` n'ont que
    4 couleurs alors que le backend renvoie 5 statuts de projet (dont `archived`).

### Mineurs

14. **Ports incohérents** entre `.env.example` (3000) et `vite.config.ts` (8080) — compensé par
    le backend, mais à harmoniser (recommandé : proxy vers 3000 et `VITE_API_URL=/api`).
15. **Tests Vitest** : le bloc `test` (environnement `jsdom`, `globals`, fichier de setup) a
    disparu de `vite.config.ts` dans le dernier commit ; les tests de composants ne trouvent plus
    `document` ni les matchers `jest-dom`.
16. **ESLint : 2 erreurs, 1 avertissement** — `AuthContext.tsx` exporte le contexte et le
    composant dans le même fichier (`react-refresh/only-export-components`) ;
    `useMediaQuery.ts` appelle `setState` directement dans un effet ; `Modal.tsx` lit
    `modalRef.current` dans le nettoyage de l'effet.
17. **Connexion Google** absente du frontend ; le backend est prêt (`GET /api/auth/google/config`,
    `POST /api/auth/google` avec le `credential` de Google Identity Services). Il faut un
    « ID client OAuth » (type Application Web, origine `http://localhost:5173`) à mettre dans
    `GOOGLE_CLIENT_ID` côté backend.

---

## 3. Service IA (FastAPI) — bugs trouvés dans `b290fff` (tous corrigés depuis)

Ce qui a été fait : `groq` dans `requirements.txt`, tests du service LLM réécrits pour Groq,
`.env.example` documente `GROQ_API_KEY` / `GROQ_MODEL` / `USE_MOCK`, mode démo étendu à toutes
les tâches (nouveau `app/services/mock_llm.py` utilisé par `LLMService`, flux compris), journal de
démarrage corrigé, `response_format` non envoyé quand il est vide, typage corrigé (Pyright 0
erreur), documentation mise à jour (README, Dockerfile, `INTEGRATION_GUIDE.md`).

### Bloquants

1. **Le service ne démarre pas sur une installation propre** — le code importe `groq`
   (`app/services/llm_service.py`) mais `requirements.txt` déclare encore `google-genai` :
   `ModuleNotFoundError: No module named 'groq'` au démarrage, image Docker comprise.
   *Correction* : remplacer `google-genai>=1.0.0` par `groq` dans `requirements.txt`.

2. **Tests en échec** — sans `groq`, `pytest` s'arrête dès la collecte de
   `tests/test_llm_service.py` ; avec `groq` installé, 4 tests échouent car ils testent encore
   l'API Gemini (`parts`, rôle `model`, `client.aio.models.generate_content`). Résultat mesuré :
   62 réussis, 4 échoués. *Correction* : réécrire ces tests pour `client.chat.completions.create`.

3. **Impossible de configurer le service à partir du modèle** — `.env.example` documente
   `OPENAI_API_KEY` / `OPENAI_MODEL`, alors que le code lit `GROQ_API_KEY` / `GROQ_MODEL`.
   Sans `GROQ_API_KEY`, chaque appel au LLM échoue (502 « Erreur Groq : Connection error »).

### Majeurs

4. **Mode démo partiel** — `USE_MOCK=true` n'est respecté que par `/api/ai/chat` ;
   `/api/ai/analyze-risks` et `/api/ai/generate-report` appellent quand même Groq et échouent
   sans clé.
5. **Journal de démarrage trompeur** — `app/main.py` affiche `gemini_model` alors que le modèle
   utilisé est `groq_model`.

### Mineurs

6. `response_format=None` est envoyé explicitement à Groq quand aucun format n'est demandé
   (utiliser la sentinelle `omit` du SDK).
7. 14 erreurs de typage Pyright/Pylance (types des messages Groq, `Settings()` sans
   `internal_api_key`, faux objets `Request` dans `tests/test_rate_limit.py`) : soulignées en
   rouge dans VS Code dès que la vérification de types est active.
8. Documentation restée sur Gemini : `README.md`, `Dockerfile`, `tests/conftest.py`
   (`GEMINI_API_KEY`), `INTEGRATION_GUIDE.md`.

---

## 4. Base de données (ProjectManager_SaaS_Database)

Aucun bug trouvé : migrations `001` et `004`, jeu de données `002`, test d'isolation `003`
(`PASS`), rollback et réinstallation fonctionnent sur PostgreSQL 18.

Suggestions :
- Fournir le script de création du rôle applicatif recommandé par la documentation (sans
  `BYPASSRLS`, non propriétaire des tables), pour que la sécurité par organisation (RLS)
  s'applique réellement au backend.
- Le jeu de données utilise le même mot de passe (`password123`) pour tous les comptes :
  réservé au développement.

---

## 5. Comment vérifier

| Partie | Commande |
|---|---|
| Backend | `cd backend` puis `./mvnw test` |
| Frontend | `npx tsc -b`, `npx eslint .`, `npx vitest run`, `npm run build` |
| Service IA | `cd ai`, `pip install -r requirements.txt`, `pytest` |
| Base | migrations puis `database/tests/20260930_003_tenant_isolation_check.sql` |
