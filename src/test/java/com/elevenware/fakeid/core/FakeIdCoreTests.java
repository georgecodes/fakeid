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

import com.elevenware.fakeid.Configuration;
import com.elevenware.fakeid.core.dto.AuthorizeRequest;
import com.elevenware.fakeid.core.dto.AuthorizeResponse;
import com.elevenware.fakeid.core.dto.IntrospectRequest;
import com.elevenware.fakeid.core.dto.IntrospectResponse;
import com.elevenware.fakeid.core.dto.TokenRequest;
import com.elevenware.fakeid.core.dto.TokenResponse;
import com.elevenware.fakeid.core.error.UnsupportedGrantTypeException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises FakeIdCore entirely in-JVM with no HTTP server, no Javalin, no
 * ports bound. Demonstrates the library-use case: a consumer can issue and
 * verify id_tokens, run auth-code round trips, and introspect issued grants
 * purely through the core API.
 */
class FakeIdCoreTests {

    @Test
    void authCodeRoundTripReturnsSignedIdToken() throws Exception {
        RSAKey jwk = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("signingKey")
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        Configuration cfg = Configuration.builder()
                .jwks(new JWKSet(jwk))
                .claims(Map.of("sub", "user@example.com", "email", "user@example.com"))
                .build();
        FakeIdCore core = new FakeIdCore(cfg);

        AuthorizeResponse authResp = core.authorize(new AuthorizeRequest(
                "my-client",
                "https://app.example/cb",
                "code",
                Set.of("openid", "profile"),
                "state-abc",
                "nonce-xyz"));

        assertNotNull(authResp.code());
        assertNull(authResp.idToken());
        assertEquals("state-abc", authResp.state());

        TokenResponse tokenResp = core.token(new TokenRequest(
                "authorization_code",
                authResp.code(),
                null,
                "my-client",
                "ignored"));

        assertEquals("Bearer", tokenResp.tokenType());
        assertEquals(3600L, tokenResp.expiresIn());
        assertEquals("authorization_code", tokenResp.grantType());
        assertNotNull(tokenResp.accessToken());
        assertNotNull(tokenResp.idToken());

        SignedJWT idToken = SignedJWT.parse(tokenResp.idToken());
        assertTrue(idToken.verify(new RSASSAVerifier(jwk)));
        JWTClaimsSet claims = idToken.getJWTClaimsSet();
        assertEquals("user@example.com", claims.getSubject());
        assertEquals("nonce-xyz", claims.getStringClaim("nonce"));
        assertTrue(claims.getAudience().contains("my-client"));
    }

    @Test
    void implicitFlowEmbedsIdTokenInResponse() throws Exception {
        RSAKey jwk = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("signingKey")
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        Configuration cfg = Configuration.builder()
                .jwks(new JWKSet(jwk))
                .claims(Map.of("sub", "jeff@example.com"))
                .build();
        FakeIdCore core = new FakeIdCore(cfg);

        AuthorizeResponse resp = core.authorize(new AuthorizeRequest(
                "spa-client",
                "https://spa/cb",
                "token id_token",
                Set.of("openid"),
                null,
                "n-0S6_WzA2Mj"));

        assertNull(resp.code());
        assertNotNull(resp.accessToken());
        assertNotNull(resp.idToken());

        SignedJWT idToken = SignedJWT.parse(resp.idToken());
        assertTrue(idToken.verify(new RSASSAVerifier(jwk)));
        assertEquals("jeff@example.com", idToken.getJWTClaimsSet().getSubject());

        String redirect = resp.toRedirectLocation();
        assertTrue(redirect.startsWith("https://spa/cb?"));
        assertTrue(redirect.contains("token=" + resp.accessToken()));
        assertTrue(redirect.contains("id_token=" + resp.idToken()));
    }

    @Test
    void clientCredentialsGrantDoesNotMintIdToken() {
        FakeIdCore core = new FakeIdCore(Configuration.builder().build());

        TokenResponse resp = core.token(new TokenRequest(
                "client_credentials",
                null,
                "api:read api:write",
                "svc-client",
                "svc-secret"));

        assertEquals("Bearer", resp.tokenType());
        assertEquals("client_credentials", resp.grantType());
        assertEquals("svc-client", resp.clientId());
        assertNotNull(resp.accessToken());
        assertNull(resp.idToken());
        assertTrue(resp.scope().contains("api:read"));
        assertTrue(resp.scope().contains("api:write"));
    }

    @Test
    void unsupportedGrantTypeThrows() {
        FakeIdCore core = new FakeIdCore(Configuration.builder().build());

        UnsupportedGrantTypeException ex = assertThrows(
                UnsupportedGrantTypeException.class,
                () -> core.token(new TokenRequest("password", null, null, "c", "s")));

        assertEquals("unsupported_grant_type", ex.error());
        assertEquals(400, ex.httpStatus());
    }

    @Test
    void introspectIssuedAccessTokenIsActive() {
        FakeIdCore core = new FakeIdCore(Configuration.builder()
                .claims(Map.of("sub", "introspect-subject"))
                .build());

        TokenResponse issued = core.token(new TokenRequest(
                "client_credentials", null, "openid", "c1", "s1"));

        IntrospectResponse resp = core.introspect(new IntrospectRequest(issued.accessToken()));

        assertTrue(resp.active());
        assertEquals("c1", resp.clientId());
        assertEquals("introspect-subject", resp.sub());
        assertEquals("openid", resp.scope());
        assertNotNull(resp.exp());
        assertNotNull(resp.iat());
    }

    @Test
    void introspectUnknownTokenIsInactive() {
        FakeIdCore core = new FakeIdCore(Configuration.builder().build());

        IntrospectResponse resp = core.introspect(new IntrospectRequest("never-issued-token"));

        assertFalse(resp.active());
        assertNull(resp.clientId());
        assertNull(resp.sub());
    }

    @Test
    void discoveryExposesConfiguredIssuer() {
        Configuration cfg = Configuration.builder()
                .issuer("https://fake.example.test")
                .build();
        FakeIdCore core = new FakeIdCore(cfg);

        assertEquals("https://fake.example.test", core.discovery().getIssuer());
    }

    @Test
    void jwksExposesPublicSigningKey() {
        FakeIdCore core = new FakeIdCore(Configuration.builder().build());

        Map<String, Object> jwks = core.jwks();

        assertNotNull(jwks.get("keys"));
    }

    @Test
    void userInfoReturnsConfiguredClaims() {
        Map<String, Object> configured = Map.of(
                "sub", "alice",
                "email", "alice@example.com",
                "custom_claim", "whatever");
        FakeIdCore core = new FakeIdCore(Configuration.builder().claims(configured).build());

        Map<String, Object> claims = core.userInfo();

        assertEquals("alice", claims.get("sub"));
        assertEquals("alice@example.com", claims.get("email"));
        assertEquals("whatever", claims.get("custom_claim"));
    }
}
