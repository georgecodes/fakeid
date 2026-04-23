---
title: "Java Library"
subtitle: "Embedding Fake ID directly in a Java project"
menu:
  main:
    name: "Java Library"
    weight: 15
---

## Why embed Fake ID?

Running Fake ID in Docker is the common case, but sometimes you want an OIDC provider living inside a JVM
test &mdash; no container runtime, no port collisions, no image pulls. Fake ID is published to Maven Central
and exposes the same configuration model as the Docker image.

## Pick a flavour

Fake ID ships as two artifacts. Pick based on whether your test needs to talk HTTP or not.

| Artifact | Pulls in | Use when |
| --- | --- | --- |
| `fakeid-core` | Core OIDC logic. No Javalin, no Jetty, no HTTP server. | Unit tests that want to mint or verify tokens entirely in-process. |
| `fakeid` | Core + Javalin HTTP adapter. | Integration tests where a relying party hits `/authorize`, `/token`, `/jwks` etc. over the network. |

If you're unsure, start with `fakeid-core` &mdash; it's smaller, faster to set up, and you can graduate
to `fakeid` if you need an actual HTTP endpoint.

## `fakeid-core` &mdash; Javalin-free

### Maven

```xml
<dependency>
    <groupId>com.elevenware</groupId>
    <artifactId>fakeid-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.elevenware:fakeid-core:0.1.0'
```

### Usage

The entry point is `FakeIdCore`, constructed from a `Configuration`. It exposes the same endpoints the HTTP
server routes to &mdash; `authorize`, `token`, `discovery`, `jwks`, `userInfo`, `introspect` &mdash; as
plain method calls.

```java
FakeIdCore core = new FakeIdCore(Configuration.builder().build());

AuthorizeResponse auth = core.authorize(new AuthorizeRequest(
        "my-client", "https://app/cb", "code",
        Set.of("openid"), "state-abc", "nonce-xyz"));

TokenResponse tokens = core.token(new TokenRequest(
        "authorization_code", auth.code(), null, "my-client", "ignored"));

// tokens.idToken() is a signed JWT you can parse and assert on.
```

To verify the signature in a test, pass your own signing key in via `Configuration`:

```java
RSAKey jwk = new RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE)
        .keyID("signingKey")
        .algorithm(JWSAlgorithm.RS256)
        .generate();

FakeIdCore core = new FakeIdCore(Configuration.builder()
        .jwks(new JWKSet(jwk))
        .claims(Map.of("sub", "alice@example.com"))
        .build());

TokenResponse tokens = /* ... as above ... */;
SignedJWT idToken = SignedJWT.parse(tokens.idToken());
idToken.verify(new RSASSAVerifier(jwk));
```

## `fakeid` &mdash; full OIDC server

### Maven

```xml
<dependency>
    <groupId>com.elevenware</groupId>
    <artifactId>fakeid</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.elevenware:fakeid:0.1.0'
```

### Usage

The entry point is `FakeIdApplication`, which takes a `Configuration` and exposes `start()`, `stop()`, and
`port()`. There are three ways to build the `Configuration`:

- `Configuration.defaultConfiguration()` reads the environment variables documented on the Getting Started page.
- `Configuration.loadFromFile(path)` loads a JSON configuration file (the same file `FAKEID_CONFIG_LOCATION`
  points at in the Docker image).
- `Configuration.builder()` is for explicit in-code setup.

The three approaches share the same underlying configuration model, but the builder does not map 1:1 to every
environment-variable format &mdash; for example, `FAKEID_SIGNING_KEY` is a base64-encoded PEM that gets parsed
into a `JWKSet`, whereas the builder's `jwks(...)` method takes a `JWKSet` directly.

#### Minimal

```java
Configuration configuration = Configuration.builder()
        .port(8091)
        .build();

FakeIdApplication app = new FakeIdApplication(configuration).start();

// ... your relying party points at http://localhost:8091 ...

app.stop();
```

#### Ephemeral port for tests

To bind to a free port, use `randomPort()` (or `.port(0)`). Read the actual port back from the started
application, and stop the application in a `finally` block so a failing assertion doesn't leak a running server:

```java
FakeIdApplication app = new FakeIdApplication(
        Configuration.builder()
                .randomPort()
                .build())
        .start();

try {
    int port = app.port();
    String issuer = "http://localhost:" + port;
    // ... exercise your relying party against `issuer` ...
} finally {
    app.stop();
}
```

#### Fully configured

Supply your own signing key and override the claims returned in id tokens. Providing a key you persist and
reuse across startups keeps the JWKS stable &mdash; generating a fresh key on every start (as shown below)
produces a different JWKS each run, which can cause signature-verification errors in relying parties that
cache the JWKS:

```java
RSAKey jwk = new RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE)
        .keyID("signingKey")
        .algorithm(JWSAlgorithm.RS256)
        .generate();

Configuration configuration = Configuration.builder()
        .port(8091)
        .jwks(new JWKSet(jwk))
        .claims(Map.of(
                "sub", "jeff@example.com",
                "additionalClaims", Map.of("claim", "claimValue")))
        .build();

FakeIdApplication app = new FakeIdApplication(configuration).start();
```

## Using it in a JUnit 5 test

A typical pattern is to start Fake ID (either flavour) once per test class. With `fakeid`:

```java
class MyOidcIntegrationTest {

    private static FakeIdApplication fakeId;
    private static String issuer;

    @BeforeAll
    static void startFakeId() {
        fakeId = new FakeIdApplication(
                Configuration.builder().randomPort().build())
                .start();
        issuer = "http://localhost:" + fakeId.port();
    }

    @AfterAll
    static void stopFakeId() {
        fakeId.stop();
    }

    @Test
    void discoveryDocumentIsServed() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder()
                        .uri(URI.create(issuer + "/.well-known/openid-configuration"))
                        .GET()
                        .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
```

With `fakeid-core`, the same idea but without an HTTP hop:

```java
class MyOidcUnitTest {

    private FakeIdCore core;

    @BeforeEach
    void buildCore() {
        core = new FakeIdCore(Configuration.builder().build());
    }

    @Test
    void tokenMintedForClientCredentials() {
        TokenResponse tokens = core.token(new TokenRequest(
                "client_credentials", null, "api:read", "c1", "s1"));

        assertEquals("Bearer", tokens.tokenType());
        assertNotNull(tokens.accessToken());
    }
}
```
