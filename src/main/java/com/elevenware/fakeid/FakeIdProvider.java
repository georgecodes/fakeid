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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.oidc4j.v2.lib.Provider;
import com.oidc4j.v2.lib.ProviderConfiguration;
import com.oidc4j.v2.lib.SigningKeySource;
import com.oidc4j.v2.lib.store.AcceptAllClientStore;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FakeIdProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FakeIdProvider.class);

    private final Configuration configuration;
    private final Provider provider;

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
        JWSAlgorithm keyAlg = signingKey.getAlgorithm() != null
                ? JWSAlgorithm.parse(signingKey.getAlgorithm().getName())
                : configuration.getSigningAlgorithm();
        SigningKeySource keySource = new SigningKeySource(signingKey, keyAlg);

        UserStore userStore = new InMemoryUserStore();
        Object subject = configuration.getClaims().get("sub");
        if (subject != null) {
            userStore.save(userFromClaims(subject.toString(), configuration.getClaims()));
        }

        return new Provider(
                providerConfig,
                new AcceptAllClientStore(),
                new InMemoryPendingGrantStore(),
                new InMemoryIssuedGrantStore(),
                userStore,
                keySource);
    }

    private static User userFromClaims(String subject, Map<String, Object> claims) {
        User.Builder builder = User.builder(subject)
                .name(str(claims.get("name")))
                .preferredUsername(str(claims.get("preferred_username")))
                .givenName(str(claims.get("given_name")))
                .familyName(str(claims.get("family_name")))
                .email(str(claims.get("email")))
                .emailVerified(Boolean.TRUE.equals(claims.get("email_verified")));
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            String key = entry.getKey();
            if (!STANDARD_CLAIM_KEYS.contains(key)) {
                builder.claim(key, entry.getValue());
            }
        }
        return builder.build();
    }

    private static final Set<String> STANDARD_CLAIM_KEYS = Set.of(
            "sub", "name", "preferred_username", "given_name", "family_name", "email", "email_verified");

    private static String str(Object v) {
        return v == null ? null : v.toString();
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

        Optional<com.oidc4j.v2.lib.ClientCredentials> credentials = com.oidc4j.v2.lib.ClientAuthenticationParser.parse(
                context.header("Authorization"),
                context.formParam("client_id"),
                context.formParam("client_secret"));

        switch (grantTypeName) {
            case "authorization_code":
                context.json(authCodeGrant(authCode, scope, credentials.map(com.oidc4j.v2.lib.ClientCredentials::getClientId).orElse(null)));
                break;
            case "client_credentials":
                if(credentials.isEmpty() || com.oidc4j.v2.lib.ClientCredentials.NONE.equals(credentials.get().getMethod())) {
                    context.status(401).json(Map.of("error", "invalid_client", "error_description", "client credentials are required"));
                    return;
                }
                Map<String, Object> response = clientCredentialsGrant(credentials.get().getClientId(), scope);
                context.json(response);
                break;
            default:
                context.status(400).json(Map.of("error", "unsupported_grant_type", "error_description", "unsupported grant type: " + grantTypeName));
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
        com.oidc4j.v2.lib.AuthorizationRequest.Builder builder = com.oidc4j.v2.lib.AuthorizationRequest.builder()
                .clientId(params.get("client_id").get(0))
                .responseType(params.get("response_type").get(0))
                .redirectUri(params.get("redirect_uri").get(0))
                .scopes(Set.of(params.get("scope").get(0).split(" ")));
        if (hasValidValue(params.get("state"))) {
            builder.state(params.get("state").get(0));
        }
        if (hasValidValue(params.get("nonce"))) {
            builder.nonce(params.get("nonce").get(0));
        }
        com.oidc4j.v2.lib.AuthorizationRequest request = builder.build();
        String subject = configuration.getClaims().get("sub").toString();
        LOG.info("Auth Request for client {} with scopes {}", request.getClientId(), request.getScopes());
        com.oidc4j.v2.lib.AuthorizationResponse response = provider.authorizeDirectly(request, subject);
        String redirect = response.buildQueryRedirect();
        LOG.info("Authorization response: {}", redirect);
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
                nonce,
                now,
                now.plus(10L, ChronoUnit.MINUTES));
        pending.grant();
        provider.getPendingGrantStore().save(pending);
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

    private Map<String, Object> authCodeGrant(String authCode, String scope, String authClientId) {
        com.oidc4j.v2.lib.OidcContext ctx = new com.oidc4j.v2.lib.OidcContext();
        ctx.setGrantType("authorization_code");
        ctx.setCode(authCode);
        if (authClientId == null) {
            authClientId = provider.getPendingGrantStore().findByCode(authCode)
                    .map(com.oidc4j.v2.lib.store.PendingGrant::getClientId)
                    .orElse(null);
        }
        ctx.setClientId(authClientId);
        ctx.setClientSecret("");
        if (scope != null && !scope.isEmpty()) {
            ctx.setRequestedScopes(Set.of(scope.split(" ")));
        }
        provider.fireTokenRequest(ctx);
        if (ctx.hasErrors()) {
            throw new IllegalStateException("Token request failed: " + ctx.getErrors());
        }
        String clientId = ctx.getAuthenticatedClient() != null
                ? ctx.getAuthenticatedClient().getId()
                : authClientId;
        Set<String> grantedScopes = ctx.getGrantedScopes() == null ? Set.of() : ctx.getGrantedScopes();
        LOG.info("Token issued using auth code grant for client {}", clientId);
        Map<String,Object> res = new HashMap<>();
        res.put("access_token", ctx.getAccessToken());
        res.put("token_type", "Bearer");
        res.put("expires_in", 3600);
        res.put("scope", String.join(" ", grantedScopes));
        res.put("issued_at", System.currentTimeMillis() / 1000L);
        res.put("client_id", clientId);
        res.put("grant_type", "authorization_code");
        if(ctx.getIdToken() != null) {
            res.put("id_token", ctx.getIdToken());
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
