# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **EC signing algorithm support**: Fake ID can now sign id tokens using Elliptic Curve (EC)
  algorithms — ES256 (P-256), ES384 (P-384), and ES512 (P-521) — in addition to the existing
  RSA-based algorithms.
- When `FAKEID_SIGNING_ALGORITHM` / `FAKEID_SIGNING_ALG` is set to an EC algorithm, a matching
  EC key is generated automatically on startup.
- When `FAKEID_SIGNING_KEY` is provided with a PEM-encoded EC private key and an EC algorithm is
  selected, the EC key is loaded and used for signing.
- `Configuration.Builder.signingAlgorithm(JWSAlgorithm)` now accepts EC algorithms (ES256, ES384,
  ES512) in addition to RSA algorithms.
- Six new unit tests covering EC signing with both auto-generated and user-supplied EC keys across
  all three EC algorithms.

### Changed

- Updated `oidc4j` dependency from `0.1.0` to `0.1.6`. The new version introduces
  `SigningKeySource`, a unified key-management abstraction that supports RSA and EC keys, key
  rotation, and PEM-based loading.
- `TokenMinter` now accepts a generic `JWK` (was `RSAKey`) and dispatches to `RSASSASigner` or
  `ECDSASigner` based on the key type. Existing RSA behaviour is unchanged.
- `FakeIdCore` uses `SigningKeySource.getActiveJwk()` (instead of `getSigningKey()`) so that the
  active key is correctly resolved for both RSA and EC keys.

### Documentation

- Updated `README.md` and `docs/content/_index.md` to document EC algorithm support and provide
  OpenSSL commands for generating EC private keys.
- Updated `docs/content/library.md` with Java examples for EC key construction and token
  verification using `ECDSAVerifier`.

## [0.1.0] — 2025-04-15

### Added

- Initial release.
- Authorization code and client credentials grant types.
- Configurable RSA signing algorithms: RS256 (default), RS384, RS512, PS256, PS384, PS512.
- Environment-variable and JSON-file based configuration.
- `fakeid-core` artifact (Javalin-free) and `fakeid` artifact (full HTTP server).
- JWKS endpoint, discovery document, userinfo endpoint, and token introspection endpoint.
