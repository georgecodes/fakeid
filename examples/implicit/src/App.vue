<script setup>
import { ref, onMounted } from 'vue'
import { UserManager } from 'oidc-client-ts'

const { issuer, clientId } = window.APP_CONFIG

const userManager = new UserManager({
  authority: issuer,
  client_id: clientId,
  redirect_uri: window.location.origin + '/',
  response_type: 'id_token token',
  scope: 'openid profile email',
})

const user = ref(null)
const loading = ref(true)
const error = ref(null)

onMounted(async () => {
  try {
    if (window.location.hash.includes('id_token') || window.location.hash.includes('access_token')) {
      user.value = await userManager.signinRedirectCallback()
      window.history.replaceState({}, document.title, '/')
    } else {
      user.value = await userManager.getUser()
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

async function login() {
  await userManager.signinRedirect()
}

async function logout() {
  await userManager.removeUser()
  user.value = null
}
</script>

<template>
  <nav class="navbar navbar-expand-md navbar-dark bg-dark mb-4">
    <div class="container-fluid">
      <a class="navbar-brand" href="/">FakeID Implicit Flow Demo</a>
      <button v-if="user" class="btn btn-outline-success" @click="logout">Logout</button>
    </div>
  </nav>
  <main class="container">
    <div v-if="loading" class="bg-body-tertiary p-5 rounded">
      <p>Loading&hellip;</p>
    </div>
    <div v-else-if="error" class="alert alert-danger">
      <strong>Authentication error:</strong> {{ error }}
    </div>
    <div v-else-if="user" class="bg-body-tertiary p-5 rounded">
      <h1>User Profile</h1>
      <p class="lead">Logged in via OIDC implicit grant.</p>
      <table class="table table-striped mt-4">
        <thead>
          <tr><th>Claim</th><th>Value</th></tr>
        </thead>
        <tbody>
          <tr v-for="(value, key) in user.profile" :key="key">
            <td><strong>{{ key }}</strong></td>
            <td>{{ value }}</td>
          </tr>
        </tbody>
      </table>
      <button class="btn btn-lg btn-primary mt-3" @click="logout">Log out &raquo;</button>
    </div>
    <div v-else class="bg-body-tertiary p-5 rounded">
      <h1>FakeID Implicit Flow Demo</h1>
      <p class="lead">Click below to log in using the OIDC implicit grant.</p>
      <button class="btn btn-lg btn-primary" @click="login">Login &raquo;</button>
    </div>
  </main>
</template>
