package com.elevenware.easyid;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.IntNode;
import com.nimbusds.jose.jwk.JWKSet;

import java.io.IOException;
import java.text.ParseException;


public class JWKSetJsonHandler extends StdDeserializer<JWKSet> {

    protected JWKSetJsonHandler() {
        super(JWKSet.class);
    }

    @Override
    public JWKSet deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        try {
            String jwksString = node.toString();
            return JWKSet.parse(jwksString);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
