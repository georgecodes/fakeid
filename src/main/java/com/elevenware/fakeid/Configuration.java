package com.elevenware.fakeid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.nimbusds.jose.jwk.JWKSet;

import java.util.Map;

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
        Configuration configuration = new Configuration();
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
        return configuration;
    }

}
