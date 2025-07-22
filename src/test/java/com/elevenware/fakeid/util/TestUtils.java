package com.elevenware.fakeid.util;

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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class TestUtils {

    private static ObjectMapper MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static Map<String, String> buildQueryMap(String query) {
        if (query == null) {
            return Collections.emptyMap();
        }

        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            String[] currentParam = param.split("=");
            if (currentParam.length != 2)
                continue;
            String name = currentParam[0];
            String value = currentParam[1];
            map.put(name, value);
        }
        return map;
    }

    public static Map<String, String> convertStringToMap(String data) {
        Map<String, String> map = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(data, "&");

        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            String[] keyValue = token.split("=");
            map.put(keyValue[0], keyValue[1]);
        }

        return map;
    }

}
