package com.elevenware.fakeid.integration;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.util.Map;

import static com.elevenware.fakeid.util.TestUtils.buildQueryMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("IntegrationTest")
public abstract class AbstractIntegrationTest {

    private static ObjectMapper mapper = new ObjectMapper();

    protected Map<String, Object> fetchDiscoveryDocument(String url) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/.well-known/openid-configuration", url)))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> document = mapper.readValue(response.body(), Map.class);
            return document;
        } catch (IOException | InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    protected JWKSet fetchJwks(String url) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/jwks",url)))
                .GET()
                .header("Accept", "application/json")
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JWKSet jwkSet = JWKSet.parse(response.body());
            return jwkSet;
        } catch (IOException | InterruptedException | ParseException e) {
            throw new AssertionError(e);
        }

    }

    protected Map<String, String> implicitAuthRequest(String baseUrl, String state, String nonce, String clientId) throws UnsupportedEncodingException {
        HttpClient client = HttpClient.newHttpClient();

        String authRequest = new StringBuilder()
                .append(String.format("%s/authorize?", baseUrl))
                .append("response_type=code%20id_token&")
                .append("redirect_uri=http://localhost:8000/redirect")
                .append("&client_id=").append(URLEncoder.encode(clientId, "UTF-8"))
                .append("&scope=openid%20email")
                .append("&state=").append(state)
                .append("&nonce=").append(nonce)
                .toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authRequest))
                .GET()
                .build();

        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String redirect = response.headers().firstValue("location").orElse(null);
            assertNotNull(redirect);
            URI redirectUri = URI.create(redirect);
            String query = redirectUri.getQuery();
            return buildQueryMap(query);
        } catch (IOException | InterruptedException e) {
            throw new AssertionError(e);
        }

    }

}
