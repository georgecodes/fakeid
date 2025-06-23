package com.elevenware.fakeid.integration;

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
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "fakeid.integration.tests", matches = "true")
public class ZeroConfigTests extends AbstractIntegrationTest {



    @Test
    void discoveryDocumentHasDefaultIssuer() throws IOException, InterruptedException {

        Map<String, Object> discoveryDocument = fetchDiscoveryDocument("http://localhost:8091");
        assertEquals("http://localhost:8091", discoveryDocument.get("issuer"));

    }

    @Test
    void jwksReturnsKeys() {

        JWKSet jwkSet = fetchJwks("http://localhost:8091");

        assertNotNull(jwkSet);
        assertEquals(1, jwkSet.getKeys().size());
        JWK key = jwkSet.getKeyByKeyId("signingKey");
        assertNotNull(key);
        assertEquals(JWSAlgorithm.RS256, key.getAlgorithm());
        assertFalse(key.isPrivate());

    }

    @Test
    void defaultClaims() throws IOException, ParseException, JOSEException {

        String state = RandomStringUtils.randomAlphabetic(32);
        String nonce = RandomStringUtils.randomAlphabetic(32);
        String clientId = "client1";

        Map<String, String> queryParams = implicitAuthRequest("http://localhost:8091", state, nonce, clientId);
        String stateReturned = queryParams.get("state");
        String code = queryParams.get("code");
        String idToken = queryParams.get("id_token");

        assertEquals(state, stateReturned);
        assertNotNull(code);
        SignedJWT jwt = SignedJWT.parse(idToken);
        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        assertEquals(nonce, claims.getClaim("nonce"));
        assertEquals("John C. Developer", claims.getSubject());
        assertEquals("John C. Developer", claims.getClaim("name"));
        assertEquals("john@developer.com", claims.getClaim("email"));
        assertEquals("http://localhost:8091", claims.getIssuer());
        assertEquals(clientId, claims.getAudience().get(0));

        JWKSet jwkSet = fetchJwks("http://localhost:8091");
        RSAKey key = jwkSet.getKeyByKeyId("signingKey").toRSAKey();

        RSASSAVerifier verifier = new RSASSAVerifier(key);
        boolean signedByJwk = jwt.verify(verifier);

        assertTrue(signedByJwk);

    }



}
