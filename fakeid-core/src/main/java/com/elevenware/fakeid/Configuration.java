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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.oidc4j.v2.lib.IdTokenEncrypter;
import com.oidc4j.v2.lib.ProviderConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Configuration {

    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);

    private JWKSet jwks;
    private String issuer;
    private Map<String, Object> claims;
    private int port = 8091;
    private JWSAlgorithm signingAlgorithm;
    private JWEAlgorithm idTokenEncryptionAlgorithm;
    private EncryptionMethod idTokenEncryptionMethod;
    private JWK idTokenEncryptionKey;

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getIssuer() {
        return issuer;
    }

    public JWKSet getJwks() {
        return jwks;
    }

    public void setJwks(JWKSet jwks) {
        this.jwks = jwks;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, Object> claims) {
        if(!claims.containsKey("sub")) {
            throw new ConfigurationException("Claims must contain sub key");
        }
        LOG.info("Setting claims to {}", claims);
        this.claims = claims;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        LOG.info("Setting port to {}", port);
        this.port = port;
    }

    public JWSAlgorithm getSigningAlgorithm() {
        return signingAlgorithm;
    }

    public void setSigningAlgorithm(JWSAlgorithm algorithm) {
        LOG.info("Setting signing algorithm to {}", algorithm);
        this.signingAlgorithm = algorithm;
    }

    /**
     * @return the JWE key-management algorithm applied to id tokens, or {@code null} when
     *         id tokens are signed only
     */
    public JWEAlgorithm getIdTokenEncryptionAlgorithm() {
        return idTokenEncryptionAlgorithm;
    }

    public void setIdTokenEncryptionAlgorithm(JWEAlgorithm algorithm) {
        LOG.info("Setting id token encryption algorithm to {}", algorithm);
        this.idTokenEncryptionAlgorithm = algorithm;
    }

    /**
     * @return the JWE content-encryption method applied to id tokens; defaults to
     *         {@code A128CBC-HS256} once an algorithm is configured
     */
    public EncryptionMethod getIdTokenEncryptionMethod() {
        return idTokenEncryptionMethod;
    }

    public void setIdTokenEncryptionMethod(EncryptionMethod method) {
        LOG.info("Setting id token encryption method to {}", method);
        this.idTokenEncryptionMethod = method;
    }

    /**
     * @return the relying party's key that id tokens are encrypted to. When Fake ID generated
     *         the key pair itself this includes the private half, so a test can decrypt with it.
     */
    public JWK getIdTokenEncryptionKey() {
        return idTokenEncryptionKey;
    }

    public void setIdTokenEncryptionKey(JWK key) {
        LOG.info("Setting id token encryption key with kid {}", key == null ? null : key.getKeyID());
        this.idTokenEncryptionKey = key;
    }

    public boolean isIdTokenEncryptionEnabled() {
        return idTokenEncryptionAlgorithm != null;
    }

    /**
     * Jackson entry point for the {@code idTokenEncryption} block of the JSON config file:
     * <pre>{@code
     * "idTokenEncryption": { "alg": "RSA-OAEP-256", "enc": "A256GCM", "key": { ...JWK... } }
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    public void setIdTokenEncryption(Map<String, Object> block) {
        if (block == null) {
            return;
        }
        for (String name : block.keySet()) {
            if (!Set.of("alg", "enc", "key").contains(name)) {
                throw new ConfigurationException(
                        "Unknown idTokenEncryption property '" + name + "': expected alg, enc and/or key");
            }
        }
        Object alg = block.get("alg");
        if (alg != null) {
            setIdTokenEncryptionAlgorithm(JWEAlgorithm.parse(alg.toString()));
        }
        Object enc = block.get("enc");
        if (enc != null) {
            setIdTokenEncryptionMethod(EncryptionMethod.parse(enc.toString()));
        }
        Object key = block.get("key");
        if (key != null) {
            if (!(key instanceof Map)) {
                throw new ConfigurationException("idTokenEncryption.key must be a JWK object");
            }
            try {
                setIdTokenEncryptionKey(JWK.parse((Map<String, Object>) key));
            } catch (ParseException e) {
                throw new ConfigurationException("Failed to parse idTokenEncryption.key as a JWK: " + e.getMessage());
            }
        }
    }

    public static Configuration loadFromFile(String filePath) {
        LOG.info("Loading configuration from file {}", filePath);
        Configuration configuration;
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(JWKSet.class, new JWKSetJsonHandler());
        objectMapper.registerModule(module);
        try {
            configuration = objectMapper.readValue(new java.io.File(filePath), Configuration.class);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to load configuration from file: " + filePath, e);
        }
        setDefaultIssuer(configuration);
        setDefaultClaims(configuration);
        setDefaultSigningAlgorithm(configuration);
        setDefaultJwks(configuration);
        setDefaultIdTokenEncryption(configuration);
        return configuration;
    }

    private static void setDefaultSigningAlgorithm(Configuration configuration) {
        LOG.info("Setting default signing algorithm");
        if(configuration.getSigningAlgorithm() != null) {
            LOG.info("Signing algorithm already set to {} - using that", configuration.getSigningAlgorithm());
            return;
        }
        String setSigningAlgorithm = System.getenv("FAKEID_SIGNING_ALG");
        if(setSigningAlgorithm == null) {
            setSigningAlgorithm = System.getenv("FAKEID_SIGNING_ALGORITHM");
        }
        if(setSigningAlgorithm != null) {
            LOG.info("Setting default signing algorithm to {}", setSigningAlgorithm);
            JWSAlgorithm algorithm = JWSAlgorithm.parse(setSigningAlgorithm);
            configuration.setSigningAlgorithm(algorithm);
        } else {
            LOG.info("No signing algorithm set, setting to RS256");
            configuration.setSigningAlgorithm(JWSAlgorithm.RS256);
        }
    }

    public static Configuration defaultConfiguration() {
        Configuration configuration = new Configuration();
        setDefaultSigningAlgorithm(configuration);
        setDefaultIssuer(configuration);
        setDefaultClaims(configuration);
        setDefaultJwks(configuration);
        setDefaultIdTokenEncryption(configuration);
        return configuration;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void setDefaultJwks(Configuration configuration) {
        if(configuration.getJwks() != null) {
            return;
        }
        String setSigningKey = System.getenv("FAKEID_SIGNING_KEY");
        if(setSigningKey != null) {
            try {
                String pem = new String(Base64.getDecoder().decode(setSigningKey), StandardCharsets.UTF_8);
                JWK jwk = loadJwkFromPem(pem, configuration.getSigningAlgorithm());
                configuration.setJwks(new JWKSet(jwk));
                return;
            } catch (JOSEException e) {
                throw new ConfigurationException("Failed to parse FAKEID_SIGNING_KEY: " + e.getMessage());
            }
        }
        try {
            JWK jwk = generateSigningKey(configuration.getSigningAlgorithm());
            configuration.setJwks(new JWKSet(jwk));
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    // 2048-bit RSA is the standard minimum for a dev/test OIDC mock
    private static final int DEFAULT_RSA_KEY_SIZE = 2048;

    private static JWK loadJwkFromPem(String pem, JWSAlgorithm algorithm) throws JOSEException {
        JWK parsed = parsePemToJwk(pem);
        validateKeyAlgorithmCompatibility(parsed, algorithm);
        if (parsed instanceof ECKey) {
            ECKey ecKey = (ECKey) parsed;
            Curve expected = curveForEcAlgorithm(algorithm);
            if (!expected.equals(ecKey.getCurve())) {
                throw new ConfigurationException(
                        "EC key curve " + ecKey.getCurve() + " does not match algorithm " + algorithm +
                        " (expected curve " + expected + ")");
            }
            return new ECKey.Builder(ecKey.getCurve(), ecKey.toECPublicKey())
                    .privateKey(ecKey.toECPrivateKey())
                    .algorithm(algorithm)
                    .keyID("signingKey")
                    .keyUse(KeyUse.SIGNATURE)
                    .build();
        }
        RSAKey rsaKey = (RSAKey) parsed;
        return new RSAKey.Builder(rsaKey.toRSAPublicKey())
                .privateKey(rsaKey.toRSAPrivateKey())
                .algorithm(algorithm)
                .keyID("signingKey")
                .keyUse(KeyUse.SIGNATURE)
                .build();
    }

    // Detects key type from PEM header rather than relying on the algorithm hint,
    // so a key/algorithm mismatch produces a clear diagnostic rather than a parse error.
    private static JWK parsePemToJwk(String pem) throws JOSEException {
        if (pem.contains("EC PRIVATE KEY") || pem.contains("EC PARAMETERS")) {
            return ECKey.parseFromPEMEncodedObjects(pem);
        }
        if (pem.contains("RSA PRIVATE KEY")) {
            return RSAKey.parseFromPEMEncodedObjects(pem);
        }
        // PKCS#8 ("BEGIN PRIVATE KEY"): key type is embedded in the DER, not the header.
        // Try RSA first; fall back to EC.
        try {
            return RSAKey.parseFromPEMEncodedObjects(pem);
        } catch (JOSEException e) {
            return ECKey.parseFromPEMEncodedObjects(pem);
        }
    }

    private static void validateKeyAlgorithmCompatibility(JWK key, JWSAlgorithm algorithm) {
        boolean ecKey = key instanceof ECKey;
        boolean ecAlg = isEcAlgorithm(algorithm);
        if (ecKey && !ecAlg) {
            throw new ConfigurationException(
                    "EC key provided but a non-EC algorithm is configured (" + algorithm + "). " +
                    "Use an EC algorithm (ES256, ES384, ES512) or supply an RSA key.");
        }
        if (!ecKey && ecAlg) {
            throw new ConfigurationException(
                    "RSA key provided but an EC algorithm is configured (" + algorithm + "). " +
                    "Use an RSA algorithm (RS256, RS384, RS512, PS256, PS384, PS512) or supply an EC key.");
        }
    }

    private static JWK generateSigningKey(JWSAlgorithm algorithm) throws JOSEException {
        if (isEcAlgorithm(algorithm)) {
            Curve curve = curveForEcAlgorithm(algorithm);
            return new ECKeyGenerator(curve)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID("signingKey")
                    .issueTime(new Date())
                    .algorithm(algorithm)
                    .generate();
        }
        return new RSAKeyGenerator(DEFAULT_RSA_KEY_SIZE)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("signingKey")
                .issueTime(new Date())
                .algorithm(algorithm)
                .generate();
    }

    private static boolean isEcAlgorithm(JWSAlgorithm algorithm) {
        return JWSAlgorithm.Family.EC.contains(algorithm);
    }

    private static Curve curveForEcAlgorithm(JWSAlgorithm algorithm) {
        if (JWSAlgorithm.ES256.equals(algorithm)) return Curve.P_256;
        if (JWSAlgorithm.ES384.equals(algorithm)) return Curve.P_384;
        if (JWSAlgorithm.ES512.equals(algorithm)) return Curve.P_521;
        throw new ConfigurationException("Unsupported EC algorithm: " + algorithm);
    }

    static final String DEFAULT_ENCRYPTION_KEY_ID = "idTokenEncryptionKey";

    private static final String RSA_ENCRYPTION_ALGS = "RSA-OAEP, RSA-OAEP-256, RSA-OAEP-384, RSA-OAEP-512";
    private static final String EC_ENCRYPTION_ALGS = "ECDH-ES, ECDH-ES+A128KW, ECDH-ES+A192KW, ECDH-ES+A256KW";

    /**
     * Fills in id token encryption settings from the environment where they have not been set
     * explicitly, then validates the result and generates a recipient key pair if an algorithm was
     * configured without a key.
     */
    private static void setDefaultIdTokenEncryption(Configuration configuration) {
        if (configuration.getIdTokenEncryptionAlgorithm() == null) {
            String alg = System.getenv("FAKEID_ID_TOKEN_ENCRYPTION_ALG");
            if (alg == null) {
                alg = System.getenv("FAKEID_ID_TOKEN_ENCRYPTION_ALGORITHM");
            }
            if (alg != null) {
                configuration.setIdTokenEncryptionAlgorithm(JWEAlgorithm.parse(alg));
            }
        }
        if (configuration.getIdTokenEncryptionMethod() == null) {
            String enc = System.getenv("FAKEID_ID_TOKEN_ENCRYPTION_ENC");
            if (enc != null) {
                configuration.setIdTokenEncryptionMethod(EncryptionMethod.parse(enc));
            }
        }
        if (configuration.getIdTokenEncryptionKey() == null) {
            String key = System.getenv("FAKEID_ID_TOKEN_ENCRYPTION_KEY");
            if (key != null) {
                configuration.setIdTokenEncryptionKey(parseEncryptionKey(key));
            }
        }
        applyIdTokenEncryptionDefaults(configuration);
    }

    static void applyIdTokenEncryptionDefaults(Configuration configuration) {
        JWEAlgorithm alg = configuration.idTokenEncryptionAlgorithm;
        if (alg == null) {
            if (configuration.idTokenEncryptionMethod != null) {
                throw new ConfigurationException(
                        "An id token encryption method (enc) was configured without an algorithm (alg). " +
                        "Set FAKEID_ID_TOKEN_ENCRYPTION_ALG / idTokenEncryption.alg as well.");
            }
            if (configuration.idTokenEncryptionKey != null) {
                throw new ConfigurationException(
                        "An id token encryption key was configured without an algorithm (alg). " +
                        "Set FAKEID_ID_TOKEN_ENCRYPTION_ALG / idTokenEncryption.alg as well.");
            }
            return;
        }
        validateEncryptionAlgorithm(alg);
        if (configuration.idTokenEncryptionMethod == null) {
            configuration.idTokenEncryptionMethod = IdTokenEncrypter.DEFAULT_ENCRYPTION_METHOD;
        }
        EncryptionMethod enc = configuration.idTokenEncryptionMethod;
        if (!ProviderConfiguration.SUPPORTED_ID_TOKEN_ENCRYPTION_ENCS.contains(enc.getName())) {
            throw new ConfigurationException(
                    "Unsupported id token encryption method '" + enc + "'. Supported methods are " +
                    String.join(", ", ProviderConfiguration.SUPPORTED_ID_TOKEN_ENCRYPTION_ENCS));
        }
        JWK key = configuration.idTokenEncryptionKey;
        if (key == null) {
            key = generateEncryptionKey(alg);
            LOG.warn("No id token encryption key supplied; generated a {} key pair. Give this private JWK to " +
                     "your relying party so it can decrypt id tokens: {}", key.getKeyType(), key.toJSONString());
        }
        configuration.idTokenEncryptionKey = normaliseEncryptionKey(key, alg);
    }

    private static void validateEncryptionAlgorithm(JWEAlgorithm alg) {
        if (JWEAlgorithm.RSA1_5.equals(alg)) {
            throw new ConfigurationException(
                    "Id token encryption algorithm RSA1_5 is refused (PKCS#1 v1.5 key transport). Use " +
                    RSA_ENCRYPTION_ALGS + ".");
        }
        if (!ProviderConfiguration.SUPPORTED_ID_TOKEN_ENCRYPTION_ALGS.contains(alg.getName())) {
            throw new ConfigurationException(
                    "Unsupported id token encryption algorithm '" + alg + "'. Supported algorithms are " +
                    RSA_ENCRYPTION_ALGS + ", " + EC_ENCRYPTION_ALGS + ".");
        }
        if (!isRsaEncryptionAlgorithm(alg) && !isEcdhEncryptionAlgorithm(alg)) {
            throw new ConfigurationException(
                    "Symmetric id token encryption algorithm '" + alg + "' is not supported: Fake ID accepts " +
                    "any client without a registered secret, so there is no secret to derive a key from. Use " +
                    RSA_ENCRYPTION_ALGS + ", " + EC_ENCRYPTION_ALGS + ".");
        }
    }

    private static boolean isRsaEncryptionAlgorithm(JWEAlgorithm alg) {
        return JWEAlgorithm.Family.RSA.contains(alg);
    }

    private static boolean isEcdhEncryptionAlgorithm(JWEAlgorithm alg) {
        return JWEAlgorithm.Family.ECDH_ES.contains(alg);
    }

    private static JWK generateEncryptionKey(JWEAlgorithm alg) {
        try {
            if (isRsaEncryptionAlgorithm(alg)) {
                return new RSAKeyGenerator(DEFAULT_RSA_KEY_SIZE)
                        .keyUse(KeyUse.ENCRYPTION)
                        .keyID(DEFAULT_ENCRYPTION_KEY_ID)
                        .issueTime(new Date())
                        .algorithm(alg)
                        .generate();
            }
            return new ECKeyGenerator(Curve.P_256)
                    .keyUse(KeyUse.ENCRYPTION)
                    .keyID(DEFAULT_ENCRYPTION_KEY_ID)
                    .issueTime(new Date())
                    .algorithm(alg)
                    .generate();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate id token encryption key", e);
        }
    }

    /**
     * Checks the supplied key suits the algorithm and returns a copy carrying {@code use=enc}, the
     * configured {@code alg} and a {@code kid}, which is the shape oidc4j selects recipient keys by.
     */
    private static JWK normaliseEncryptionKey(JWK key, JWEAlgorithm alg) {
        if (isRsaEncryptionAlgorithm(alg) && !(key instanceof RSAKey)) {
            throw new ConfigurationException(
                    "Id token encryption algorithm " + alg + " requires an RSA key but a " + key.getKeyType() +
                    " key was supplied. Use one of " + EC_ENCRYPTION_ALGS + " with an EC key, or supply an RSA key.");
        }
        if (isEcdhEncryptionAlgorithm(alg) && !(key instanceof ECKey)) {
            throw new ConfigurationException(
                    "Id token encryption algorithm " + alg + " requires an EC key but a " + key.getKeyType() +
                    " key was supplied. Use one of " + RSA_ENCRYPTION_ALGS + " with an RSA key, or supply an EC key.");
        }
        if (key instanceof ECKey) {
            Curve curve = ((ECKey) key).getCurve();
            if (!Curve.P_256.equals(curve) && !Curve.P_384.equals(curve) && !Curve.P_521.equals(curve)) {
                throw new ConfigurationException(
                        "Id token encryption EC key uses curve " + curve + "; only P-256, P-384 and P-521 are supported");
            }
        }
        if (key.getAlgorithm() != null && !alg.getName().equals(key.getAlgorithm().getName())) {
            throw new ConfigurationException(
                    "Id token encryption key declares alg '" + key.getAlgorithm() + "' but the configured " +
                    "algorithm is " + alg + ". Remove alg from the key or make them match.");
        }
        if (key.getKeyUse() != null && !KeyUse.ENCRYPTION.equals(key.getKeyUse())) {
            throw new ConfigurationException(
                    "Id token encryption key has use '" + key.getKeyUse() + "' but an encryption key (use=enc) " +
                    "is required. Do not reuse the signing key.");
        }
        String kid = key.getKeyID() != null ? key.getKeyID() : DEFAULT_ENCRYPTION_KEY_ID;
        if (key instanceof RSAKey) {
            return new RSAKey.Builder((RSAKey) key)
                    .keyUse(KeyUse.ENCRYPTION)
                    .algorithm(alg)
                    .keyID(kid)
                    .build();
        }
        return new ECKey.Builder((ECKey) key)
                .keyUse(KeyUse.ENCRYPTION)
                .algorithm(alg)
                .keyID(kid)
                .build();
    }

    /**
     * Parses the value of {@code FAKEID_ID_TOKEN_ENCRYPTION_KEY}: base64 of either a JWK JSON object
     * or a PEM-encoded public key (RSA or EC). A PEM private key is also accepted; only its public
     * half is used for encryption.
     */
    static JWK parseEncryptionKey(String encoded) {
        String decoded;
        try {
            String compact = encoded.replaceAll("\\s+", "");
            decoded = new String(Base64.getDecoder().decode(compact), StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("FAKEID_ID_TOKEN_ENCRYPTION_KEY must be base64 encoded: " + e.getMessage());
        }
        try {
            if (decoded.startsWith("{")) {
                return JWK.parse(decoded);
            }
            if (decoded.contains("-----BEGIN")) {
                return parsePemToJwk(decoded);
            }
        } catch (ParseException | JOSEException e) {
            throw new ConfigurationException("Failed to parse FAKEID_ID_TOKEN_ENCRYPTION_KEY: " + e.getMessage());
        }
        throw new ConfigurationException(
                "FAKEID_ID_TOKEN_ENCRYPTION_KEY must decode to either a JWK JSON object or a PEM-encoded key");
    }

    private static void setDefaultClaims(Configuration configuration) {
        if(configuration.getClaims() != null) {
            return;
        }
        String sampleClaims = System.getenv("FAKEID_SAMPLE_CLAIMS");
        if(sampleClaims == null) {
            sampleClaims = System.getenv("FAKEID_SAMPLE_JWT");
        }
        if(sampleClaims != null) {
            String[] split = sampleClaims.split("\\.");
            switch(split.length) {
                case 3:
                    configuration.setClaims(parseSignedJwt(sampleClaims));
                    return;
                case 1:
                    configuration.setClaims(parseUnsignedJwt(sampleClaims));
                    return;
                default:
                    throw new RuntimeException("Invalid claims format: " + sampleClaims);
            }
        }
        configuration.setClaims(Map.of(
                "name", "John C. Developer",
                "sub", "John C. Developer",
                "email", "john@developer.com"));
    }

    private static Map<String, Object> parseUnsignedJwt(String sampleClaims) {
        sampleClaims = new String(Base64.getDecoder().decode(sampleClaims));
        try {
            return new ObjectMapper().readValue(sampleClaims, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> parseSignedJwt(String sampleClaims) {
        try {
            JWT jwt = JWTParser.parse(sampleClaims);
            return jwt.getJWTClaimsSet().getClaims();

        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setDefaultIssuer(Configuration configuration) {
        if(configuration.getIssuer() != null) {
            return;
        }
        String setIssuer = System.getenv("FAKEID_ISSUER");
        if(setIssuer != null) {
            configuration.setIssuer(setIssuer);
            return;
        }
        configuration.setIssuer(String.format("http://localhost:%d", configuration.getPort()));
    }

    public static class Builder {

        private boolean built;
        private int port = -1;
        private String issuer;
        private JWKSet jwks;
        private Map<String, Object> claims;
        private JWSAlgorithm algorithm = JWSAlgorithm.RS256;
        private JWEAlgorithm idTokenEncryptionAlgorithm;
        private EncryptionMethod idTokenEncryptionMethod;
        private JWK idTokenEncryptionKey;

        public Configuration build() {
            if(built) {
                throw new IllegalStateException("This has already been built");
            }
            built = true;
            Configuration configuration = new Configuration();
            if(port == 0) {
                try {
                    ServerSocket serverSocket = new ServerSocket(0);
                    port = serverSocket.getLocalPort();
                    serverSocket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            configuration.setSigningAlgorithm(algorithm);
            if( port != -1) {
                configuration.setPort(port);
            }
            if(issuer != null) {
                configuration.setIssuer(issuer);
            } else {
                setDefaultIssuer(configuration);
            }
            if(jwks != null) {
                configuration.setJwks(jwks);
            } else {
                setDefaultJwks(configuration);
            }
            if(claims != null) {
                configuration.setClaims(claims);
            } else {
                setDefaultClaims(configuration);
            }
            if(idTokenEncryptionAlgorithm != null) {
                configuration.setIdTokenEncryptionAlgorithm(idTokenEncryptionAlgorithm);
            }
            if(idTokenEncryptionMethod != null) {
                configuration.setIdTokenEncryptionMethod(idTokenEncryptionMethod);
            }
            if(idTokenEncryptionKey != null) {
                configuration.setIdTokenEncryptionKey(idTokenEncryptionKey);
            }
            setDefaultIdTokenEncryption(configuration);

            return configuration;
        }

        public Builder jwks(JWKSet jwks) {
            this.jwks = jwks;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder claims(Map<String, Object> claims) {
            this.claims = claims;
            return this;
        }

        public Builder signingAlgorithm(JWSAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        /**
         * Encrypts id tokens with the given JWE algorithm. The content-encryption method defaults to
         * {@code A128CBC-HS256} and a recipient key pair is generated unless one is supplied via
         * {@link #idTokenEncryptionKey(JWK)}.
         */
        public Builder idTokenEncryptionAlgorithm(JWEAlgorithm algorithm) {
            this.idTokenEncryptionAlgorithm = algorithm;
            return this;
        }

        public Builder idTokenEncryptionMethod(EncryptionMethod method) {
            this.idTokenEncryptionMethod = method;
            return this;
        }

        /**
         * The relying party's RSA or EC key that id tokens are encrypted to. Only the public half is
         * used; a full key pair is accepted so a test can keep the private half for decryption.
         */
        public Builder idTokenEncryptionKey(JWK key) {
            this.idTokenEncryptionKey = key;
            return this;
        }

        public Builder idTokenEncryption(JWEAlgorithm algorithm, EncryptionMethod method, JWK key) {
            return idTokenEncryptionAlgorithm(algorithm)
                    .idTokenEncryptionMethod(method)
                    .idTokenEncryptionKey(key);
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder randomPort() {
            return port(0);
        }
    }

}
