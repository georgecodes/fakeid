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
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IdTokenEncryptionConfigurationTests {

    @Test
    void encryptionIsOffByDefault() {
        Configuration cfg = Configuration.builder().build();

        assertFalse(cfg.isIdTokenEncryptionEnabled());
        assertNull(cfg.getIdTokenEncryptionAlgorithm());
        assertNull(cfg.getIdTokenEncryptionMethod());
        assertNull(cfg.getIdTokenEncryptionKey());
    }

    @Test
    void builderWithAlgorithmAndRsaKey() throws Exception {
        RSAKey rpKey = new RSAKeyGenerator(2048).keyID("rp-enc").generate();

        Configuration cfg = Configuration.builder()
                .idTokenEncryption(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM, rpKey)
                .build();

        assertTrue(cfg.isIdTokenEncryptionEnabled());
        assertEquals(JWEAlgorithm.RSA_OAEP_256, cfg.getIdTokenEncryptionAlgorithm());
        assertEquals(EncryptionMethod.A256GCM, cfg.getIdTokenEncryptionMethod());
        JWK key = cfg.getIdTokenEncryptionKey();
        assertEquals("rp-enc", key.getKeyID());
        assertEquals(KeyUse.ENCRYPTION, key.getKeyUse());
        assertEquals(JWEAlgorithm.RSA_OAEP_256, key.getAlgorithm());
        assertTrue(key.isPrivate(), "the supplied private half is retained so tests can decrypt");
        assertEquals(rpKey.getModulus(), key.toRSAKey().getModulus());
    }

    @Test
    void methodDefaultsToA128CbcHs256() throws Exception {
        Configuration cfg = Configuration.builder()
                .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP_256)
                .build();

        assertEquals(EncryptionMethod.A128CBC_HS256, cfg.getIdTokenEncryptionMethod());
    }

    @Test
    void algorithmOnlyGeneratesRsaKeyPair() {
        Configuration cfg = Configuration.builder()
                .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP)
                .build();

        JWK key = cfg.getIdTokenEncryptionKey();
        assertInstanceOf(RSAKey.class, key);
        assertTrue(key.isPrivate());
        assertEquals(KeyUse.ENCRYPTION, key.getKeyUse());
        assertEquals(JWEAlgorithm.RSA_OAEP, key.getAlgorithm());
        assertEquals(Configuration.DEFAULT_ENCRYPTION_KEY_ID, key.getKeyID());
    }

    @Test
    void algorithmOnlyGeneratesP256KeyPairForEcdh() {
        Configuration cfg = Configuration.builder()
                .idTokenEncryptionAlgorithm(JWEAlgorithm.ECDH_ES_A256KW)
                .build();

        JWK key = cfg.getIdTokenEncryptionKey();
        assertInstanceOf(ECKey.class, key);
        assertEquals(Curve.P_256, key.toECKey().getCurve());
        assertTrue(key.isPrivate());
        assertEquals(KeyUse.ENCRYPTION, key.getKeyUse());
    }

    @Test
    void keyWithoutAlgorithmIsRejected() throws Exception {
        RSAKey rpKey = new RSAKeyGenerator(2048).generate();

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder().idTokenEncryptionKey(rpKey).build());

        assertTrue(ex.getMessage().contains("without an algorithm"), ex.getMessage());
    }

    @Test
    void methodWithoutAlgorithmIsRejected() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder().idTokenEncryptionMethod(EncryptionMethod.A256GCM).build());

        assertTrue(ex.getMessage().contains("without an algorithm"), ex.getMessage());
    }

    @Test
    void rsaAlgorithmWithEcKeyIsRejected() throws Exception {
        ECKey ecKey = new ECKeyGenerator(Curve.P_256).generate();

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder()
                        .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP_256)
                        .idTokenEncryptionKey(ecKey)
                        .build());

        assertTrue(ex.getMessage().contains("requires an RSA key"), ex.getMessage());
    }

    @Test
    void ecdhAlgorithmWithRsaKeyIsRejected() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).generate();

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder()
                        .idTokenEncryptionAlgorithm(JWEAlgorithm.ECDH_ES)
                        .idTokenEncryptionKey(rsaKey)
                        .build());

        assertTrue(ex.getMessage().contains("requires an EC key"), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A128KW", "A256KW", "A128GCMKW", "dir"})
    void symmetricAlgorithmsAreRejected(String alg) {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder().idTokenEncryptionAlgorithm(JWEAlgorithm.parse(alg)).build());

        assertTrue(ex.getMessage().contains("Symmetric"), ex.getMessage());
    }

    @Test
    void rsa15IsRejected() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder().idTokenEncryptionAlgorithm(JWEAlgorithm.RSA1_5).build());

        assertTrue(ex.getMessage().contains("RSA1_5"), ex.getMessage());
    }

    @Test
    void unknownAlgorithmIsRejected() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder().idTokenEncryptionAlgorithm(JWEAlgorithm.parse("XYZ-OAEP")).build());

        assertTrue(ex.getMessage().contains("Unsupported id token encryption algorithm"), ex.getMessage());
    }

    @Test
    void unknownMethodIsRejected() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder()
                        .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP_256)
                        .idTokenEncryptionMethod(EncryptionMethod.parse("A999GCM"))
                        .build());

        assertTrue(ex.getMessage().contains("Unsupported id token encryption method"), ex.getMessage());
    }

    @Test
    void signingKeyCannotBeReusedForEncryption() throws Exception {
        RSAKey sigKey = new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE).generate();

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder()
                        .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP_256)
                        .idTokenEncryptionKey(sigKey)
                        .build());

        assertTrue(ex.getMessage().contains("use=enc"), ex.getMessage());
    }

    @Test
    void keyDeclaringDifferentAlgorithmIsRejected() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).algorithm(JWEAlgorithm.RSA_OAEP).generate();

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.builder()
                        .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP_256)
                        .idTokenEncryptionKey(key)
                        .build());

        assertTrue(ex.getMessage().contains("declares alg"), ex.getMessage());
    }

    @Test
    void keyWithoutKidGetsDefaultKid() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).generate();

        Configuration cfg = Configuration.builder()
                .idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP_256)
                .idTokenEncryptionKey(key)
                .build();

        assertEquals(Configuration.DEFAULT_ENCRYPTION_KEY_ID, cfg.getIdTokenEncryptionKey().getKeyID());
    }

    @Test
    void envKeyParsesBase64RsaPublicPem() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).generate();
        String encoded = base64(pem(rsaKey.toRSAPublicKey()));

        JWK parsed = Configuration.parseEncryptionKey(encoded);

        assertInstanceOf(RSAKey.class, parsed);
        assertFalse(parsed.isPrivate());
        assertEquals(rsaKey.getModulus(), parsed.toRSAKey().getModulus());
    }

    @Test
    void envKeyParsesBase64EcPublicPem() throws Exception {
        ECKey ecKey = new ECKeyGenerator(Curve.P_384).generate();
        String encoded = base64(pem(ecKey.toECPublicKey()));

        JWK parsed = Configuration.parseEncryptionKey(encoded);

        assertInstanceOf(ECKey.class, parsed);
        assertFalse(parsed.isPrivate());
        assertEquals(Curve.P_384, parsed.toECKey().getCurve());
        assertEquals(ecKey.getX(), parsed.toECKey().getX());
    }

    @Test
    void envKeyParsesBase64Jwk() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("from-jwk").generate().toPublicJWK();

        JWK parsed = Configuration.parseEncryptionKey(base64(rsaKey.toJSONString()));

        assertEquals("from-jwk", parsed.getKeyID());
        assertEquals(rsaKey.getModulus(), parsed.toRSAKey().getModulus());
    }

    @Test
    void envKeyParsesWrappedBase64() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("wrapped").generate().toPublicJWK();
        String encoded = base64(rsaKey.toJSONString()).replaceAll("(.{76})", "$1\n");

        JWK parsed = Configuration.parseEncryptionKey(encoded);

        assertEquals("wrapped", parsed.getKeyID());
        assertEquals(rsaKey.getModulus(), parsed.toRSAKey().getModulus());
    }

    @Test
    void envKeyRejectsInvalidBase64() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.parseEncryptionKey("not base64!!"));

        assertTrue(ex.getMessage().contains("base64"), ex.getMessage());
    }

    @Test
    void envKeyRejectsContentThatIsNeitherJwkNorPem() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.parseEncryptionKey(base64("hello world")));

        assertTrue(ex.getMessage().contains("JWK JSON object or a PEM"), ex.getMessage());
    }

    @Test
    void envKeyRejectsMalformedJwk() {
        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                Configuration.parseEncryptionKey(base64("{\"kty\":\"RSA\"}")));

        assertTrue(ex.getMessage().contains("Failed to parse"), ex.getMessage());
    }

    @Test
    void fileBasedWithFullEncryptionBlock(@TempDir File tmp) throws Exception {
        ECKey rpKey = new ECKeyGenerator(Curve.P_256).keyID("rp-ec").generate();
        Map<String, Object> block = new HashMap<>();
        block.put("alg", "ECDH-ES+A128KW");
        block.put("enc", "A256GCM");
        block.put("key", rpKey.toPublicJWK().toJSONObject());
        createConfig(tmp, Map.of("idTokenEncryption", block));

        Configuration cfg = Configuration.loadFromFile(tmp.getPath() + "/config.json");

        assertTrue(cfg.isIdTokenEncryptionEnabled());
        assertEquals(JWEAlgorithm.ECDH_ES_A128KW, cfg.getIdTokenEncryptionAlgorithm());
        assertEquals(EncryptionMethod.A256GCM, cfg.getIdTokenEncryptionMethod());
        JWK key = cfg.getIdTokenEncryptionKey();
        assertEquals("rp-ec", key.getKeyID());
        assertFalse(key.isPrivate());
        assertEquals(rpKey.getX(), key.toECKey().getX());
        // the signing key is still generated as usual alongside
        assertNotNull(cfg.getJwks().getKeyByKeyId("signingKey"));
        assertEquals(JWSAlgorithm.RS256, cfg.getSigningAlgorithm());
    }

    @Test
    void fileBasedWithAlgorithmOnlyGeneratesKey(@TempDir File tmp) throws Exception {
        createConfig(tmp, Map.of("idTokenEncryption", Map.of("alg", "RSA-OAEP-256")));

        Configuration cfg = Configuration.loadFromFile(tmp.getPath() + "/config.json");

        assertEquals(JWEAlgorithm.RSA_OAEP_256, cfg.getIdTokenEncryptionAlgorithm());
        assertEquals(EncryptionMethod.A128CBC_HS256, cfg.getIdTokenEncryptionMethod());
        assertInstanceOf(RSAKey.class, cfg.getIdTokenEncryptionKey());
        assertTrue(cfg.getIdTokenEncryptionKey().isPrivate());
    }

    @Test
    void fileBasedRejectsUnknownEncryptionProperty(@TempDir File tmp) throws Exception {
        createConfig(tmp, Map.of("idTokenEncryption", Map.of("algorithm", "RSA-OAEP-256")));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                Configuration.loadFromFile(tmp.getPath() + "/config.json"));

        ConfigurationException root = rootCause(ex);
        assertTrue(root.getMessage().contains("Unknown idTokenEncryption property 'algorithm'"), root.getMessage());
    }

    @Test
    void fileBasedRejectsNonObjectKey(@TempDir File tmp) throws Exception {
        createConfig(tmp, Map.of("idTokenEncryption", Map.of("alg", "RSA-OAEP-256", "key", "-----BEGIN PUBLIC KEY-----")));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                Configuration.loadFromFile(tmp.getPath() + "/config.json"));

        assertTrue(rootCause(ex).getMessage().contains("must be a JWK object"));
    }

    private static ConfigurationException rootCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        assertInstanceOf(ConfigurationException.class, t);
        return (ConfigurationException) t;
    }

    private static String pem(Key key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void createConfig(File tmp, Map<String, Object> config) throws IOException {
        new ObjectMapper().writeValue(new File(tmp, "config.json"), config);
    }
}
