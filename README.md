## Introduction

If you're ever developing an application which uses OIDC for login, it can get tiresome setting up an OIDC provider
to use during development. [Fake ID](https://github.com/georgecodes/fakeid) simplifies this by mocking out a basic
OP for use in development. With a little configuration, your app can request and receive access and id tokens without
you needing to authenticate and consent.

## Usage

Typically you would run Fake ID in a docker container, or docker compose. Here is a sample docker-compose file for using
FakeID whilst you develop your relying party application

```yaml
version: '3.8'

services:
  fakeid:
    image: georgemc/fakeid:v0.0.2-dev
    ports:
      - "8091:8091"
```

The above will run the fake id container with all the default values.

You may also wish to run your relying party inside docker compose. This needs a little  more work so that FakeID is reachable from both
your browser and your relying party via the backchannel.

```yaml
version: '3.8'

services:
  app:
    build:
      context: .
    ports:
      - "8080:8080"
    environment:
      ISSUER_URI: http://auth.localtest.me:8091
  fakeid:
    image: georgemc/fakeid:v0.0.2-dev
    ports:
      - "8091:8091"
    hostname: auth.localtest.me
    environment:
      FAKEID_ISSUER: http://auth.localtest.me:8091
```

The DNS entry auth.localtest.me is externally resolvable to localhost, and we have told docker to allow the fakeid container to be reached via it internally as well.

## Using Fake ID as a Java library

Fake ID is published to Maven Central in two flavours. Pick based on whether you need a running HTTP endpoint
or just the provider logic.

### `fakeid-core` — Javalin-free, in-process

No HTTP server, no ports bound, no Javalin on your classpath. Use it when you want to mint id_tokens or run
auth-code round trips entirely in the JVM — typically in unit tests of code that verifies tokens.

```xml
<dependency>
    <groupId>com.elevenware</groupId>
    <artifactId>fakeid-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
FakeIdCore core = new FakeIdCore(Configuration.builder().build());

AuthorizeResponse auth = core.authorize(new AuthorizeRequest(
        "my-client", "https://app/cb", "code",
        Set.of("openid"), null, "nonce-abc"));

TokenResponse tokens = core.token(new TokenRequest(
        "authorization_code", auth.code(), null, "my-client", "ignored"));
// tokens.idToken() is a signed JWT you can assert on.
```

### `fakeid` — full OIDC server

The original library form. Includes Javalin and the HTTP adapter. Use it when a relying party needs to hit
an OIDC endpoint over the network — integration tests that exercise the real HTTP flow your app uses in production.

```xml
<dependency>
    <groupId>com.elevenware</groupId>
    <artifactId>fakeid</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
Configuration configuration = Configuration.builder().port(8091).build();
FakeIdApplication app = new FakeIdApplication(configuration).start();

// ... point your relying party at http://localhost:8091 ...

app.stop();
```

For tests, bind to an ephemeral port with `randomPort()` and stop the app in a `finally` block so a failing
assertion doesn't leak a running server:

```java
FakeIdApplication app = new FakeIdApplication(
        Configuration.builder().randomPort().build()).start();
try {
    int port = app.port();
    String issuer = "http://localhost:" + port;
    // ... exercise your relying party against `issuer` ...
} finally {
    app.stop();
}
```

### Configuration parity with the Docker image

Both flavours share the same `Configuration` API, but the builder doesn't map 1:1 to every environment-variable
format. For example, `FAKEID_CONFIG_LOCATION` is handled via `Configuration.loadFromFile(...)`, and
`FAKEID_SIGNING_KEY` (a base64-encoded PEM) is parsed into a `JWKSet` rather than accepted directly by the
builder. For environment-variable or file-based parity, use `Configuration.defaultConfiguration()` or
`Configuration.loadFromFile(...)`.

## Configuration

There are a few ways to configure Fake ID. The simplest way is to simply start the container, and allow it to provide sensible defaults.

Configuration options are

| Environment variable | Usage |
|----------------------|-------|
| FAKEID_CONFIG_LOCATION | This determines the location of the json config file, should you choose to use this.
| FAKEID_ISSUER       | This is the issuer used. It's the base url for all operations, as well as the issuer claim in id tokens
| FAKEID_SIGNING_KEY   | A base64 encoded PEM-format private key (RSA or EC). Used for signing id tokens, and available on the JWKS URI of Fake ID
| FAKEID_SAMPLE_CLAIMS | This is a template for returned id tokens. It can be either a full JWT or simply some base 64 encoded JSON
| FAKEID_SAMPLE_JWT    | This is merely an alias for FAKEID_SAMPLE_CLAIMS. Either can be used for either chosen format
| FAKEID_SIGNING_ALGORITHM | The JWS algorithm used to sign id tokens. RSA: RS256, RS384, RS512, PS256, PS384, PS512. EC: ES256, ES384, ES512. Defaults to RS256
| FAKEID_SIGNING_ALG | Shorthand for FAKEID_SIGNING_ALGORITHM
| FAKEID_ID_TOKEN_ENCRYPTION_ALG | Switches on id token encryption using this JWE key-management algorithm. RSA: RSA-OAEP, RSA-OAEP-256, RSA-OAEP-384, RSA-OAEP-512. EC: ECDH-ES, ECDH-ES+A128KW, ECDH-ES+A192KW, ECDH-ES+A256KW. Off by default
| FAKEID_ID_TOKEN_ENCRYPTION_ALGORITHM | Alias for FAKEID_ID_TOKEN_ENCRYPTION_ALG
| FAKEID_ID_TOKEN_ENCRYPTION_ENC | The JWE content-encryption method: A128CBC-HS256, A192CBC-HS384, A256CBC-HS512, A128GCM, A192GCM, A256GCM. Defaults to A128CBC-HS256
| FAKEID_ID_TOKEN_ENCRYPTION_KEY | The relying party's public key that id tokens are encrypted to, base64 encoded. Either a PEM-format public key (RSA or EC) or a JWK JSON object

If you do not provide FAKEID_ISSUER it will default to http://localhost:8091

If you do not provide FAKEID_SIGNING_KEY one will be generated for you on startup. Note that this will change every time you start Fake ID, and if any relying parties are cacheing JWKS
you may hit signature verification errors.

If you do not provide FAKEID_SAMPLE_CLAIMS your id tokens will have the usual necessary claims such as iss and aud, as well as a subject and name of "John C. Developer" and an email claim of "john@developer.com"

### ID token encryption

By default id tokens are signed only. Set FAKEID_ID_TOKEN_ENCRYPTION_ALG and Fake ID returns every id token as a nested JWT: the signed token wrapped in a JWE (sign then encrypt, per OpenID Connect Core section 10.2) with `cty: JWT` in the header. The encryption applies to every client, since Fake ID accepts any client id without registration. The discovery document advertises `id_token_encryption_alg_values_supported` and `id_token_encryption_enc_values_supported` only while encryption is on.

The key you supply is the relying party's **public** key, so it is never published on the JWKS URI. Its type must match the algorithm family: RSA-OAEP algorithms need an RSA key, ECDH-ES algorithms need an EC key on P-256, P-384 or P-521. If you set an algorithm without a key Fake ID generates a key pair on startup and logs the private JWK so you can paste it into your relying party. As with the signing key, that pair changes on every start.

Symmetric algorithms (A128KW, A256KW, A128GCMKW, dir and friends) derive their key from the client secret. Fake ID has no registered secrets, so those algorithms are rejected. RSA1_5 is refused too.

In the JSON config file the same settings live under `idTokenEncryption`; see `config/config-encrypted.json` for a complete example:

```json
"idTokenEncryption": {
  "alg": "RSA-OAEP-256",
  "enc": "A256GCM",
  "key": { "kty": "RSA", "kid": "rp-encryption-key", "use": "enc", "e": "AQAB", "n": "..." }
}
```

## Generating these options

You may generate the signing key however you please. Below are a few examples of how to

### OpenSSL for generating keys

**RSA key (default):**

```sh
openssl genrsa -out keypair.pem 2048
base64 keypair.pem
```

**EC key (for ES256/ES384/ES512):**

```sh
# ES256 — P-256 curve
openssl ecparam -name prime256v1 -genkey -noout -out ec-key.pem
openssl pkcs8 -topk8 -nocrypt -in ec-key.pem -out ec-key-pkcs8.pem
base64 ec-key-pkcs8.pem

# ES384 — P-384 curve
openssl ecparam -name secp384r1 -genkey -noout -out ec-key.pem
openssl pkcs8 -topk8 -nocrypt -in ec-key.pem -out ec-key-pkcs8.pem
base64 ec-key-pkcs8.pem

# ES512 — P-521 curve
openssl ecparam -name secp521r1 -genkey -noout -out ec-key.pem
openssl pkcs8 -topk8 -nocrypt -in ec-key.pem -out ec-key-pkcs8.pem
base64 ec-key-pkcs8.pem
```

Set `FAKEID_SIGNING_ALGORITHM` to the matching EC algorithm (`ES256`, `ES384`, or `ES512`) when providing an EC key.

### OpenSSL for the id token encryption key

The relying party owns this key pair. Fake ID only needs the public half, and the relying party keeps the private half to decrypt id tokens.

**RSA (for RSA-OAEP, RSA-OAEP-256, RSA-OAEP-384, RSA-OAEP-512):**

```sh
openssl genrsa -out rp-enc-key.pem 2048          # keep this with the relying party
openssl rsa -in rp-enc-key.pem -pubout -out rp-enc-pub.pem
base64 < rp-enc-pub.pem | tr -d '\n'             # FAKEID_ID_TOKEN_ENCRYPTION_KEY
```

**EC (for ECDH-ES, ECDH-ES+A128KW, ECDH-ES+A192KW, ECDH-ES+A256KW):**

```sh
openssl ecparam -name prime256v1 -genkey -noout -out rp-enc-key.pem   # or secp384r1 / secp521r1
openssl ec -in rp-enc-key.pem -pubout -out rp-enc-pub.pem
base64 < rp-enc-pub.pem | tr -d '\n'             # FAKEID_ID_TOKEN_ENCRYPTION_KEY
```

### Some websites which will generate RSA keys

https://cryptotools.net/rsagen

https://emn178.github.io/online-tools/rsa/key-generator/

https://www.devglan.com/online-tools/rsa-key-generator

Only the private key need be provided

### Generating sample claims without using a JWT

You may simply create a file

```json
{
  "name": "Dave Coder",
  "email": "dave@coding.com",
  "extraClaim": "Anything you want"
}
```
and encode to base 64

```bash
cat claims.json | base64
```

Or encode the JSON online at https://www.base64decode.org/

### Generating a full JWT

You may already have a sample JWT you have captured to serve as a template id token. Simply use that as-is. The signature and header will be disregarded

You can generate a JWT online at https://jwt.rocks/

Note that providing a JWT will simply be used for some claims. The header and signature will be ignored. Certain claims will also be disregarded, including iss, iat, exp
and aud.
