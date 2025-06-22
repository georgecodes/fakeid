package com.elevenware.fakeid;

import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ProviderTests {

    @Test
    void testDiscoveryDocument() {

        Configuration configuration = Configuration.defaultConfiguration();
        FakeIdProvider provider = new FakeIdProvider(configuration);

        Context ctx = mock(Context.class);

        ArgumentCaptor<DiscoveryDocument> jsonCaptor = ArgumentCaptor.forClass(DiscoveryDocument.class);
        provider.getDiscoveryDocument(ctx);
        verify(ctx, times(1)).json(jsonCaptor.capture());
        DiscoveryDocument doc = jsonCaptor.getValue();

        assertEquals(doc.getIssuer(), configuration.getIssuer());
    }

}
