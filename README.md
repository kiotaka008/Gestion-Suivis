# \# Gestion Suivis — ProjectManager SaaS

# 

# Plateforme SaaS de gestion de projets et d'équipes.

# 

# \## Architecture du projet

# 

# Ce projet est composé de 3 parties :

# 

# 1\. \*\*Frontend (React / TypeScript)\*\* : Interface utilisateur (Dashboard, Projets, Activités, Assistant IA).

# 2\. \*\*Backend (Java / Spring Boot)\*\* : API REST, authentification JWT, gestion des données et proxy vers le service IA.

# 3\. \*\*Service IA (Python / FastAPI)\*\* : Micro-service dédié à l'intelligence artificielle (Google Gemini).

# 

# Flux de données :

# Frontend (React) → Backend (Spring Boot) → Service IA (FastAPI) → Google Gemini API

# 

# \## Prérequis pour lancer le projet complet

# 

# \- Node.js (pnpm)

# \- Java 21 (Temurin)

# \- PostgreSQL 17

# \- Python 3.12

# 

# \## Lancer le projet en local

# 

# \### 1. Base de données

# 

# ```bash

# psql -U postgres -c "CREATE DATABASE gestion\_suivis;"

# psql -U postgres -d gestion\_suivis -f ProjectManager\_SaaS\_Database/database/migrations/20260930\_001\_projectmanager\_initial\_schema.sql

# psql -U postgres -d gestion\_suivis -f ProjectManager\_SaaS\_Database/database/seeds/20260930\_002\_projectmanager\_dev\_seed.sql

