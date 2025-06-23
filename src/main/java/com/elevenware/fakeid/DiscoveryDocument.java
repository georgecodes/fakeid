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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Set;

public class DiscoveryDocument {

    private  String base;
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String userinfoEndpoint;
    private String jwksUri;
    private String registrationEndpoint;
    private String endSessionEndpoint;
    private String introspectionEndpoint;
    private boolean requestParameterSupported;
    private Set<String> grantTypesSupported;
    private Set<String> scopesSupported;
    private Set<String> tokenEndointAuthMethodsSupported;
    private Set<String> acrValuesSupported;
    private Set<String> responseTypesSupported;
    private Set<String> subjectTypesSupported = Set.of("public");
    private Set<String> idTokenSigningAlgsSupported = Set.of("RS256", "PS256");
    private Set<String> claimsSuported = Set.of("name", "email", "given_name", "family_name", "sub");

    @JsonIgnore
    public String getBase() {
        return base;
    }

    public String getAuthorizationEndpoint() {
        return fromBase("/authorize");
    }

    public String getTokenEndpoint() {
        return fromBase("/token");
    }

    public String getUserinfoEndpoint() {
        return fromBase("/userinfo");
    }

    public String getJwksUri() {
        return fromBase("/jwks");
    }

    public String getRegistrationEndpoint() {
        return fromBase("/register");
    }

    public String getEndSessionEndpoint() {
        return fromBase("/logout");
    }

    public String getIntrospectionEndpoint() {
        return fromBase("/token/introspect");
    }

    private String fromBase(String s) {
        return base + s;
    }

    public String getIssuer() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public boolean isRequestParameterSupported() {
        return requestParameterSupported;
    }

    public void setRequestParameterSupported(boolean requestParameterSupported) {
        this.requestParameterSupported = requestParameterSupported;
    }

    public Set<String> getGrantTypesSupported() {
        return grantTypesSupported;
    }

    public void setGrantTypesSupported(Set<String> grantTypesSupported) {
        this.grantTypesSupported = grantTypesSupported;
    }

    public Set<String> getScopesSupported() {
        return scopesSupported;
    }

    public void setScopesSupported(Set<String> scopesSupported) {
        this.scopesSupported = scopesSupported;
    }

    public Set<String> getTokenEndointAuthMethodsSupported() {
        return tokenEndointAuthMethodsSupported;
    }

    public void setTokenEndointAuthMethodsSupported(Set<String> tokenEndointAuthMethodsSupported) {
        this.tokenEndointAuthMethodsSupported = tokenEndointAuthMethodsSupported;
    }

    public Set<String> getAcrValuesSupported() {
        return acrValuesSupported;
    }

    public void setAcrValuesSupported(Set<String> acrValuesSupported) {
        this.acrValuesSupported = acrValuesSupported;
    }

    public Set<String> getResponseTypesSupported() {
        return responseTypesSupported;
    }

    public void setResponseTypesSupported(Set<String> responseTypesSupported) {
        this.responseTypesSupported = responseTypesSupported;
    }

    public Set<String> getSubjectTypesSupported() {
        return subjectTypesSupported;
    }

    public void setSubjectTypesSupported(Set<String> subjectTypesSupported) {
        this.subjectTypesSupported = subjectTypesSupported;
    }

    public Set<String> getIdTokenSigningAlgsSupported() {
        return idTokenSigningAlgsSupported;
    }

    public void setIdTokenSigningAlgsSupported(Set<String> idTokenSigningAlgsSupported) {
        this.idTokenSigningAlgsSupported = idTokenSigningAlgsSupported;
    }

    public Set<String> getClaimsSuported() {
        return claimsSuported;
    }

    public void setClaimsSuported(Set<String> claimsSuported) {
        this.claimsSuported = claimsSuported;
    }

    public static DiscoveryDocument create(String base) {
        DiscoveryDocument doc = new DiscoveryDocument();
        doc.setBase(base);
        doc.setRequestParameterSupported(true);
        doc.setGrantTypesSupported(Set.of("authorization_code", "client_credentials", "refresh_token"));
        doc.setScopesSupported(Set.of("openid", "profile", "email"));
        doc.setTokenEndointAuthMethodsSupported(Set.of("client_secret_basic", "client_secret_post"));
        doc.setAcrValuesSupported(Set.of("urn:mace:incommon:iap:silver"));
        doc.setResponseTypesSupported(Set.of("code", "token"));
        return doc;
    }

}
