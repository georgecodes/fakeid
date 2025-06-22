package com.elevenware.fakeid;

import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProviderTests {

    @Test
    void testDiscoveryDocument() {
        FakeIdProvider provider = new FakeIdProvider(Configuration.defaultConfiguration());

        Context ctx = mock(Context.class);
        // capture json call
        ArgumentCaptor<?> jsonCaptor = ArgumentCaptor.forClass(Object.class);
        provider.getDiscoveryDocument(ctx);
    }

}
