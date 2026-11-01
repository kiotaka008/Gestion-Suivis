package com.gestionsuivis.backend.security;

import com.gestionsuivis.backend.entity.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Émission des JWT signés en HS256. Le jeton ne contient que l'identifiant
 * (sujet), l'email et le rôle ; l'utilisateur est rechargé à chaque requête.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String EMETTEUR = "gestion-suivis";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";

    private final JwtEncoder encodeur;
    private final SecuriteProperties proprietes;
    private final Clock horloge;

    public String generer(Utilisateur utilisateur) {
        Instant maintenant = horloge.instant();
        JwtClaimsSet revendications = JwtClaimsSet.builder()
                .issuer(EMETTEUR)
                .subject(utilisateur.getId().toString())
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(proprietes.dureeJeton()))
                .claim(CLAIM_EMAIL, utilisateur.getEmail())
                .claim(CLAIM_ROLE, utilisateur.getRole().name())
                .build();
        JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).build();
        return encodeur.encode(JwtEncoderParameters.from(entete, revendications)).getTokenValue();
    }
}
