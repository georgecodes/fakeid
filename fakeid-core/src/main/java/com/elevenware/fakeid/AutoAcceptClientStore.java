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

import com.oidc4j.v2.lib.store.Client;
import com.oidc4j.v2.lib.store.ClientStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoAcceptClientStore implements ClientStore {

    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    @Override
    public Optional<Client> findById(String id) {
        return Optional.of(clients.computeIfAbsent(id, cid -> new Client(cid, "")));
    }

    @Override
    public void save(Client client) {
        clients.put(client.getId(), client);
    }
}
