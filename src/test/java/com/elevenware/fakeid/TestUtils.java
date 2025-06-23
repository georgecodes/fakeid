package com.elevenware.fakeid;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TestUtils {

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

}
