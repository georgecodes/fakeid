package com.elevenware.easyid;

import io.javalin.Javalin;

public class EasyIdApplication {

    public static void main(String[] args) {
        var app = Javalin.create(/*config*/)
                .get("/.well-known/openid-configuration", ctx -> ctx.result("Hello World"))
                .start(7070);
    }

}
