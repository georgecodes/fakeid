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
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oidc4j.v2.lib.Provider;
import com.oidc4j.v2.lib.ProviderConfiguration;
import com.oidc4j.v2.lib.SigningKeySource;
import com.oidc4j.v2.lib.store.Client;
import com.oidc4j.v2.lib.store.ClientStore;
import com.oidc4j.v2.lib.store.InMemoryIssuedGrantStore;
import com.oidc4j.v2.lib.store.InMemoryPendingGrantStore;
import com.oidc4j.v2.lib.store.InMemoryUserStore;
import com.oidc4j.v2.lib.store.User;
import com.oidc4j.v2.lib.store.UserStore;
import io.javalin.http.Context;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FakeIdProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FakeIdProvider.class);

    private final Configuration configuration;
    private final Provider provider;
    private final Map<String, String> noncesByCode = new ConcurrentHashMap<>();

    public FakeIdProvider(Configuration configuration) {
        this.configuration = configuration;
        this.provider = buildV2Provider(configuration);
    }

    private static Provider buildV2Provider(Configuration configuration) {
        ProviderConfiguration providerConfig = ProviderConfiguration.builder()
                .issuer(configuration.getIssuer())
                .grantType("authorization_code")
                .grantType("client_credentials")
                .grantType("refresh_token")
                .clientAuthMethod("client_secret_basic")
                .clientAuthMethod("client_secret_post")
                .scope("openid")
                .scope("profile")
                .scope("email")
                .build();

        RSAKey signingKey = (RSAKey) configuration.getJwks().getKeyByKeyId("signingKey");
        SigningKeySource keySource = new SigningKeySource(signingKey);

        ClientStore clientStore = new AutoAcceptClientStore();
        UserStore userStore = new InMemoryUserStore();
        Object subject = configuration.getClaims().get("sub");
        if (subject != null) {
            userStore.save(userFromClaims(subject.toString(), configuration.getClaims()));
        }

        return new Provider(
                providerConfig,
                clientStore,
                new InMemoryPendingGrantStore(),
                new InMemoryIssuedGrantStore(),
                userStore,
                keySource);
    }

    private static User userFromClaims(String subject, Map<String, Object> claims) {
        return new User(
                subject,
                str(claims.get("name")),
                str(claims.get("preferred_username")),
                str(claims.get("given_name")),
                str(claims.get("family_name")),
                str(claims.get("email")),
                Boolean.TRUE.equals(claims.get("email_verified")));
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static final class AutoAcceptClientStore implements ClientStore {
        private final Map<String, Client> clients = new ConcurrentHashMap<>();

        @Override
        public Optional<Client> findById(String id) {
            return Optional.of(clients.computeIfAbsent(id, cid -> new Client(cid, "")));
        }

        @Override
        public void save(Client client) {
            clients.put(client.getId(), client);
        }
    }

    public void getDiscoveryDocument(@NotNull Context context) {
        context.json(provider.discoveryDocument());
    }

    public void userInfoEndpoint(@NotNull Context context) {
        context.json(configuration.getClaims());
    }

    public void tokenEndpoint(@NotNull Context context) {

        Map<String, List<String>> params = context.formParamMap();
        String grantTypeName = context.formParam("grant_type");
        String scope = context.formParam("scope");
        String authCode = context.formParam("code");

        LOG.info("Token endpoint request parameters: {}", params);
        LOG.info("Grant type: {}", grantTypeName);

        if(grantTypeName == null) {
            context.status(400).json(Map.of("error", "invalid_request", "error_description", "missing required parameter: grant_type"));
            return;
        }

        ClientCredentials credentials = extractClientCredentials(context);

        switch (grantTypeName) {
            case "authorization_code":
                context.json(authCodeGrant(authCode, scope));
                break;
            case "client_credentials":
                if(credentials == null) {
                    context.status(401).json(Map.of("error", "invalid_client", "error_description", "client credentials are required"));
                    return;
                }
                Map<String, Object> response = clientCredentialsGrant(credentials.clientId, scope);
                context.json(response);
                break;
            default:
                context.status(400).json(Map.of("error", "unsupported_grant_type", "error_description", "unsupported grant type: " + grantTypeName));
        }

    }

    private ClientCredentials extractClientCredentials(Context context) {
        String authHeader = context.header("Authorization");
        if(authHeader != null && authHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String encoded = authHeader.substring(6).trim();
                String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.ISO_8859_1);
                int colonIdx = decoded.indexOf(':');
                if(colonIdx > 0 && colonIdx < decoded.length() - 1) {
                    return new ClientCredentials(decoded.substring(0, colonIdx), decoded.substring(colonIdx + 1));
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
            return null;
        }
        String clientId = context.formParam("client_id");
        String clientSecret = context.formParam("client_secret");
        if(clientId != null && !clientId.isEmpty() && clientSecret != null && !clientSecret.isEmpty()) {
            return new ClientCredentials(clientId, clientSecret);
        }
        return null;
    }

    private static final class ClientCredentials {
        final String clientId;
        final String clientSecret;
        ClientCredentials(String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }
    }

    public void authorizationEndpoint(@NotNull Context context) {
        Map<String, List<String>> params = context.queryParamMap();
        for(String required : List.of("client_id", "redirect_uri", "response_type", "scope")) {
            if(!hasValidValue(params.get(required))) {
                context.status(400).json(Map.of("error", "invalid_request", "error_description", "missing required parameter: " + required));
                return;
            }
        }
        AuthRequest authRequest = new AuthRequest();
        authRequest.setClientId(params.get("client_id").get(0));
        authRequest.setScopes(Set.copyOf(params.get("scope")));
        authRequest.setRedirectUri(params.get("redirect_uri").get(0));
        authRequest.setResponseType(params.get("response_type").get(0));
        if(hasValidValue(params.get("state"))) {
            authRequest.setState(params.get("state").get(0));
        }
        if(params.containsKey("nonce")) {
            authRequest.setNonce(params.get("nonce").get(0));
        }
        LOG.info("Auth Request for client {} with scopes {}", authRequest.getClientId(), authRequest.getScopes());
        StringBuilder responseBuilder = new StringBuilder()
                .append(authRequest.getRedirectUri());
        char separator = '?';
        String authCode = RandomStringUtils.randomAlphanumeric(16);
        String clientId = params.get("client_id").get(0);
        String subject = configuration.getClaims().get("sub").toString();
        String responseType = authRequest.getResponseType();
        if(responseType.contains("code")) {
            savePendingAuthCode(authCode, clientId, subject, authRequest.getScopes(),
                    authRequest.getRedirectUri(), authRequest.getNonce());
            responseBuilder.append(separator)
                  .append("code=").append(authCode);
            separator = '&';
        }
        if(responseType.contains("token")) {
            String accessToken = RandomStringUtils.randomAlphanumeric(32);
            saveIssuedGrant(clientId, "implicit", authRequest.getScopes(), accessToken);
            responseBuilder.append(separator)
                    .append("token=").append(accessToken);
            separator = '&';
        }
        if (responseType.contains("id_token")) {
            responseBuilder.append(separator)
                    .append("id_token=").append(idToken(authRequest.getNonce(), clientId));
            separator = '&';
        }
        if (authRequest.getState() != null) {
            responseBuilder.append(separator)
                    .append("state=").append(authRequest.getState());
        }

        LOG.info("Authorization response: {}", responseBuilder.toString());
        String redirect = responseBuilder.toString();
        context.redirect(redirect);
    }

    public void savePendingAuthCode(String code, String clientId, String subject,
                                    Set<String> scopes, String redirectUri, String nonce) {
        Instant now = Instant.now();
        com.oidc4j.v2.lib.store.PendingGrant pending = new com.oidc4j.v2.lib.store.PendingGrant(
                code,
                clientId,
                subject,
                scopes == null ? Set.of() : scopes,
                redirectUri,
                null,
                null,
                now,
                now.plus(10L, ChronoUnit.MINUTES));
        pending.grant();
        provider.getPendingGrantStore().save(pending);
        if (nonce != null) {
            noncesByCode.put(code, nonce);
        }
    }

    private String idToken(String nonce, String clientId) {
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
        claimsBuilder.subject(configuration.getClaims().get("sub").toString());
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
        RSAKey signingKey = provider.getKeySource().getSigningKey();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.parse(signingKey.getAlgorithm().getName()))
                .keyID(signingKey.getKeyID())
                .build();
        SignedJWT idToken = new SignedJWT(header, claimsBuilder.build());
        try {
            idToken.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
            return idToken.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    public void jwksEndpoint(@NotNull Context context) {
        context.json(provider.getKeySource().getPublicJwks().toJSONObject());
    }

    public void introspectionEndpoint(@NotNull Context context) {
        Map<String, List<String>> body = context.formParamMap();
        String token = body.get("token").get(0);
        Optional<com.oidc4j.v2.lib.store.IssuedGrant> found = provider.getIssuedGrantStore().findByAccessToken(token);
        if (found.isPresent()) {
            com.oidc4j.v2.lib.store.IssuedGrant grant = found.get();
            context.json(Map.of(
                    "active", true,
                    "client_id", grant.getClientId(),
                    "sub", configuration.getClaims().get("sub").toString(),
                    "scope", String.join(" ", grant.getGrantedScopes()),
                    "exp", grant.getExpiresAt().getEpochSecond(),
                    "iat", grant.getIssuedAt().getEpochSecond()
            ));
        } else {
            context.json(Map.of("active", false));
        }
    }

    private Map<String, Object> authCodeGrant(String authCode, String scope) {
        com.oidc4j.v2.lib.store.PendingGrant pending = provider.getPendingGrantStore()
                .consume(authCode)
                .orElseThrow(() -> new IllegalStateException("Unknown authorization code: " + authCode));
        String clientId = pending.getClientId();
        String idToken = null;
        Set<String> scopes = pending.getConsentedScopes();
        if(scopes == null) {
            scopes = Collections.emptySet();
        }
        if(scope == null) {
            scope = String.join(" ", scopes);
        }
        if(scope.contains("openid")) {
            idToken = idToken(noncesByCode.remove(authCode), clientId);
        } else {
            noncesByCode.remove(authCode);
        }
        String accessToken = RandomStringUtils.randomAlphanumeric(32);
        saveIssuedGrant(clientId, "authorization_code", scopes, accessToken);

        LOG.info("Token issued using auth code grant for client {}", clientId);
        Map<String,Object> res = new HashMap<>();
        res.put("access_token", accessToken);
        res.put("token_type", "Bearer");
        res.put("expires_in", 3600);
        res.put("scope", scope);
        res.put("issued_at", System.currentTimeMillis() / 1000L);
        res.put("client_id", clientId);
        res.put("grant_type", "authorization_code");
        if(idToken != null) {
            res.put("id_token", idToken);
        }
        return res;
    }

    private Map<String, Object> clientCredentialsGrant(String clientId, String scope) {
        String accessToken = RandomStringUtils.randomAlphanumeric(32);
        Set<String> scopes = new HashSet<>();
        if(scope != null){
            for(String s: scope.split(" ")) {
                scopes.add(s);
            }
        } else {
            scope = "";
        }
        saveIssuedGrant(clientId, "client_credentials", scopes, accessToken);

        LOG.info("Token issued using client credentials grant for client {}", clientId);
        Map<String,Object> res = new HashMap<>();
        res.put("access_token", accessToken);
        res.put("token_type", "Bearer");
        res.put("expires_in", 3600);
        res.put("scope", scope);
        res.put("issued_at", System.currentTimeMillis() / 1000L);
        res.put("client_id", clientId);
        res.put("grant_type", "client_credentials");
        return res;
    }

    private void saveIssuedGrant(String clientId, String grantType, Set<String> scopes, String accessToken) {
        Instant now = Instant.now();
        provider.getIssuedGrantStore().save(new com.oidc4j.v2.lib.store.IssuedGrant(
                UUID.randomUUID().toString(),
                clientId,
                grantType,
                scopes,
                accessToken,
                null,
                now,
                now.plus(1L, ChronoUnit.HOURS)));
    }

    private boolean hasValidValue(List<String> values) {
        return values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).isEmpty();
    }

}
