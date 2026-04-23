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

import com.elevenware.fakeid.core.FakeIdCore;
import com.elevenware.fakeid.core.dto.AuthorizeRequest;
import com.elevenware.fakeid.core.dto.AuthorizeResponse;
import com.elevenware.fakeid.core.dto.IntrospectRequest;
import com.elevenware.fakeid.core.dto.IntrospectResponse;
import com.elevenware.fakeid.core.dto.TokenRequest;
import com.elevenware.fakeid.core.dto.TokenResponse;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FakeIdProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FakeIdProvider.class);

    private final FakeIdCore core;

    public FakeIdProvider(Configuration configuration) {
        this.core = new FakeIdCore(configuration);
    }

    public void getDiscoveryDocument(@NotNull Context context) {
        context.json(core.discovery());
    }

    public void jwksEndpoint(@NotNull Context context) {
        context.json(core.jwks());
    }

    public void userInfoEndpoint(@NotNull Context context) {
        context.json(core.userInfo());
    }

    public void tokenEndpoint(@NotNull Context context) {

        Map<String, List<String>> params = context.formParamMap();
        String grantTypeName = context.formParam("grant_type");
        String scope = context.formParam("scope");
        String authCode = context.formParam("code");

        LOG.info("Token endpoint request parameters: {}", params);
        LOG.info("Grant type: {}", grantTypeName);

        if (grantTypeName == null) {
            context.status(400).json(Map.of("error", "invalid_request", "error_description", "missing required parameter: grant_type"));
            return;
        }

        ClientCredentials credentials = extractClientCredentials(context);

        if ("client_credentials".equals(grantTypeName) && credentials == null) {
            context.status(401).json(Map.of("error", "invalid_client", "error_description", "client credentials are required"));
            return;
        }

        TokenRequest tokenRequest = new TokenRequest(
                grantTypeName,
                authCode,
                scope,
                credentials == null ? null : credentials.clientId,
                credentials == null ? null : credentials.clientSecret);

        TokenResponse response = core.token(tokenRequest);
        context.json(response);
    }

    public void authorizationEndpoint(@NotNull Context context) {
        Map<String, List<String>> params = context.queryParamMap();
        for (String required : List.of("client_id", "redirect_uri", "response_type", "scope")) {
            if (!hasValidValue(params.get(required))) {
                context.status(400).json(Map.of("error", "invalid_request", "error_description", "missing required parameter: " + required));
                return;
            }
        }
        AuthorizeRequest request = new AuthorizeRequest(
                params.get("client_id").get(0),
                params.get("redirect_uri").get(0),
                params.get("response_type").get(0),
                Set.copyOf(params.get("scope")),
                hasValidValue(params.get("state")) ? params.get("state").get(0) : null,
                params.containsKey("nonce") ? params.get("nonce").get(0) : null);

        AuthorizeResponse response = core.authorize(request);
        String redirect = response.toRedirectLocation();
        LOG.info("Authorization response: {}", redirect);
        context.redirect(redirect);
    }

    public void introspectionEndpoint(@NotNull Context context) {
        Map<String, List<String>> body = context.formParamMap();
        String token = body.get("token").get(0);
        IntrospectResponse response = core.introspect(new IntrospectRequest(token));
        context.json(response);
    }

    public void savePendingAuthCode(String code, String clientId, String subject,
                                    Set<String> scopes, String redirectUri, String nonce) {
        core.savePendingAuthCode(code, clientId, subject, scopes, redirectUri, nonce);
    }

    private ClientCredentials extractClientCredentials(Context context) {
        String authHeader = context.header("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String encoded = authHeader.substring(6).trim();
                String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.ISO_8859_1);
                int colonIdx = decoded.indexOf(':');
                if (colonIdx > 0 && colonIdx < decoded.length() - 1) {
                    return new ClientCredentials(decoded.substring(0, colonIdx), decoded.substring(colonIdx + 1));
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
            return null;
        }
        String clientId = context.formParam("client_id");
        String clientSecret = context.formParam("client_secret");
        if (clientId != null && !clientId.isEmpty() && clientSecret != null && !clientSecret.isEmpty()) {
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

    private boolean hasValidValue(List<String> values) {
        return values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).isEmpty();
    }

}
