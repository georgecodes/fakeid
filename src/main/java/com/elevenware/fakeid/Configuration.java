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
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class Configuration {

    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);

    private JWKSet jwks;
    private String issuer;
    private Map<String, Object> claims;
    private int port = 8091;
    private JWSAlgorithm signingAlgorithm;

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

    public static Configuration loadFromFile(String filePath) {
        LOG.info("Loading configuration from file {}", filePath);
        Configuration configuration;
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(JWKSet.class, new JWKSetJsonHandler());
        objectMapper.registerModule(module);
        try {
            configuration = objectMapper.readValue(new java.io.File(filePath), Configuration.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load configuration from file: " + filePath, e);
        }
        setDefaultIssuer(configuration);
        setDefaultClaims(configuration);
        setDefaultSigningAlgorithm(configuration);
        setDefaultJwks(configuration);
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
                setSigningKey = new String(Base64.getDecoder().decode(setSigningKey));
                RSAKey key = RSAKey.parseFromPEMEncodedObjects(setSigningKey).toRSAKey();
                key = new RSAKey.Builder(key.toRSAPublicKey())
                        .privateKey(key.toRSAPrivateKey())
                        .algorithm(configuration.getSigningAlgorithm())
                        .keyID("signingKey")
                        .keyUse(KeyUse.SIGNATURE)
                        .build();
                configuration.setJwks(new JWKSet(key));
                return;
            } catch (JOSEException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            RSAKey jwk = new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID("signingKey")
                    .issueTime(new Date())
                    .algorithm(configuration.getSigningAlgorithm())
                    .generate();
            JWKSet jwks = new JWKSet(jwk);
            configuration.setJwks(jwks);
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
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

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder randomPort() {
            return port(0);
        }
    }

}
