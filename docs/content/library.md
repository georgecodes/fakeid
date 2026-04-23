---
title: "Java Library"
subtitle: "Embedding Fake ID directly in a Java project"
menu:
  main:
    name: "Java Library"
    weight: 15
---

## Why embed Fake ID?

Running Fake ID in Docker is the common case, but sometimes you just want an OIDC provider running inside a JVM
test &mdash; no container runtime, no port collisions, no image pulls. Fake ID is published to Maven Central and
exposes the same configuration model as the Docker image, so you can start it in-process and shut it down when
your test is done.

## Add the dependency

Fake ID is published under `com.elevenware:fakeid` on Maven Central.

### Maven

```xml
<dependency>
    <groupId>com.elevenware</groupId>
    <artifactId>fakeid</artifactId>
    <version>0.0.3</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.elevenware:fakeid:0.0.3'
```

## Starting Fake ID in code

The entry point is `FakeIdApplication`, which takes a `Configuration` and exposes `start()`, `stop()`, and
`port()`. There are three ways to build a `Configuration`:

- `Configuration.defaultConfiguration()` reads the environment variables documented on the Getting Started page.
- `Configuration.loadFromFile(path)` loads a JSON configuration file (the same file `FAKEID_CONFIG_LOCATION`
  points at in the Docker image).
- `Configuration.builder()` is for explicit in-code setup.

The three approaches share the same underlying configuration model, but the builder does not map 1:1 to every
environment-variable format — for example, `FAKEID_SIGNING_KEY` is a base64-encoded PEM that gets parsed into a
`JWKSet`, whereas the builder's `jwks(...)` method takes a `JWKSet` directly.

### Minimal

```java
Configuration configuration = Configuration.builder()
        .port(8091)
        .build();

FakeIdApplication app = new FakeIdApplication(configuration).start();

// ... your relying party points at http://localhost:8091 ...

app.stop();
```

### Ephemeral port for tests

To bind to a free port in tests, use `randomPort()` (or `.port(0)`). Read the actual port back from the started
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

### Fully configured

Supply your own signing key and override the claims returned in id tokens. Providing a key you persist and reuse
across startups keeps the JWKS stable — generating a fresh key on every start (as shown below) produces a
different JWKS each run, which can cause signature-verification errors in relying parties that cache the JWKS:

```java
RSAKey jwk = new RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE)
        .keyID("signingKey")
        .algorithm(Algorithm.parse("RS256"))
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

A typical pattern is to start Fake ID once per test class, then point a relying party at it:

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

## Caveats

Fake ID currently ships Javalin as a runtime dependency, so the library form pulls in an embedded HTTP server
even when used in tests. That's intentional &mdash; the server is what makes it useful as an OIDC provider &mdash; but
it's worth knowing if you're auditing your test classpath.
