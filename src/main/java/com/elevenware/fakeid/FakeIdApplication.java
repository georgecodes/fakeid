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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class FakeIdApplication {

    private static final Logger LOG = LoggerFactory.getLogger(FakeIdApplication.class);
    private final Configuration configuration;
    private Javalin server;

    public FakeIdApplication(Configuration configuration) {
        this.configuration = configuration;
    }

    public FakeIdApplication start() {
        JavalinJackson jsonMapper = new JavalinJackson();

        jsonMapper.getMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        var provider = new FakeIdProvider(configuration);

        server = Javalin.create(c -> {
                    c.jsonMapper(jsonMapper);
                    c.bundledPlugins.enableCors(cors -> {
                        cors.addRule(it -> {
                            it.anyHost();
                        });
                    });
                })
                .get("/.well-known/openid-configuration", provider::getDiscoveryDocument)
                .get("/jwks", provider::jwksEndpoint)
                .get("/authorize", provider::authorizationEndpoint)
                .post("/token", provider::tokenEndpoint)
                .post("/token/introspect", provider::introspectionEndpoint)
                .start(configuration.getPort());
        LOG.info("Fake ID started");
        return this;
    }

    public void stop() {
        Optional.ofNullable(server).ifPresent(Javalin::stop);
    }

    public int port() {
        return server.port();
    }

    public static void main(String[] args) {

        LOG.info("Starting Fake ID");

        String configLocation = System.getenv("FAKEID_CONFIG_LOCATION");
        Configuration configuration;
        if (configLocation != null) {
            LOG.info("Loading configuration from {}", configLocation);
            configuration = Configuration.loadFromFile(configLocation);
        } else {
            LOG.info("Loading default configuration");
            configuration = Configuration.defaultConfiguration();
        }
        new FakeIdApplication(configuration).start();

    }


}
