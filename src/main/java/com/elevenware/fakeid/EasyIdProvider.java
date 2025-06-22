package com.elevenware.fakeid;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.javalin.http.Context;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EasyIdProvider {

    private final String baseUrl;
    private final DiscoveryDocument discoveryDocument;
    private final Configuration configuration;
    private Map<String, AuthRequest> requests = new HashMap<>();
    private Map<String, Grant> issuedTokens = new HashMap<>();

    public EasyIdProvider(Configuration configuration) {
        this.baseUrl = configuration.getIssuer();
        this.configuration = configuration;
        this.discoveryDocument = DiscoveryDocument.create(baseUrl);
    }


    public void getDiscoveryDocument(@NotNull Context context) {
        context.json(discoveryDocument);
    }

    public void tokenEndpoint(@NotNull Context context) {
        Map<String, List<String>> params = context.formParamMap();
        String clientId = context.formParam("client_id");
        String clientSecret = context.formParam("client_secret");
        String grantTypeName = context.formParam("grant_type");
        String scope = context.formParam("scope");
        String authCode = context.formParam("code");
        AuthRequest request = requests.get(context.formParam("code"));
        String idToken = idToken(request.getNonce());
        String accessToken = RandomStringUtils.randomAlphanumeric(32);
        Grant grant = new Grant();
        grant.setAccessToken(accessToken);
        grant.setClientId(params.get("client_id").get(0));
        grant.setSub(configuration.getClaims().get("name").toString());
        issuedTokens.put(accessToken, grant);

        context.json(Map.of(
                "access_token", accessToken,
                "token_type", "Bearer",
                "expires_in", 3600,
                "scope", "scope",
                "issued_at", System.currentTimeMillis() / 1000L,
                "client_id", clientId,
                "grant_type", grantTypeName,
                "id_token", idToken
        ));
    }

    public void authorizationEndpoint(@NotNull Context context) {
        Map<String, List<String>> params = context.queryParamMap();
        AuthRequest authRequest = new AuthRequest();
        authRequest.setClientId(params.get("client_id").get(0));
        authRequest.setScopes(Set.copyOf(params.get("scope")));
        authRequest.setRedirectUri(params.get("redirect_uri").get(0));
        authRequest.setResponseType(params.get("response_type").get(0));
        authRequest.setState(params.get("state").get(0));
        authRequest.setNonce(params.get("nonce").get(0));
        StringBuilder responseBuilder = new StringBuilder()
                .append(authRequest.getRedirectUri());
        char separator = '?';
        String authCode = RandomStringUtils.randomAlphanumeric(16);
        String responseType = authRequest.getResponseType();
        if(responseType.contains("code")) {
          responseBuilder.append(separator)
                  .append("code=").append(authCode);
            separator = '&';
        }
        if(responseType.contains("token")) {
            String accessToken = RandomStringUtils.randomAlphanumeric(32);
            Grant grant = new Grant();
            grant.setAccessToken(accessToken);
            grant.setClientId(params.get("client_id").get(0));
            grant.setSub(configuration.getClaims().get("name").toString());
            issuedTokens.put(authCode, grant);
            responseBuilder.append(separator)
                    .append("id_token=").append(accessToken);
            separator = '&';
        }
        if (responseType.contains("id_token")) {
            responseBuilder.append(separator)
                    .append("id_token=").append(idToken(authRequest.getNonce()));
            separator = '&';
        }
        if (authRequest.getState() != null) {
            responseBuilder.append(separator)
                    .append("state=").append(authRequest.getState());
        }

        requests.put(authCode, authRequest);
        String redirect = responseBuilder.toString();
        context.redirect(redirect);
    }

    private String idToken(String nonce) {
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
        claimsBuilder.subject(configuration.getClaims().get("name").toString());
        for(Map.Entry<String, Object> claim: configuration.getClaims().entrySet()) {
            claimsBuilder.claim(claim.getKey(), claim.getValue());
        }
        claimsBuilder.claim("nonce", nonce);
        claimsBuilder.claim("iss", configuration.getIssuer());
        JWK signingKey = configuration.getJwks().getKeyByKeyId("signingKey");
        String alg = signingKey.getAlgorithm().getName();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.parse(alg))
                .keyID(configuration.getJwks().getKeyByKeyId("signingKey").getKeyID())
                .build();
        SignedJWT idToken = new SignedJWT(header, claimsBuilder.build());
        try {
            idToken.sign(new RSASSASigner(signingKey.toRSAKey().toRSAPrivateKey()));
            return idToken.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }


    public void jwksEndpoint(@NotNull Context context) {
        context.json(configuration.getJwks().toPublicJWKSet().toJSONObject(true));
    }

    public void introspectionEndpoint(@NotNull Context context) {
        Map<String, List<String>> body = context.formParamMap();
        String token = body.get("token").get(0);
        Grant grant = issuedTokens.get(token);
        if(grant != null) {
            // In a real implementation, you would check the token validity and other claims
            context.json(Map.of(
                    "active", true,
                    "client_id", grant.getClientId(),
                    "sub", grant.getSub(),
                    "scope", "scope",
                    "exp", System.currentTimeMillis() / 1000L + 3600,
                    "iat", System.currentTimeMillis() / 1000L
            ));
        } else {
            context.json(Map.of("active", false));
        }
    }
}
