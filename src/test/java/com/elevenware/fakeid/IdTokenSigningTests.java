package com.elevenware.fakeid;

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
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.text.ParseException;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IdTokenSigningTests {

    @Test
    void rssSigningByDefault() throws ParseException {

        String code = "abc";
        String nonce = "123";
        String aud = "client";

        FakeIdProvider fakeIdProvider = new FakeIdProvider(Configuration.builder().build());
        AuthRequest request = new AuthRequest();
        request.setNonce(nonce);
        request.setClientId(aud);
        fakeIdProvider.getRequests().put(code, request);
        Context context = Mockito.mock(Context.class);
        when(context.formParam("grant_type")).thenReturn("authorization_code");
        when(context.formParam("scope")).thenReturn("read openid");
        when(context.formParam("code")).thenReturn(code);

        fakeIdProvider.tokenEndpoint(context);
        ArgumentCaptor<Map> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(context).json(contextCaptor.capture());
        Map<String, String> response = (Map<String, String>) contextCaptor.getValue();
        String idToken = response.get("id_token");

        SignedJWT jwt = SignedJWT.parse(idToken);
        JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
        assertEquals(JWSAlgorithm.RS256, algorithm);

    }

    @Test
    void pssSigningWithMyJwks() throws ParseException, JOSEException {

        String code = "abc";
        String nonce = "123";
        String aud = "client";
        RSAKey theJwk = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("signingKey")
                .issueTime(new Date())
                .algorithm(JWSAlgorithm.PS256)
                .generate();
        JWKSet jwks = new JWKSet(theJwk);

        FakeIdProvider fakeIdProvider = new FakeIdProvider(Configuration.builder()
                .jwks(jwks)
                .build());
        AuthRequest request = new AuthRequest();
        request.setNonce(nonce);
        request.setClientId(aud);
        fakeIdProvider.getRequests().put(code, request);
        Context context = Mockito.mock(Context.class);
        when(context.formParam("grant_type")).thenReturn("authorization_code");
        when(context.formParam("scope")).thenReturn("read openid");
        when(context.formParam("code")).thenReturn(code);

        fakeIdProvider.tokenEndpoint(context);
        ArgumentCaptor<Map> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(context).json(contextCaptor.capture());
        Map<String, String> response = (Map<String, String>) contextCaptor.getValue();
        String idToken = response.get("id_token");

        SignedJWT jwt = SignedJWT.parse(idToken);
        JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
        assertEquals(JWSAlgorithm.PS256, algorithm);

        RSASSAVerifier verifier = new RSASSAVerifier(theJwk.toRSAPublicKey());
        assertTrue(jwt.verify(verifier));

    }

    @Test
    void pssSigningWithCreatedJwks() throws ParseException, JOSEException {

        String code = "abc";
        String nonce = "123";
        String aud = "client";

        Configuration cfg = Configuration.builder()
                .signingAlgorithm(JWSAlgorithm.PS256)
                .build();

        FakeIdProvider fakeIdProvider = new FakeIdProvider(cfg);
        AuthRequest request = new AuthRequest();
        request.setNonce(nonce);
        request.setClientId(aud);
        fakeIdProvider.getRequests().put(code, request);
        Context context = Mockito.mock(Context.class);
        when(context.formParam("grant_type")).thenReturn("authorization_code");
        when(context.formParam("scope")).thenReturn("read openid");
        when(context.formParam("code")).thenReturn(code);

        fakeIdProvider.tokenEndpoint(context);
        ArgumentCaptor<Map> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(context).json(contextCaptor.capture());
        Map<String, String> response = (Map<String, String>) contextCaptor.getValue();
        String idToken = response.get("id_token");

        SignedJWT jwt = SignedJWT.parse(idToken);
        JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
        assertEquals(JWSAlgorithm.PS256, algorithm);

        JWKSet jwks = cfg.getJwks();
        JWK theJwk = jwks.getKeys().iterator().next();
        RSASSAVerifier verifier = new RSASSAVerifier(theJwk.toRSAKey().toRSAPublicKey());
        assertTrue(jwt.verify(verifier));

    }

}
