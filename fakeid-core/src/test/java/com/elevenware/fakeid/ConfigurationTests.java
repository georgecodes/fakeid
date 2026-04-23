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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigurationTests {

    @Test
    void zeroConfiguration() {

        Configuration config = Configuration.defaultConfiguration();
        assertNotNull(config.getIssuer());
        assertEquals("http://localhost:8091", config.getIssuer());

        Map<String, Object> claims = config.getClaims();
        assertNotNull(claims);
        assertTrue(claims.containsKey("name"));
        assertTrue(claims.containsKey("email"));
        assertTrue(claims.containsKey("sub"));

        JWKSet jwkSet = config.getJwks();
        assertNotNull(jwkSet);
        assertFalse(jwkSet.isEmpty());
        JWK jwk = jwkSet.getKeyByKeyId("signingKey");
        assertNotNull(jwk);
        assertTrue(jwk.isPrivate());

    }

    @Test
    void randomPort() {

        Configuration config = Configuration.builder().randomPort().build();
        assertNotNull(config.getIssuer());
        assertNotEquals(8081, config.getPort());

        Map<String, Object> claims = config.getClaims();
        assertNotNull(claims);
        assertTrue(claims.containsKey("name"));
        assertTrue(claims.containsKey("email"));

        JWKSet jwkSet = config.getJwks();
        assertNotNull(jwkSet);
        assertFalse(jwkSet.isEmpty());
        JWK jwk = jwkSet.getKeyByKeyId("signingKey");
        assertNotNull(jwk);
        assertTrue(jwk.isPrivate());

    }

    @Test
    void fileBasedWithClaimsOnly(@TempDir File tmp) throws IOException {
        Map<String, Object> claims = Map.of(
                "name", "Jim Dev",
                "email", "jim@example.com",
                "sub", "Jim Dev"
        );
        createConfig(tmp, Map.of("claims", claims));
        Configuration config = Configuration.loadFromFile(tmp.getPath() + "/config.json");
        assertNotNull(config.getIssuer());

        claims = config.getClaims();
        assertNotNull(claims);
        assertTrue(claims.containsKey("name"));
        assertEquals("Jim Dev", claims.get("name"));
        assertTrue(claims.containsKey("email"));
        assertEquals("jim@example.com", claims.get("email"));

        JWKSet jwkSet = config.getJwks();
        assertNotNull(jwkSet);
        assertFalse(jwkSet.isEmpty());
        JWK jwk = jwkSet.getKeyByKeyId("signingKey");
        assertNotNull(jwk);
        assertTrue(jwk.isPrivate());
    }

    @Test
    void claimMustHaveSub(@TempDir File tmp) throws IOException {
        Map<String, Object> claims = Map.of(
                "name", "Jim Dev",
                "email", "jim@example.com"
        );
        createConfig(tmp, Map.of("claims", claims));
        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> Configuration.loadFromFile(tmp.getPath() + "/config.json"));
        Throwable cause = runtimeException.getCause();
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof ConfigurationException);

    }

    @Test
    void fileBasedWithJwksOnly(@TempDir File tmp) throws IOException, JOSEException {
        RSAKey theJwk = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("signingKey")
                .issueTime(new Date())
                .algorithm(Algorithm.parse("RS256"))
                .generate();
        JWKSet jwks = new JWKSet(theJwk);

        createConfig(tmp, Map.of("jwks", jwks.toJSONObject(false)));
        Configuration config = Configuration.loadFromFile(tmp.getPath() + "/config.json");
        assertNotNull(config.getIssuer());

        Map<String, Object> claims = config.getClaims();
        assertNotNull(claims);
        assertTrue(claims.containsKey("name"));
        assertTrue(claims.containsKey("email"));
        assertTrue(claims.containsKey("sub"));

        JWKSet jwkSet = config.getJwks();
        assertNotNull(jwkSet);
        assertFalse(jwkSet.isEmpty());
        JWK jwk = jwkSet.getKeyByKeyId("signingKey");
        RSAKey rsaKey = (RSAKey) jwk;
        assertNotNull(rsaKey);
        assertTrue(rsaKey.isPrivate());
        assertEquals(theJwk.getModulus(), rsaKey.getModulus());
        assertEquals(theJwk.getPublicExponent(), rsaKey.getPublicExponent());
        assertEquals(theJwk.getPrivateExponent(), rsaKey.getPrivateExponent());
    }

    @Test
    void fileBasedWithIssuerOnly(@TempDir File tmp) throws IOException {
        createConfig(tmp, Map.of("issuer", "https://auth.example.localhost.com"));
        Configuration config = Configuration.loadFromFile(tmp.getPath() + "/config.json");
        assertNotNull(config);
        assertEquals("https://auth.example.localhost.com", config.getIssuer());
        Map<String, Object> claims = config.getClaims();
        assertNotNull(claims);
        assertTrue(claims.containsKey("sub"));
        assertTrue(claims.containsKey("name"));
        assertTrue(claims.containsKey("email"));

        JWKSet jwkSet = config.getJwks();
        assertNotNull(jwkSet);
        assertFalse(jwkSet.isEmpty());
        JWK jwk = jwkSet.getKeyByKeyId("signingKey");
        assertNotNull(jwk);
        assertTrue(jwk.isPrivate());
    }

    private void createConfig(File tmp, Map<String, Object> config) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(new FileOutputStream(new File(tmp, "config.json")), config);
    }

}
