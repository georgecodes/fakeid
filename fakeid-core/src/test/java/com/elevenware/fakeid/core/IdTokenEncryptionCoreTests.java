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
import com.elevenware.fakeid.core.dto.TokenRequest;
import com.elevenware.fakeid.core.dto.TokenResponse;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oidc4j.v2.lib.model.DiscoveryDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdTokenEncryptionCoreTests {

    private static RSAKey signingKey() throws Exception {
        return new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("signingKey")
                .algorithm(JWSAlgorithm.RS256)
                .generate();
    }

    @Test
    void authCodeFlowReturnsNestedJweDecryptableByRelyingParty() throws Exception {
        RSAKey signingKey = signingKey();
        RSAKey rpKey = new RSAKeyGenerator(2048).keyID("rp-rsa").generate();
        Configuration cfg = Configuration.builder()
                .jwks(new JWKSet(signingKey))
                .claims(Map.of("sub", "user@example.com", "email", "user@example.com"))
                .idTokenEncryption(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM, rpKey)
                .build();
        FakeIdCore core = new FakeIdCore(cfg);

        AuthorizeResponse authResp = core.authorize(new AuthorizeRequest(
                "my-client", "https://app.example/cb", "code", Set.of("openid"), "st", "nonce-xyz"));
        TokenResponse tokenResp = core.token(new TokenRequest(
                "authorization_code", authResp.code(), null, "my-client", "secret"));

        assertNotNull(tokenResp.idToken());
        assertEquals(5, tokenResp.idToken().split("\\.").length, "compact JWE has five segments");

        EncryptedJWT jwe = EncryptedJWT.parse(tokenResp.idToken());
        assertEquals(JWEAlgorithm.RSA_OAEP_256, jwe.getHeader().getAlgorithm());
        assertEquals(EncryptionMethod.A256GCM, jwe.getHeader().getEncryptionMethod());
        assertEquals("rp-rsa", jwe.getHeader().getKeyID());
        assertEquals("JWT", jwe.getHeader().getContentType());

        jwe.decrypt(new RSADecrypter(rpKey));
        SignedJWT inner = jwe.getPayload().toSignedJWT();
        assertNotNull(inner, "payload must be a signed JWT");
        assertEquals(JWSAlgorithm.RS256, inner.getHeader().getAlgorithm());
        assertEquals("signingKey", inner.getHeader().getKeyID());
        assertTrue(inner.verify(new RSASSAVerifier(signingKey)));

        JWTClaimsSet claims = inner.getJWTClaimsSet();
        assertEquals("user@example.com", claims.getSubject());
        assertEquals("nonce-xyz", claims.getStringClaim("nonce"));
        assertEquals(cfg.getIssuer(), claims.getIssuer());
        assertEquals(List.of("my-client"), claims.getAudience());
    }

    @Test
    void implicitFlowReturnsEncryptedIdTokenUsingEcdh() throws Exception {
        RSAKey signingKey = signingKey();
        ECKey rpKey = new ECKeyGenerator(Curve.P_256).keyID("rp-ec").generate();
        Configuration cfg = Configuration.builder()
                .jwks(new JWKSet(signingKey))
                .claims(Map.of("sub", "jeff@example.com"))
                .idTokenEncryptionAlgorithm(JWEAlgorithm.ECDH_ES)
                .idTokenEncryptionKey(rpKey)
                .build();
        FakeIdCore core = new FakeIdCore(cfg);

        AuthorizeResponse resp = core.authorize(new AuthorizeRequest(
                "spa-client", "https://spa/cb", "token id_token", Set.of("openid"), null, "n-0S6_WzA2Mj"));

        assertNotNull(resp.idToken());
        EncryptedJWT jwe = EncryptedJWT.parse(resp.idToken());
        assertEquals(JWEAlgorithm.ECDH_ES, jwe.getHeader().getAlgorithm());
        assertEquals(EncryptionMethod.A128CBC_HS256, jwe.getHeader().getEncryptionMethod(), "default enc");
        jwe.decrypt(new ECDHDecrypter(rpKey));
        SignedJWT inner = jwe.getPayload().toSignedJWT();
        assertTrue(inner.verify(new RSASSAVerifier(signingKey)));
        assertEquals("jeff@example.com", inner.getJWTClaimsSet().getSubject());
        assertEquals("n-0S6_WzA2Mj", inner.getJWTClaimsSet().getStringClaim("nonce"));
        assertTrue(resp.toRedirectLocation().contains("id_token=" + resp.idToken()));
    }

    @Test
    void generatedRecipientKeyCanDecrypt() throws Exception {
        Configuration cfg = Configuration.builder()
                .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP)
                .build();
        FakeIdCore core = new FakeIdCore(cfg);

        AuthorizeResponse authResp = core.authorize(new AuthorizeRequest(
                "c", "https://app/cb", "code", Set.of("openid"), null, null));
        TokenResponse tokenResp = core.token(new TokenRequest(
                "authorization_code", authResp.code(), null, "c", "s"));

        EncryptedJWT jwe = EncryptedJWT.parse(tokenResp.idToken());
        jwe.decrypt(new RSADecrypter(cfg.getIdTokenEncryptionKey().toRSAKey()));
        assertEquals(cfg.getClaims().get("sub"), jwe.getPayload().toSignedJWT().getJWTClaimsSet().getSubject());
    }

    @Test
    void clientCredentialsGrantStillIssuesNoIdToken() {
        FakeIdCore core = new FakeIdCore(Configuration.builder()
                .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP_256)
                .build());

        TokenResponse resp = core.token(new TokenRequest("client_credentials", null, "api", "svc", "s"));

        assertNull(resp.idToken());
        assertNotNull(resp.accessToken());
    }

    @Test
    void unencryptedProviderStillIssuesPlainSignedJwt() throws Exception {
        FakeIdCore core = new FakeIdCore(Configuration.builder().build());

        AuthorizeResponse authResp = core.authorize(new AuthorizeRequest(
                "c", "https://app/cb", "code", Set.of("openid"), null, null));
        TokenResponse tokenResp = core.token(new TokenRequest(
                "authorization_code", authResp.code(), null, "c", "s"));

        assertEquals(3, tokenResp.idToken().split("\\.").length);
        assertNotNull(SignedJWT.parse(tokenResp.idToken()));
    }

    @Test
    void discoveryAdvertisesEncryptionWhenEnabled() {
        FakeIdCore core = new FakeIdCore(Configuration.builder()
                .idTokenEncryptionAlgorithm(JWEAlgorithm.ECDH_ES_A256KW)
                .idTokenEncryptionMethod(EncryptionMethod.A256CBC_HS512)
                .build());

        DiscoveryDocument doc = core.discovery();

        assertEquals(Set.of("ECDH-ES+A256KW"), doc.getIdTokenEncryptionAlgValuesSupported());
        assertEquals(Set.of("A256CBC-HS512"), doc.getIdTokenEncryptionEncValuesSupported());
    }

    @Test
    void discoveryOmitsEncryptionWhenDisabled() {
        FakeIdCore core = new FakeIdCore(Configuration.builder().build());

        DiscoveryDocument doc = core.discovery();

        assertNull(doc.getIdTokenEncryptionAlgValuesSupported());
        assertNull(doc.getIdTokenEncryptionEncValuesSupported());
    }

    @Test
    @SuppressWarnings("unchecked")
    void jwksDoesNotPublishTheRelyingPartyKey() throws Exception {
        Configuration cfg = Configuration.builder()
                .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP_256)
                .build();
        FakeIdCore core = new FakeIdCore(cfg);

        List<Map<String, Object>> keys = (List<Map<String, Object>>) core.jwks().get("keys");

        assertEquals(1, keys.size());
        assertEquals("signingKey", keys.get(0).get("kid"));
        JWK published = JWK.parse(keys.get(0));
        assertTrue(!published.isPrivate());
    }
}
