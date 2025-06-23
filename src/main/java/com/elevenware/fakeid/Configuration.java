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
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;

import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class Configuration {

    private JWKSet jwks;
    private String issuer;
    private Map<String, Object> claims;

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
        this.claims = claims;
    }

    public static Configuration loadFromFile(String filePath) {
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
        setDefaultJwks(configuration);
        return configuration;
    }

    public static Configuration defaultConfiguration() {
        Configuration configuration = new Configuration();
        setDefaultIssuer(configuration);
        setDefaultClaims(configuration);
        setDefaultJwks(configuration);
        return configuration;
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
                        .algorithm(Algorithm.parse("RS256"))
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
                    .algorithm(Algorithm.parse("RS256"))
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
        configuration.setIssuer("http://localhost:8091");
    }



}
