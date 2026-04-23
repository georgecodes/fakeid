package com.elevenware.fakeid.core;

/*-
 * #%L
 * Fake ID
 * %%
 * Copyright (C) 2025 George McIntosh
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

public final class TokenMinter {

    private final RSAKey signingKey;
    private final String issuer;

    public TokenMinter(RSAKey signingKey, String issuer) {
        this.signingKey = signingKey;
        this.issuer = issuer;
    }

    public String mintIdToken(String subject, String audience, String nonce, Map<String, Object> claims) {
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
        for (Map.Entry<String, Object> claim : claims.entrySet()) {
            claimsBuilder.claim(claim.getKey(), claim.getValue());
        }
        claimsBuilder.subject(subject);
        if (nonce != null) {
            claimsBuilder.claim("nonce", nonce);
        }
        claimsBuilder.claim("iss", issuer);
        claimsBuilder.audience(audience);
        Instant now = Instant.now();
        claimsBuilder.issueTime(Date.from(now));
        claimsBuilder.expirationTime(Date.from(now.plus(1L, ChronoUnit.HOURS)));
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.parse(signingKey.getAlgorithm().getName()))
                .keyID(signingKey.getKeyID())
                .build();
        SignedJWT idToken = new SignedJWT(header, claimsBuilder.build());
        try {
            idToken.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
            return idToken.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign id_token", e);
        }
    }
}
