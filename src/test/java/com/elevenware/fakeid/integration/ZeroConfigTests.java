package com.elevenware.fakeid.integration;

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
