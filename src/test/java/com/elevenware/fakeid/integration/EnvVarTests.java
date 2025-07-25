package com.elevenware.fakeid.integration;

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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnvVarTests extends AbstractIntegrationTest {

    @Test
    void discoveryDocumentHasDefaultIssuer() throws IOException, InterruptedException {

        Map<String, Object> discoveryDocument = fetchDiscoveryDocument("http://localhost:8092");
        assertEquals("http://localhost:8092", discoveryDocument.get("issuer"));

    }

    @Test
    void jwksReturnsKeys() throws JOSEException {

        String configuredKey = "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2d0lCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktrd2dnU2xBZ0VBQW9JQkFRREorWGZNMTQwM0RXZXkKWkFOakJad1I0UjViV2hnTit2ODdtRFVuUng4TEFYSGcxaEVWVlRwRGhYYkl0QmhrL1V3ZkhUSlc0OGc0WmxSKwpjSTZOSzhHRWNwQ21uSEFMK09mZDJpakZSL3BnREFRc0ZpNlo5dmhkUFgrSWNjTHY3RktsZTRhaVJhTlFSV3FLCm1PRDBDQXpoTE9wdFNJeTVDbSszemt2cXpicXR5RkRzbFF2Yml5RUM4MjhpSVJBUWRGOUFHamt0c08yckVyR3UKUUFONytSWDh2Y2xJY2U0VkhLdFJsQVhhMmVOc0RRRVV5S0YrM3RrQmpDTzRxaWR2TzhCcDFsUE40dEpHdzc2MgovMHNieUxKMG5idmxDek84VENoS05oUllBRnovRm9LMllnZzlDNVNxTHU0cGt3TzExMFdJSjNmV01lOGRNU1BuCmZKckNjM0daQWdNQkFBRUNnZ0VBRU16M0dhdCtmQ3RaeWwyR2FKakJYRDFoSkpFd2VnMDhhTzBtMHI4WVJuYVcKemZDUW1Ea3dNUWlya2xOa3loWEYzTHExZ3NQTTFtQXd2Qm5KeWRWdmNnQ0RwdEJSZzdWdk9DV2JDWm9NcWl1bAp0ek1iS2tTQWNXVVp0RFJlVmszT3JDUkQxVUM4cm9NdnN4cTNiNlV4eVJOTzVzdEhwV2JJVno1S1UxeE1vSUxvCnBWc05DYzFsVVpFRTNtdENKeDcrM04zVnVSTlZXaDR6S05FM0t3dU1uM3A2NmdFcGFLVnpUa0dLTHQ3RThpUUsKWTlZSXE1aHVQVXNrT0g4ZTl1VDFRKzJYdnFkQWxtWisyZi9zKzYyMkU1RGdneExseHBWTFl1M3VyZ01QTzNMdgpFTFlremJqdWdtc2s4bnNEMytWOFJpVWFUdXZYR0paYlY0WU1aeGhOV1FLQmdRRDl6NFFTNjd4UERCd29QWVVTCkNxaldQMk5iQWMrK3lwdXVVc2d4Sm5PVmV3Tkw2aWpWWUgwa3FjOVFmR0o4MkJaYVRSYktsN2dkbUZiVVlRNUoKVmRFWE5CUU1rNFo3c0VWbEFOWjZSYS9FR3dVRWVPTlg0WnNtVmhuTFlzUXZNVlpIUHViR1hiVXZ6SDBaMitMdQpGNE84MWhKVVRrOXd3aVNGSk1kTEtuVVNQUUtCZ1FETHQzdkpjYTE1SkZvMEJLYzhWL2VoaGF2SC9MckdDcm5lCjJHN3NhR21ab1BtbnhnUC81WDI5cUtUM2VBTy9lZ1RhOVRtOWQrQTY1VWZ0d1BoVk1DQU82QUFqcXdSTkhrdnoKZFJWK0lybnJmdlIreXFSL0hDeFpLNkFNWWZ0dkI3YjFCVVFhUkxCSmhPTU1taVBHcndEbWo4ak82SmRKZTR3cQpab0lnNHI1ZWpRS0JnUUNvcCtXT29TRWZzZDlnQ2dsTUJOTk9rdzZWb2UySFRhRUh4TENNb0kxN3ppTlJwY2IrCmkwQjlSVzJpZ1JUOCtxWlgyUlFGQ2E1cDFCb1d4R3UyNVpTc2c0bXpGYkR1cmlKZXpLQzQzV2UvdTJpcElSK0kKL1R5WXkrd1dENHBvQmJQY1lmcjk0N1VManltdUxrUnNqQ21aS1BOREhFcURsaTZvWHRranBKSG96UUtCZ1FDawplZHIrMU1KYnFhY09rcWMzaUVxTjVhL0JBdU1GbklsZlM5MmJEOTRheEtaczkrb294SEFXSGNBN2NYR05PZ1lMCmhxeDh0Zm1iYzV6MGI3WFFpYytJV1hZclZodGQ3RlVrRm1jbzlQNnBEVkozd0VLNXdkUm9sbGxkUmdyTUpTMXAKZkR2MC9YcGJrV2dEdDd1azRZelhta1ZtRU5KODZMeE5TNEJLN3VjR3pRS0JnUUR6aDVBcE1DYWwrR2NqNk5VNwpXMjVtYmNlVXFUd2F5R2tmd1ZEZjJMVlB2cFRFc0xmNzJmS0VCdGFkeFF2UXRGSnhLVlVqMlhlT3RzRUIzV1Y4CjlzNGlhRGFDMkxTYTR6TCtIa0dTUmxxMHVtMGN0eUxmZ1A4YnRjeEhtbGtHVm81TW9HYUFsYUd1L0dnMERPZWQKMEo1WTZQREJ0Z0xzSE56MDRNUHVvcDFJOVE9PQotLS0tLUVORCBQUklWQVRFIEtFWS0tLS0tCg==";
        configuredKey = new String(Base64.getDecoder().decode(configuredKey));
        RSAKey expecteKey = RSAKey.parseFromPEMEncodedObjects(configuredKey).toRSAKey();

        JWKSet jwkSet = fetchJwks("http://localhost:8092");

        assertNotNull(jwkSet);
        assertEquals(1, jwkSet.getKeys().size());
        RSAKey key = jwkSet.getKeyByKeyId("signingKey").toRSAKey();
        assertNotNull(key);
        assertEquals(JWSAlgorithm.RS256, key.getAlgorithm());
        assertFalse(key.isPrivate());

        assertFalse(key.isPrivate());
        assertEquals(expecteKey.getModulus(), key.getModulus());
        assertEquals(expecteKey.getPublicExponent(), key.getPublicExponent());

    }

    @Test
    void providedClaims() throws IOException, ParseException, JOSEException {

        String state = RandomStringUtils.randomAlphabetic(32);
        String nonce = RandomStringUtils.randomAlphabetic(32);
        String clientId = "client1";

        Map<String, String> queryParams = implicitAuthRequest("http://localhost:8092", state, nonce, clientId);
        String stateReturned = queryParams.get("state");
        String code = queryParams.get("code");
        String idToken = queryParams.get("id_token");

        assertEquals(state, stateReturned);
        assertNotNull(code);
        SignedJWT jwt = SignedJWT.parse(idToken);
        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        assertEquals(nonce, claims.getClaim("nonce"));
        assertEquals("Billy Otheruser", claims.getSubject());
        assertEquals("Billy Otheruser", claims.getClaim("sub"));
        assertEquals("billy@example.com", claims.getClaim("email"));
        assertEquals("http://localhost:8092", claims.getIssuer());
        assertEquals(clientId, claims.getAudience().get(0));
        assertEquals("Some other claim", claims.getClaim("additional"));

        JWKSet jwkSet = fetchJwks("http://localhost:8092");
        RSAKey key = jwkSet.getKeyByKeyId("signingKey").toRSAKey();

        RSASSAVerifier verifier = new RSASSAVerifier(key);
        boolean signedByJwk = jwt.verify(verifier);

        assertTrue(signedByJwk);

    }

}
