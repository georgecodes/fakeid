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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the encryption metadata over real HTTP, so the snake_case JSON names relying parties
 * read from the discovery document are covered.
 */
@FakeIdTest
public class IdTokenEncryptionDiscoveryTests {

    @FakeIdPort
    int port;

    @Test
    @ConfigModifier("enableEncryption")
    void discoveryAdvertisesEncryptionMetadata() throws IOException, InterruptedException {
        Map<String, Object> doc = fetchDiscovery();

        assertEquals(List.of("RSA-OAEP-256"), doc.get("id_token_encryption_alg_values_supported"));
        assertEquals(List.of("A256GCM"), doc.get("id_token_encryption_enc_values_supported"));
        assertTrue(doc.containsKey("id_token_signing_alg_values_supported"));
    }

    @Test
    void discoveryOmitsEncryptionMetadataByDefault() throws IOException, InterruptedException {
        Map<String, Object> doc = fetchDiscovery();

        assertFalse(doc.containsKey("id_token_encryption_alg_values_supported"));
        assertFalse(doc.containsKey("id_token_encryption_enc_values_supported"));
    }

    @ConfigModifier("discoveryAdvertisesEncryptionMetadata")
    void enableEncryption(Configuration.Builder builder) {
        builder.idTokenEncryptionAlgorithm(JWEAlgorithm.RSA_OAEP_256)
                .idTokenEncryptionMethod(EncryptionMethod.A256GCM);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchDiscovery() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/.well-known/openid-configuration"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return new ObjectMapper().readValue(response.body(), Map.class);
    }
}
