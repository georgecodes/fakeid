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

import com.elevenware.fakeid.util.FakeIdPort;
import com.elevenware.fakeid.util.FakeIdTest;
import com.elevenware.fakeid.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@FakeIdTest
public class AuthorizedGrantTests {

    FakeIdApplication app;

    @FakeIdPort
    int port;

    @Test
    void implicitGrantNoIdToken() throws IOException, InterruptedException {

        HttpClient client = HttpClient.newHttpClient();
        String state = "fowurhferg";
        String requestUri = new StringBuilder().append("http://localhost:").append(port).append("/authorize")
                .append("?client_id=").append("client1")
                .append("&response_type=token")
                .append("&redirect_uri=http://whatever.com")
                .append("&scope=profile")
                .append("&state=").append(state)
                .toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUri))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        Optional<String> oLocation = response.headers().firstValue("Location");
        URI location = oLocation.map(URI::create).orElseThrow(AssertionError::new);
        Map<String, String> responseQuery = TestUtils.buildQueryMap(location.getQuery());
        String accessToken = responseQuery.get("token");
        assertEquals(state, responseQuery.get("state"));
        assertNotNull(accessToken);
        assertNull(responseQuery.get("code"));
        assertNull(responseQuery.get("id_code"));

    }

    @Test
    void implicitGrantWithIdToken() throws IOException, InterruptedException {

        HttpClient client = HttpClient.newHttpClient();
        String state = "jhtertrjhe";
        String requestUri = new StringBuilder().append("http://localhost:").append(port).append("/authorize")
                .append("?client_id=").append("client1")
                .append("&response_type=token%20id_token")
                .append("&redirect_uri=http://whatever.com")
                .append("&scope=openid%20profile")
                .append("&state=").append(state)
                .toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUri))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        Optional<String> oLocation = response.headers().firstValue("Location");
        URI location = oLocation.map(URI::create).orElseThrow(AssertionError::new);
        Map<String, String> responseQuery = TestUtils.buildQueryMap(location.getQuery());
        assertEquals(state, responseQuery.get("state"));
        assertNotNull(responseQuery.get("token"));
        assertNull(responseQuery.get("code"));
        assertNotNull(responseQuery.get("id_token"));
    }

    @Test
    void authCodeGrantNoIdToken() throws IOException, InterruptedException {

        HttpClient client = HttpClient.newHttpClient();
        String state = "httt43htr";
        String requestUri = new StringBuilder().append("http://localhost:").append(port).append("/authorize")
                .append("?client_id=").append("client1")
                .append("&response_type=code")
                .append("&redirect_uri=http://whatever.com")
                .append("&scope=profile")
                .append("&state=").append(state)
                .toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUri))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        Optional<String> oLocation = response.headers().firstValue("Location");
        URI location = oLocation.map(URI::create).orElseThrow(AssertionError::new);
        Map<String, String> responseQuery = TestUtils.buildQueryMap(location.getQuery());
        assertEquals(state, responseQuery.get("state"));
        assertNull(responseQuery.get("token"));
        assertNotNull(responseQuery.get("code"));
        assertNull(responseQuery.get("id_code"));

        String code = responseQuery.get("code");

        Map<String, String> tokenRequest = Map.of(
                "code", code,
                "grant_type", "authorization_code",
                "client_id", "client1"

        );

        request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://localhost:%d/token", port)))
                .POST(HttpRequest.BodyPublishers.ofString(TestUtils.getFormDataAsString(tokenRequest)))
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        Map<String, Object> tokenResponse = TestUtils.mapper().readValue(response.body(), Map.class);
        assertFalse(tokenResponse.containsKey("id_token"));
        assertEquals("profile", tokenResponse.get("scope"));
    }

    @Test
    void authCodeGrantIdToken() throws IOException, InterruptedException {

        HttpClient client = HttpClient.newHttpClient();
        String state = "httt43htr";
        String nonce = "gr7y7hr2ger";
        String requestUri = new StringBuilder().append("http://localhost:").append(port).append("/authorize")
                .append("?client_id=").append("client1")
                .append("&response_type=code")
                .append("&redirect_uri=http://whatever.com")
                .append("&scope=profile%20openid")
                .append("&state=").append(state)
                .append("&nonce=").append(nonce)
                .toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUri))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        Optional<String> oLocation = response.headers().firstValue("Location");
        URI location = oLocation.map(URI::create).orElseThrow(AssertionError::new);
        Map<String, String> responseQuery = TestUtils.buildQueryMap(location.getQuery());
        assertEquals(state, responseQuery.get("state"));
        assertNull(responseQuery.get("token"));
        assertNotNull(responseQuery.get("code"));
        assertNull(responseQuery.get("id_code"));

        String code = responseQuery.get("code");

        Map<String, String> tokenRequest = Map.of(
                "code", code,
                "grant_type", "authorization_code",
                "client_id", "client1"

        );

        request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://localhost:%d/token", port)))
                .POST(HttpRequest.BodyPublishers.ofString(TestUtils.getFormDataAsString(tokenRequest)))
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        Map<String, Object> tokenResponse = TestUtils.mapper().readValue(response.body(), Map.class);
        assertTrue(tokenResponse.containsKey("id_token"));
        assertTrue(tokenResponse.get("scope").toString().contains("openid"));
        assertTrue(tokenResponse.get("scope").toString().contains("profile"));
    }

}
