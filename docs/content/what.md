---
title: "How It Works"
subtitle: "What Fake ID does (and doesn't do)"
menu:
  main:
    name: "How It Works"
    weight: 20
---

## The OIDC Flow

Typically, if you are authenticating users to your application via OIDC, you would have an OP &mdash; an OIDC provider &mdash; running somewhere.
Upon hitting, for instance, a "login with OIDC" button, your app would redirect the user to the OP, wherein they would log in and be presented
with a consent page saying what application needed access, and a button to grant that. Once they grant those permissions, they're redirected back
to your application, which, depending on the flow used, would either get an id token immediately, or proceed to obtain one via the back channel.

What Fake ID aims to provide is that same functionality for development, without the bother of having to run a full blown OP, and without the need
for any user interaction. The relying party &mdash; that's your application &mdash; will simply run off to Fake ID, and everything happens automatically,
giving it back an id token which it can then use.

## Implemented Endpoints

<dl class="endpoint">
  <dt>/.well-known/openid-configuration</dt>
  <dd>Discovery endpoint. Prevents clients having to be configured with various other endpoints.</dd>

  <dt>/authorize</dt>
  <dd>Where the initial authorisation request goes. Automatically redirects back to the relying party with state, an auth code, and any requested tokens.</dd>

  <dt>/token</dt>
  <dd>Where the relying party can exchange an auth code for access and id tokens. Also supports the <code>client_credentials</code> grant for machine-to-machine flows, returning an access token without an id token.</dd>

  <dt>/jwks</dt>
  <dd>Where the public key used to sign the id tokens is available, so relying parties can verify id tokens.</dd>

  <dt>/userinfo</dt>
  <dd>Returns the configured claims in JSON format.</dd>
</dl>

## Supported Grant Types

<dl class="endpoint">
  <dt>authorization_code</dt>
  <dd>The standard OIDC flow. Exchange an auth code obtained from <code>/authorize</code> for an access token and (when <code>openid</code> scope is requested) an id token.</dd>

  <dt>client_credentials</dt>
  <dd>
    Machine-to-machine flow. POST to <code>/token</code> with <code>grant_type=client_credentials</code> and client credentials. Credentials may be sent either as
    <code>client_id</code> and <code>client_secret</code> form parameters, or via an HTTP Basic <code>Authorization</code> header. As with everything else in Fake ID,
    the credentials are not validated &mdash; they merely need to be present. The response contains an access token but no id token.
  </dd>
</dl>

## What Fake ID Does NOT Do

Fake ID is just that &mdash; fake. It does not authenticate, nor is it even currently aware of, OAuth clients.
You must include a `client_id` in requests and a `client_secret` for token requests.
These must merely be present; they aren't checked at all.

The id token returned will always contain the same claims (`exp` and `iat` aside).

In short: this is not, in any way, an actual implementation of OIDC.
It should go without saying &mdash; this must not be used to secure anything which matters.
