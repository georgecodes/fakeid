---
title: "EasyID"
build:
  render: always
  list: always
  publishResources: false
---

## Introduction

If you're ever developing an application which uses OIDC for login, it can get tiresome setting up an OIDC provider
to use during development. [EasyID](https://github.com/georgecodes/easyid) simplifies this by mocking out a basic 
OP for use in development. With a little configuration, your app can request and receive access and id tokens without
you needing to authenticate and consent.

