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

import com.elevenware.fakeid.core.dto.TokenResponse;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.SignedJWT;
import io.javalin.http.Context;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the token endpoint the same way {@link IdTokenSigningTests} does and checks that, once
 * encryption is configured, the id_token is a nested JWE the relying party can open.
 */
public class IdTokenEncryptionTests {

    static Stream<Arguments> algorithms() throws Exception {
        return Stream.of(
                Arguments.of(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM,
                        new RSAKeyGenerator(2048).keyID("rp-rsa").generate()),
                Arguments.of(JWEAlgorithm.RSA_OAEP, EncryptionMethod.A128CBC_HS256,
                        new RSAKeyGenerator(2048).keyID("rp-rsa").generate()),
                Arguments.of(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM,
                        new ECKeyGenerator(Curve.P_256).keyID("rp-ec").generate()),
                Arguments.of(JWEAlgorithm.ECDH_ES_A128KW, EncryptionMethod.A128GCM,
                        new ECKeyGenerator(Curve.P_384).keyID("rp-ec").generate()),
                Arguments.of(JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A256CBC_HS512,
                        new ECKeyGenerator(Curve.P_521).keyID("rp-ec").generate()));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void tokenEndpointReturnsEncryptedIdToken(JWEAlgorithm alg, EncryptionMethod enc, JWK rpKey) throws Exception {
        String code = "abc";
        String nonce = "123";
        String aud = "client";

        Configuration cfg = Configuration.builder()
                .signingAlgorithm(JWSAlgorithm.ES256)
                .idTokenEncryption(alg, enc, rpKey)
                .build();
        FakeIdProvider fakeIdProvider = new FakeIdProvider(cfg);
        fakeIdProvider.savePendingAuthCode(code, aud, "sub", Set.of("openid"), null, nonce);
        Context context = Mockito.mock(Context.class);
        when(context.formParam("grant_type")).thenReturn("authorization_code");
        when(context.formParam("scope")).thenReturn("read openid");
        when(context.formParam("code")).thenReturn(code);

        fakeIdProvider.tokenEndpoint(context);
        ArgumentCaptor<TokenResponse> captor = ArgumentCaptor.forClass(TokenResponse.class);
        verify(context).json(captor.capture());
        String idToken = captor.getValue().idToken();

        EncryptedJWT jwe = EncryptedJWT.parse(idToken);
        assertEquals(alg, jwe.getHeader().getAlgorithm());
        assertEquals(enc, jwe.getHeader().getEncryptionMethod());
        assertEquals(rpKey.getKeyID(), jwe.getHeader().getKeyID());
        assertEquals("JWT", jwe.getHeader().getContentType());

        jwe.decrypt(decrypterFor(rpKey));
        SignedJWT inner = jwe.getPayload().toSignedJWT();
        assertEquals(JWSAlgorithm.ES256, inner.getHeader().getAlgorithm());
        ECKey signingKey = cfg.getJwks().getKeyByKeyId("signingKey").toECKey();
        assertTrue(inner.verify(new ECDSAVerifier(signingKey.toECPublicKey())));
        assertEquals(nonce, inner.getJWTClaimsSet().getStringClaim("nonce"));
        assertEquals(aud, inner.getJWTClaimsSet().getAudience().get(0));
    }

    private static JWEDecrypter decrypterFor(JWK key) throws Exception {
        if (key instanceof ECKey) {
            return new ECDHDecrypter((ECKey) key);
        }
        return new RSADecrypter(key.toRSAKey());
    }
}
