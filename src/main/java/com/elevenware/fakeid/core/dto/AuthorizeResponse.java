package com.elevenware.fakeid.core.dto;

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

public record AuthorizeResponse(
        String redirectUri,
        String code,
        String accessToken,
        String idToken,
        String state) {

    public String toRedirectLocation() {
        StringBuilder sb = new StringBuilder().append(redirectUri);
        char sep = '?';
        if (code != null) {
            sb.append(sep).append("code=").append(code);
            sep = '&';
        }
        if (accessToken != null) {
            sb.append(sep).append("token=").append(accessToken);
            sep = '&';
        }
        if (idToken != null) {
            sb.append(sep).append("id_token=").append(idToken);
            sep = '&';
        }
        if (state != null) {
            sb.append(sep).append("state=").append(state);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "AuthorizeResponse[redirectUri=" + redirectUri
                + ", code=[REDACTED]"
                + ", accessToken=[REDACTED]"
                + ", idToken=[REDACTED]"
                + ", state=" + state + "]";
    }
}
