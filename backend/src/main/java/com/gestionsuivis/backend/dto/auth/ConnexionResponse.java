package com.gestionsuivis.backend.dto.auth;

import com.gestionsuivis.backend.dto.utilisateur.UtilisateurResponse;

/**
 * Réponse de {@code POST /api/auth/login}, identique à {@code LoginResponse} du
 * frontend : {@code { "user": { ... } }}. Le JWT n'est jamais dans le corps : il est
 * posé dans le cookie HttpOnly {@code access_token}.
 */
public record ConnexionResponse(UtilisateurResponse user) {
}
