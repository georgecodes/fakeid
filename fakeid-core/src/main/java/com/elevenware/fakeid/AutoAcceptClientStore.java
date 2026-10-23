package com.elevenware.fakeid;

/*-
 * #%L
 * Fake ID
 * %%
 * Copyright (C) 2025 George McIntosh
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.oidc4j.v2.lib.store.Client;
import com.oidc4j.v2.lib.store.ClientStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A client store that accepts any client id. When id token encryption is configured every
 * auto-created client is registered with {@code id_token_encrypted_response_alg},
 * {@code id_token_encrypted_response_enc} and the recipient public key as its inline {@code jwks},
 * which is what oidc4j's {@code IdTokenEncrypter} reads.
 */
public final class AutoAcceptClientStore implements ClientStore {

    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final String idTokenEncryptedResponseAlg;
    private final String idTokenEncryptedResponseEnc;
    private final String jwks;

    public AutoAcceptClientStore() {
        this(null, null, null);
    }

    /**
     * @param algorithm    JWE algorithm for id tokens, or {@code null} to issue signed-only tokens
     * @param method       JWE content-encryption method; may be {@code null} to use oidc4j's default
     * @param recipientKey the relying party's key; only its public half is registered on clients
     */
    public AutoAcceptClientStore(JWEAlgorithm algorithm, EncryptionMethod method, JWK recipientKey) {
        if (algorithm != null && recipientKey == null) {
            throw new IllegalArgumentException(
                    "An id token encryption algorithm requires a recipient key");
        }
        this.idTokenEncryptedResponseAlg = algorithm == null ? null : algorithm.getName();
        this.idTokenEncryptedResponseEnc = method == null ? null : method.getName();
        this.jwks = algorithm == null ? null : new JWKSet(recipientKey.toPublicJWK()).toString(true);
    }

    public boolean isIdTokenEncryptionEnabled() {
        return idTokenEncryptedResponseAlg != null;
    }

    @Override
    public Optional<Client> findById(String id) {
        return Optional.of(clients.computeIfAbsent(id, this::newClient));
    }

    private Client newClient(String id) {
        if (idTokenEncryptedResponseAlg == null) {
            return new Client(id, "");
        }
        return Client.builder(id)
                .secret("")
                .idTokenEncryptedResponseAlg(idTokenEncryptedResponseAlg)
                .idTokenEncryptedResponseEnc(idTokenEncryptedResponseEnc)
                .jwks(jwks)
                .build();
    }

    @Override
    public void save(Client client) {
        clients.put(client.getId(), client);
    }
}
