---
title: "What Does It DO?"
build:
  render: always
  list: always
  publishResources: false
---

## What This Actually Does

Typically, if you are authenticating users to your application via OIDC, you would have an OP - an OIDC provider - running somewhere.
 Upon hitting, for instance, a "login with OIDC" button, your app would redirect the user to the OP, wherein they would log in and be presented
with a consent page saying what application needed access etc. and a button to grant that. Once they grant those permissions they're redirected back 
to your application, which, depending on the flow used, would either get an id token immediately, or proceed to obtain one via the back channel.

That's a whistle stop tour of OIDC, which I expect is not necessary if you already are using OIDC. What Fake ID aims to provide, is that same functionality 
for development etc, without the bother of having to run a full blown OP, and without the need for there to be any user interaction. The relying party, that's 
your application, will simply run off to Fake ID, and everything happens automatically, giving it back an id token which it can then use.

## Implemented endpoints

```
 /.well-known/openid-configuration - this is where discovery happens. It prevents clients having to be configured with various other endpoints etc
 /authorize - where the initial authorisation request goes. This automatically redirects back to the relying party with state,an auth code and any requested tokens
 /token - where the relying party can exchange an auth code for access and id tokens
 /jwks - where the public key used to sign the id tokens will be available, so that relying parties can verify id tokens
 /userinfo - returns the configured claims in JSON format

```

## What Fake ID does NOT do

Fake ID is just that - fake. It does not authenticate, nor is it even currently aware of, oauth clients. You must include a client_id in requests and
a client secret for token requests. These must merely be present, they aren't checked at all.

The id token returned will always be the same claims, exp and iat aside. 

In short, no, this is not, in any way, an actual implementation of OIDC. It should go without saying, this mustn't be used to secure anything which matters.