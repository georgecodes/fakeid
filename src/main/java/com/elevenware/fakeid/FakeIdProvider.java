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
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.javalin.http.Context;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;

public class FakeIdProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FakeIdProvider.class);

    private final String baseUrl;
    private final DiscoveryDocument discoveryDocument;
    private final Configuration configuration;
    private Map<String, AuthRequest> requests = new HashMap<>();
    private Map<String, Grant> issuedTokens = new HashMap<>();

    public FakeIdProvider(Configuration configuration) {
        this.baseUrl = configuration.getIssuer();
        this.configuration = configuration;
        this.discoveryDocument = DiscoveryDocument.create(baseUrl);
    }


    public void getDiscoveryDocument(@NotNull Context context) {
        context.json(discoveryDocument);
    }

    public void tokenEndpoint(@NotNull Context context) {
        Map<String, List<String>> params = context.formParamMap();

        String grantTypeName = context.formParam("grant_type");
        String scope = context.formParam("scope");
        String authCode = context.formParam("code");
        AuthRequest request = requests.get(context.formParam("code"));
        String clientId = request.getClientId();
        String idToken = idToken(request.getNonce(), clientId);
        String accessToken = RandomStringUtils.randomAlphanumeric(32);
        Grant grant = new Grant();
        grant.setAccessToken(accessToken);
        grant.setClientId(request.getClientId());
        grant.setSub(configuration.getClaims().get("name").toString());
        issuedTokens.put(accessToken, grant);

        LOG.info("Token issued using grant type {} for client {}", grantTypeName, clientId);
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
            grant.setScope(authRequest.getScopes());
            issuedTokens.put(accessToken, grant);
            responseBuilder.append(separator)
                    .append("token=").append(accessToken);
            separator = '&';
        }
        if (responseType.contains("id_token")) {
            responseBuilder.append(separator)
                    .append("id_token=").append(idToken(authRequest.getNonce(), params.get("client_id").get(0)));
            separator = '&';
        }
        if (authRequest.getState() != null) {
            responseBuilder.append(separator)
                    .append("state=").append(authRequest.getState());
        }

        LOG.info("Authorization response: {}", responseBuilder.toString());
        requests.put(authCode, authRequest);
        String redirect = responseBuilder.toString();
        context.redirect(redirect);
    }

    private String idToken(String nonce, String clientId) {
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
        claimsBuilder.subject(configuration.getClaims().get("name").toString());
        for(Map.Entry<String, Object> claim: configuration.getClaims().entrySet()) {
            claimsBuilder.claim(claim.getKey(), claim.getValue());
        }
        claimsBuilder.claim("nonce", nonce);
        claimsBuilder.claim("iss", configuration.getIssuer());
        claimsBuilder.audience(clientId);
        Instant now = Instant.now();
        claimsBuilder.issueTime(Date.from(now));
        now = now.plus(1L, ChronoUnit.HOURS);
        claimsBuilder.expirationTime(Date.from(now));
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
                    "scope", String.join(" ", grant.getScopes()),
                    "exp", System.currentTimeMillis() / 1000L + 3600,
                    "iat", System.currentTimeMillis() / 1000L
            ));
        } else {
            context.json(Map.of("active", false));
        }
    }
}
