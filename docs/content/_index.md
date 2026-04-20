---
title: "Fake ID"
subtitle: "A mock OIDC provider for local development"
menu:
  main:
    name: "Getting Started"
    weight: 10
---

## Introduction

If you're ever developing an application which uses OIDC for login, it can get tiresome setting up an OIDC provider
to use during development. [Fake ID](https://github.com/georgecodes/fakeid) simplifies this by mocking out a basic
OP for use in development. With a little configuration, your app can request and receive access and id tokens without
you needing to authenticate and consent.

## Usage

Typically you would run Fake ID in a Docker container, or Docker Compose. Here is a sample docker-compose file for using
Fake ID whilst you develop your relying party application:

```yaml
version: '3.8'

services:
  fakeid:
    image: georgemc/fakeid:v0.0.3
    ports:
      - "8091:8091"
```

The above will run the Fake ID container with all the default values.

You may also wish to run your relying party inside Docker Compose. This needs a little more work so that Fake ID is reachable from both
your browser and your relying party via the backchannel:

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
    image: georgemc/fakeid:v0.0.3
    ports:
      - "8091:8091"
    hostname: auth.localtest.me
    environment:
      FAKEID_ISSUER: http://auth.localtest.me:8091
```

The DNS entry `auth.localtest.me` is externally resolvable to localhost, and we have told Docker to allow the
Fake ID container to be reached via it internally as well.

## Configuration

There are a few ways to configure Fake ID. The simplest way is to simply start the container, and allow it to provide sensible defaults.

Configuration options are:

| Environment variable | Usage |
| --- | --- |
| `FAKEID_CONFIG_LOCATION` | Location of the JSON config file, should you choose to use one. |
| `FAKEID_ISSUER` | The issuer used. It's the base URL for all operations, as well as the issuer claim in id tokens. |
| `FAKEID_SIGNING_KEY` | A base64 encoded PEM-format RSA private key. Used for signing id tokens, and available on the JWKS URI. |
| `FAKEID_SAMPLE_CLAIMS` | A template for returned id tokens. Can be either a full JWT or base64 encoded JSON. |
| `FAKEID_SAMPLE_JWT` | An alias for `FAKEID_SAMPLE_CLAIMS`. Either can be used for either format. |
| `FAKEID_SIGNING_ALGORITHM` | The JWS algorithm for signing id tokens. RSA-based only: RS256, RS384, RS512, PS256, PS384, PS512. Defaults to RS256. |
| `FAKEID_SIGNING_ALG` | Shorthand for `FAKEID_SIGNING_ALGORITHM`. |

### Defaults

- If you do not provide `FAKEID_ISSUER` it will default to `http://localhost:8091`.
- If you do not provide `FAKEID_SIGNING_KEY` one will be generated on startup. Note that this will change every time you start Fake ID, and if any relying parties are caching JWKS you may hit signature verification errors.
- If you do not provide `FAKEID_SAMPLE_CLAIMS` your id tokens will have the usual necessary claims such as `iss` and `aud`, as well as a subject and name of "John C. Developer" and an email claim of "john@developer.com".

## Generating Options

### Signing keys with OpenSSL

```sh
openssl genrsa -out keypair.pem 2048
base64 keypair.pem
```

Only the private key need be provided.

You can also generate keys online at:

- [cryptotools.net/rsagen](https://cryptotools.net/rsagen)
- [emn178.github.io RSA Key Generator](https://emn178.github.io/online-tools/rsa/key-generator/)
- [devglan.com RSA Key Generator](https://www.devglan.com/online-tools/rsa-key-generator)

### Sample claims without a JWT

Create a JSON file with your desired claims:

```json
{
  "name": "Dave Coder",
  "email": "dave@coding.com",
  "extraClaim": "Anything you want"
}
```

Then encode to base64:

```sh
cat claims.json | base64
```

Or encode the JSON online at [base64decode.org](https://www.base64decode.org/).

### Using a full JWT

If you already have a sample JWT you have captured, you can use it as-is as a template id token.
The header and signature will be disregarded. You can generate a JWT online at [jwt.rocks](https://jwt.rocks/).

Note that certain claims will be disregarded, including `iss`, `iat`, `exp`, and `aud`.
