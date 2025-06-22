package com.elevenware.fakeid;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

public class EasyIdApplication {

    public static void main(String[] args) {

        JavalinJackson jsonMapper = new JavalinJackson();

        jsonMapper.getMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        String configLocation = String.format("%s/config/config.json", System.getProperty("user.dir"));
        Configuration configuration = Configuration.loadFromFile(configLocation);
        var provider = new EasyIdProvider(configuration);
        
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
