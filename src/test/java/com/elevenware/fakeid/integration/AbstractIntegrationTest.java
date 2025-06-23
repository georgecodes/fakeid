package com.elevenware.fakeid.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.util.Map;

import static com.elevenware.fakeid.TestUtils.buildQueryMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

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
