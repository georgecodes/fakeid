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

import static com.elevenware.fakeid.util.TestUtils.getFormDataAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@FakeIdTest
public class ClientCredentialsGrantTests {

    FakeIdApplication app;

    @FakeIdPort
    int port;

    @Test
    void clientCredentialsGrant() throws IOException, InterruptedException {

        HttpClient client = HttpClient.newHttpClient();

        Map<String, String> tokenRequest = Map.of(
                "grant_type", "client_credentials",
                "client_id", "client1",
                "scope", "api:read"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://localhost:%d/token", port)))
                .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(tokenRequest)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        Map<String, Object> tokenResponse = TestUtils.mapper().readValue(response.body(), Map.class);
        assertFalse(tokenResponse.containsKey("id_token"));
        assertTrue(tokenResponse.get("scope").toString().contains("api:read"));
    }


}
