package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.entity.Role;

import java.util.UUID;

/**
 * Identité de l'utilisateur authentifié, rechargée depuis la base à chaque requête
 * (un compte désactivé ou supprimé perd immédiatement l'accès, même avec un jeton valide).
 *
 * @param id             identifiant de l'utilisateur
 * @param email          email de connexion
 * @param role           rôle courant (ADMIN, MANAGER, EMPLOYE)
 * @param departementId  département de rattachement, {@code null} si aucun
 */
public record UtilisateurConnecte(UUID id, String email, Role role, UUID departementId) {

    public boolean estAdmin() {
        return role == Role.ADMIN;
    }

    public boolean estManager() {
        return role == Role.MANAGER;
    }

    public boolean estEmploye() {
        return role == Role.EMPLOYE;
    }

    public boolean estDansDepartement(UUID departement) {
        return departement != null && departement.equals(departementId);
    }
}
