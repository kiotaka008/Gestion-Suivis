package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.entity.Utilisateur;
import com.gestionsuivis.backend.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;
import java.util.UUID;

/**
 * Transforme un JWT valide en utilisateur connecté, en rechargeant le compte
 * depuis la base : un compte supprimé ou désactivé est refusé (401) même si le
 * jeton n'a pas expiré, et le rôle courant fait foi.
 */
@RequiredArgsConstructor
public class ConvertisseurJwtUtilisateur implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UtilisateurRepository utilisateurs;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Jeton invalide.");
        }
        Utilisateur utilisateur = utilisateurs.findById(id)
                .filter(Utilisateur::isActif)
                .orElseThrow(() -> new InvalidBearerTokenException("Compte inexistant ou désactivé."));
        UtilisateurConnecte principal = new UtilisateurConnecte(utilisateur.getId(), utilisateur.getEmail(),
                utilisateur.getRole(),
                utilisateur.getDepartement() == null ? null : utilisateur.getDepartement().getId());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name())));
    }
}
