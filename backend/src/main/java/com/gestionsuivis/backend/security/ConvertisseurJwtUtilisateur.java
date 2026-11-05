package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.config.ExecuteurOrganisation;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Transforme un JWT valide en utilisateur connecté, en rechargeant le compte
 * depuis la base dans l'organisation indiquée par le jeton : un compte supprimé,
 * désactivé ou d'une autre organisation est refusé (401) même si le jeton n'a pas
 * expiré, et le rôle courant fait foi.
 */
@RequiredArgsConstructor
public class ConvertisseurJwtUtilisateur implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UtilisateurRepository utilisateurs;
    private final ExecuteurOrganisation executeur;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id = uuid(jwt.getSubject());
        UUID organisation = uuid(jwt.getClaimAsString(JwtService.CLAIM_ORGANISATION));
        Optional<Utilisateur> trouve = executeur.lire(organisation, () -> utilisateurs.findById(id));
        Utilisateur utilisateur = trouve
                .filter(Utilisateur::isActif)
                .filter(u -> organisation.equals(u.getOrganisationId()))
                .orElseThrow(() -> new InvalidBearerTokenException("Compte inexistant ou désactivé."));
        UtilisateurConnecte principal = new UtilisateurConnecte(utilisateur.getId(), utilisateur.getEmail(),
                utilisateur.getRole(), utilisateur.getOrganisationId());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name())));
    }

    private static UUID uuid(String valeur) {
        try {
            return UUID.fromString(valeur);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Jeton invalide.");
        }
    }
}
