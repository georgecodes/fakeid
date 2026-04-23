package com.elevenware.fakeid.core.error;

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

public abstract class OidcException extends RuntimeException {

    private final String error;
    private final String errorDescription;
    private final int httpStatus;

    protected OidcException(String error, String errorDescription, int httpStatus) {
        super(error + ": " + errorDescription);
        this.error = error;
        this.errorDescription = errorDescription;
        this.httpStatus = httpStatus;
    }

    public String error() {
        return error;
    }

    public String errorDescription() {
        return errorDescription;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
