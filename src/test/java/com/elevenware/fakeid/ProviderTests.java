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
