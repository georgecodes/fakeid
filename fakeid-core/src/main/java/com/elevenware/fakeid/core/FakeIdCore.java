package com.elevenware.fakeid.core;

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

import com.elevenware.fakeid.AutoAcceptClientStore;
import com.elevenware.fakeid.Configuration;
import com.elevenware.fakeid.core.dto.AuthorizeRequest;
import com.elevenware.fakeid.core.dto.AuthorizeResponse;
import com.elevenware.fakeid.core.dto.IntrospectRequest;
import com.elevenware.fakeid.core.dto.IntrospectResponse;
import com.elevenware.fakeid.core.dto.TokenRequest;
import com.elevenware.fakeid.core.dto.TokenResponse;
import com.elevenware.fakeid.core.error.UnsupportedGrantTypeException;
import com.nimbusds.jose.jwk.RSAKey;
import com.oidc4j.v2.lib.Provider;
import com.oidc4j.v2.lib.ProviderConfiguration;
import com.oidc4j.v2.lib.SigningKeySource;
import com.oidc4j.v2.lib.model.DiscoveryDocument;
import com.oidc4j.v2.lib.store.ClientStore;
import com.oidc4j.v2.lib.store.InMemoryIssuedGrantStore;
import com.oidc4j.v2.lib.store.InMemoryPendingGrantStore;
import com.oidc4j.v2.lib.store.InMemoryUserStore;
import com.oidc4j.v2.lib.store.IssuedGrant;
import com.oidc4j.v2.lib.store.PendingGrant;
import com.oidc4j.v2.lib.store.User;
import com.oidc4j.v2.lib.store.UserStore;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FakeIdCore {

    private static final Logger LOG = LoggerFactory.getLogger(FakeIdCore.class);

    private final Configuration configuration;
    private final Provider provider;
    private final TokenMinter tokenMinter;
    private final Map<String, String> noncesByCode = new ConcurrentHashMap<>();

    public FakeIdCore(Configuration configuration) {
        this.configuration = configuration;
        this.provider = buildV2Provider(configuration);
        this.tokenMinter = new TokenMinter(provider.getKeySource().getSigningKey(), configuration.getIssuer());
    }

    public TokenResponse token(TokenRequest request) {
        String grantType = request.grantType();
        switch (grantType) {
            case "authorization_code":
                return authCodeGrant(request.code(), request.scope());
            case "client_credentials":
                return clientCredentialsGrant(request.clientId(), request.scope());
            default:
                throw new UnsupportedGrantTypeException(grantType);
        }
    }

    public AuthorizeResponse authorize(AuthorizeRequest request) {
        LOG.info("Auth Request for client {} with scopes {}", request.clientId(), request.scopes());
        String subject = configuration.getClaims().get("sub").toString();
        String responseType = request.responseType();
        String code = null;
        String accessToken = null;
        String idToken = null;

        if (responseType.contains("code")) {
            code = RandomStringUtils.randomAlphanumeric(16);
            savePendingAuthCode(
                    code,
                    request.clientId(),
                    subject,
                    request.scopes(),
                    request.redirectUri(),
                    request.nonce());
        }
        if (responseType.contains("token")) {
            accessToken = RandomStringUtils.randomAlphanumeric(32);
            saveIssuedGrant(request.clientId(), "implicit", request.scopes(), accessToken);
        }
        if (responseType.contains("id_token")) {
            idToken = tokenMinter.mintIdToken(
                    subject,
                    request.clientId(),
                    request.nonce(),
                    configuration.getClaims());
        }
        return new AuthorizeResponse(
                request.redirectUri(),
                code,
                accessToken,
                idToken,
                request.state());
    }

    public DiscoveryDocument discovery() {
        return provider.discoveryDocument();
    }

    public Map<String, Object> jwks() {
        return provider.getKeySource().getPublicJwks().toJSONObject();
    }

    public Map<String, Object> userInfo() {
        return configuration.getClaims();
    }

    public IntrospectResponse introspect(IntrospectRequest request) {
        Optional<IssuedGrant> found = provider.getIssuedGrantStore().findByAccessToken(request.token());
        if (found.isPresent()) {
            IssuedGrant grant = found.get();
            return new IntrospectResponse(
                    true,
                    grant.getClientId(),
                    configuration.getClaims().get("sub").toString(),
                    String.join(" ", grant.getGrantedScopes()),
                    grant.getExpiresAt().getEpochSecond(),
                    grant.getIssuedAt().getEpochSecond());
        }
        return new IntrospectResponse(false, null, null, null, null, null);
    }

    public void savePendingAuthCode(String code, String clientId, String subject,
                                    Set<String> scopes, String redirectUri, String nonce) {
        Instant now = Instant.now();
        PendingGrant pending = new PendingGrant(
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

    private TokenResponse authCodeGrant(String authCode, String scope) {
        PendingGrant pending = provider.getPendingGrantStore()
                .consume(authCode)
                .orElseThrow(() -> new IllegalStateException("Unknown authorization code: " + authCode));
        String clientId = pending.getClientId();
        String idToken = null;
        Set<String> scopes = pending.getConsentedScopes();
        if (scopes == null) {
            scopes = Collections.emptySet();
        }
        if (scope == null) {
            scope = String.join(" ", scopes);
        }
        if (scope.contains("openid")) {
            idToken = tokenMinter.mintIdToken(
                    configuration.getClaims().get("sub").toString(),
                    clientId,
                    noncesByCode.remove(authCode),
                    configuration.getClaims());
        } else {
            noncesByCode.remove(authCode);
        }
        String accessToken = RandomStringUtils.randomAlphanumeric(32);
        saveIssuedGrant(clientId, "authorization_code", scopes, accessToken);

        LOG.info("Token issued using auth code grant for client {}", clientId);
        return new TokenResponse(
                accessToken,
                "Bearer",
                3600,
                scope,
                System.currentTimeMillis() / 1000L,
                clientId,
                "authorization_code",
                idToken);
    }

    private TokenResponse clientCredentialsGrant(String clientId, String scope) {
        String accessToken = RandomStringUtils.randomAlphanumeric(32);
        Set<String> scopes = new HashSet<>();
        if (scope != null) {
            for (String s : scope.split(" ")) {
                scopes.add(s);
            }
        } else {
            scope = "";
        }
        saveIssuedGrant(clientId, "client_credentials", scopes, accessToken);

        LOG.info("Token issued using client credentials grant for client {}", clientId);
        return new TokenResponse(
                accessToken,
                "Bearer",
                3600,
                scope,
                System.currentTimeMillis() / 1000L,
                clientId,
                "client_credentials",
                null);
    }

    private void saveIssuedGrant(String clientId, String grantType, Set<String> scopes, String accessToken) {
        Instant now = Instant.now();
        provider.getIssuedGrantStore().save(new IssuedGrant(
                UUID.randomUUID().toString(),
                clientId,
                grantType,
                scopes,
                accessToken,
                null,
                now,
                now.plus(1L, ChronoUnit.HOURS)));
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
}
