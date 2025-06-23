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

public class FakeIdApplication {

    public static void main(String[] args) {

        JavalinJackson jsonMapper = new JavalinJackson();

        jsonMapper.getMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        String configLocation = System.getenv("FAKEID_CONFIG_LOCATION");
        Configuration configuration;
        if (configLocation != null) {
            configuration = Configuration.loadFromFile(configLocation);
        } else {
            configuration = Configuration.defaultConfiguration();
        }
        var provider = new FakeIdProvider(configuration);
        
        var app = Javalin.create(c -> {
            c.jsonMapper(jsonMapper);
                })
                .get("/.well-known/openid-configuration", provider::getDiscoveryDocument)
                .get("/jwks", provider::jwksEndpoint)
                .get("/authorize", provider::authorizationEndpoint)
                .post("/token", provider::tokenEndpoint)
                .post("/token/introspect", provider::introspectionEndpoint)
                .start(8091);
    }

}
