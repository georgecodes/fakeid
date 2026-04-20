'use strict';

const express = require('express');
const session = require('express-session');
const { Issuer, generators } = require('openid-client');

const ISSUER = process.env.ISSUER;
const CLIENT_ID = process.env.CLIENT_ID;
const CLIENT_SECRET = process.env.CLIENT_SECRET;
const PORT = process.env.PORT || 3000;
const BASE_URL = process.env.BASE_URL || `http://localhost:${PORT}`;

if (!ISSUER || !CLIENT_ID || !CLIENT_SECRET) {
  console.error('Error: ISSUER, CLIENT_ID, and CLIENT_SECRET environment variables are required.');
  process.exit(1);
}

async function start() {
  const issuer = await Issuer.discover(ISSUER);
  const client = new issuer.Client({
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
    redirect_uris: [`${BASE_URL}/callback`],
    response_types: ['code'],
  });

  const app = express();

  app.use(session({
    secret: CLIENT_SECRET,
    resave: false,
    saveUninitialized: false,
    cookie: { secure: false },
  }));

  app.get('/', (req, res) => {
    if (!req.session.userinfo) {
      return res.redirect('/login');
    }
    const profile = req.session.userinfo;
    const rows = Object.entries(profile)
      .map(([key, value]) => `<tr><td><strong>${escape(key)}</strong></td><td>${escape(String(value))}</td></tr>`)
      .join('\n');
    res.send(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>FakeID - Profile</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
<nav class="navbar navbar-expand-md navbar-dark bg-dark mb-4">
  <div class="container-fluid">
    <a class="navbar-brand" href="/">FakeID Relying Party Demo</a>
    <form class="d-flex" method="get" action="/logout">
      <button class="btn btn-outline-success" type="submit">Logout</button>
    </form>
  </div>
</nav>
<main class="container">
  <div class="bg-body-tertiary p-5 rounded">
    <h1>User Profile</h1>
    <p class="lead">Logged in successfully via OIDC.</p>
    <table class="table table-striped mt-4">
      <thead><tr><th>Claim</th><th>Value</th></tr></thead>
      <tbody>
        ${rows}
      </tbody>
    </table>
    <a class="btn btn-lg btn-primary mt-3" href="/logout" role="button">Log out &raquo;</a>
  </div>
</main>
</body>
</html>`);
  });

  app.get('/login', (req, res) => {
    const nonce = generators.nonce();
    const state = generators.state();
    req.session.nonce = nonce;
    req.session.state = state;
    const url = client.authorizationUrl({
      scope: 'openid profile email',
      nonce,
      state,
    });
    req.session.save(() => res.redirect(url));
  });

  app.get('/callback', async (req, res) => {
    const params = client.callbackParams(req);
    try {
      const tokenSet = await client.callback(`${BASE_URL}/callback`, params, {
        nonce: req.session.nonce,
        state: req.session.state,
      });
      const userinfo = await client.userinfo(tokenSet.access_token);
      req.session.userinfo = userinfo;
      delete req.session.nonce;
      delete req.session.state;
      res.redirect('/');
    } catch (err) {
      console.error('Callback error:', err);
      res.status(500).send(`Authentication failed: ${err.message}`);
    }
  });

  app.get('/logout', (req, res) => {
    req.session.destroy(() => {
      res.redirect('/');
    });
  });

  app.listen(PORT, () => {
    console.log(`App running at ${BASE_URL}`);
  });
}

function escape(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

start().catch(err => {
  console.error('Failed to start:', err);
  process.exit(1);
});
