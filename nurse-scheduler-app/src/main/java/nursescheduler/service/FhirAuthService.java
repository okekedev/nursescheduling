package nursescheduler.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class FhirAuthService {

    @Value("${hchb.token-url}")
    private String tokenUrl;

    @Value("${hchb.client-id}")
    private String clientId;

    @Value("${hchb.agency-secret}")
    private String agencySecret;

    @Value("${hchb.resource-security-id}")
    private String resourceSecurityId;

    private String accessToken;
    private long tokenExpiryTime;

    public String getBearerToken() {
        if (accessToken == null || System.currentTimeMillis() > tokenExpiryTime - 60000) {
            refreshToken();
        }
        return accessToken;
    }

    private void refreshToken() {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("User-Agent", "PostmanRuntime/7.43.0");
        headers.add("Accept", "*/*");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "agency_auth");
        body.add("client_id", clientId);
        body.add("scope", "openid HCHB.api.scope agency.identity hchb.identity");
        body.add("resource_security_id", resourceSecurityId);
        body.add("agency_secret", agencySecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            TokenResponse response = restTemplate.postForObject(tokenUrl, request, TokenResponse.class);
            if (response != null && response.getAccessToken() != null) {
                accessToken = response.getAccessToken();
                tokenExpiryTime = System.currentTimeMillis() + (response.getExpiresIn() * 1000);
            } else {
                throw new RuntimeException("No access token in response");
            }
        } catch (Exception e) {
            throw new RuntimeException("Token request failed: " + e.getMessage());
        }
    }

    private static class TokenResponse {
        private String access_token;
        private int expires_in;

        public String getAccessToken() {
            return access_token;
        }

        public void setAccess_token(String access_token) {
            this.access_token = access_token;
        }

        public int getExpiresIn() {
            return expires_in;
        }

        public void setExpires_in(int expires_in) {
            this.expires_in = expires_in;
        }
    }
}