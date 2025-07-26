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

import com.elevenware.fakeid.util.ConfigModifier;
import com.elevenware.fakeid.util.FakeIdPort;
import com.elevenware.fakeid.util.FakeIdTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static com.elevenware.fakeid.util.TestUtils.mapper;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@FakeIdTest
public class UserInfoTests {

    FakeIdApplication app;

    @FakeIdPort
    int port;

    @Test
    void userInfoEqualsIdToken() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/userinfo"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        Map<String, String> userinfo = mapper().readValue(response.body(), Map.class);
        assertThat(userinfo)
                .isNotEmpty()
                .contains(entry("name", "Ted"))
                .contains(entry("magicClaim", "Perform magic"));
    }

    @ConfigModifier
    void addCustomClaims(Configuration.Builder builder) {
        builder.claims(Map.of("name", "Ted", "magicClaim", "Perform magic", "sub", "Ted"));
    }

}
