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
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static com.elevenware.fakeid.util.TestUtils.buildQueryMap;
import static com.elevenware.fakeid.util.TestUtils.mapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@FakeIdTest
public class LibraryTests {

    private FakeIdApplication app;
    @FakeIdPort
    private int port;

    @Test
    void defaultConfiguration() throws IOException, InterruptedException {

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/.well-known/openid-configuration"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        Map<String, String> discoveryDocument = mapper().readValue(response.body(), Map.class);
        assertEquals("http://localhost:" + port, discoveryDocument.get("issuer"));

    }

    @Test
    void canSpecifyPort() throws IOException, InterruptedException {

        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        serverSocket.close();

        Configuration configuration = Configuration.builder()
                .port(port)
                .build();

        app = new FakeIdApplication(configuration).start();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+port+"/.well-known/openid-configuration"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        Map<String, String> discoveryDocument = mapper().readValue(response.body(), Map.class);
        assertEquals("http://localhost:" + port, discoveryDocument.get("issuer"));

    }

    @Test
    void fullyConfigure() throws Exception {

        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        serverSocket.close();

        RSAKey jwk = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("signingKey")
                .issueTime(new Date())
                .algorithm(Algorithm.parse("RS256"))
                .generate();
        JWKSet jwks = new JWKSet(jwk);

        Configuration configuration = Configuration.builder()
                .port(port)
                .jwks(jwks)
                .claims(Map.of("sub", "jeff@example.com", "additionalClaims", Map.of("claim", "claimValue")))
                .build();

        app = new FakeIdApplication(configuration).start();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+port+"/.well-known/openid-configuration"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        Map<String, String> discoveryDocument = mapper().readValue(response.body(), Map.class);
        assertEquals("http://localhost:" + port, discoveryDocument.get("issuer"));

        String req = new StringBuilder()
                .append("http://localhost:").append(port)
                .append("/authorize")
                .append("?response_type=id_token")
                .append("&client_id=client")
                .append("&redirect_uri=https://example.com")
                .append("&state=abcde")
                .append("&nonce=fghij")
                .append("&scope=openid").toString();

        request = HttpRequest.newBuilder()
                .uri(URI.create(req))
                .GET()
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        String r = response.body();
        Optional<String> location = response.headers().firstValue("Location");
        assertTrue(location.isPresent());
        String locationUri = location.get();
        URI uri = new URI(locationUri);

        assertEquals(uri.getHost(), "example.com");
        String query = uri.getQuery();
        Map<String, String> queryParams = buildQueryMap(query);
        String idTokenValue = queryParams.get("id_token");
        SignedJWT idToken = SignedJWT.parse(idTokenValue);
        RSASSAVerifier verifier = new RSASSAVerifier(jwk);
        assertTrue(idToken.verify(verifier));
        JWTClaimsSet claims = idToken.getJWTClaimsSet();
        assertEquals("jeff@example.com", claims.getSubject());
        Map<String, String> additionalClaims = (Map<String, String>) claims.getClaim("additionalClaims");
        assertEquals("claimValue", additionalClaims.get("claim"));
    }

}
