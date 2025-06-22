package com.elevenware.fakeid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

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
        configuration.setClaims(Map.of(
                "name", "John C. Developer",
                "email", "john@developer.com"));
    }

    private static void setDefaultIssuer(Configuration configuration) {
        if(configuration.getIssuer() != null) {
            return;
        }
        configuration.setIssuer("http://localhost:8091");
    }



}
