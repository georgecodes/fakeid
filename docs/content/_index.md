---
title: "Fake ID"
build:
  render: always
  list: always
  publishResources: false
---

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
    image: georgemc/fakeid:v0.0.1
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
    image: georgemc/fakeid:v0.0.1
    ports:
      - "8091:8091"
    hostname: auth.localtest.me
    environment:
      FAKEID_ISSUER: http://auth.localtest.me:8091
```

The DNS entry auth.localtest.me is externally resolvable to localhost, and we have told docker to allow the fakeid container to be reached via it internally as well.

## Configuration

There are a few ways to configure Fake ID. The simplest way is to simply start the container, and allow it to provide sensible defaults. 

Configuration options are

| Environment variable | Usage |
|----------------------|-------|
| FAKEID_CONFIG_LOCATION | This determines the location of the json config file, should you choose to use this.
| FAKEID_ISSUER       | This is the issuer used. It's the base url for all operations, as well as the issuer claim in id tokens
| FAKEID_SIGNING_KEY   | This is a base64 encoded PEM-format RSA private key. It will be used for signing id tokens, and will be available on the JWKS uri of Fake ID
| FAKEID_SAMPLE_CLAIMS | This is a template for returned id tokens. It can be either a full JWT or simply some base 64 encoded JSON
| FAKEID_SAMPLE_JWT    | This is merely an alias for FAKEID_SAMPLE_CLAIMS. Either can be used for either chosen format
| FAKEID_SIGNING_ALGORITHM | The JWS algorithm used to sign id tokens. Currently RSA-based algorithms only supported - RS256, RS384, RS512, PS256, PS384, PS512. Defaults to RS256
| FAKEID_SIGNING_ALG | Shorthand for FAKEID_SIGNING_ALGORITHM

If you do not provide FAKEID_ISSUER it will default to http://localhost:8091

If you do not provide FAKEID_SIGNING_KEY one will be generated for you on startup. Note that this will change every time you start Fake ID, and if any relying parties are cacheing JWKS
you may hit signature verification errors.

If you do not provide FAKEID_SAMPLE_CLAIMS your id tokens will have the usual necessary claims such as iss and aud, as well as a subject and name of "John C. Developer" and an email claim of "john@developer.com"

## Generating these options

You may generate the signing key however you please. Below are a few examples of how to

### OpenSSL for generating keys

openssl genrsa -out keypair.pem 2048
base64 keypair.pem

### Some websites which will generate you a key

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