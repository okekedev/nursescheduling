package nursescheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class FhirAppointmentFetchService {

    private static final Logger logger = LoggerFactory.getLogger(FhirAppointmentFetchService.class);
    private final RestTemplate restTemplate;

    @Value("${hchb.api-url}")
    private String apiUrl;

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

    public FhirAppointmentFetchService() {
        this.restTemplate = new RestTemplate();
    }

    public String fetchAppointments() {
        try {
            // Step 1: Fetch bearer token (refreshes if expired)
            String token = getBearerToken();
            if (token == null) {
                logger.error("Failed to fetch bearer token: Token is null");
                return "Error fetching token: No token received";
            }
            logger.debug("Using bearer token: {}", token);

            // Step 2: Fetch appointments with token
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Accept", "application/fhir+json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            logger.debug("Making GET request to {}", apiUrl);
            String response = restTemplate.exchange(apiUrl, org.springframework.http.HttpMethod.GET, entity, String.class).getBody();
            logger.debug("Received response: {}", response);
            return "Fetched appointments: " + response;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error fetching appointments: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return "Error fetching appointments: HTTP " + e.getStatusCode() + " - " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error fetching appointments: {}", e.getMessage(), e);
            return "Error fetching appointments: " + e.getMessage();
        }
    }

    private String getBearerToken() {
        // Check if token is valid (not expired, with 60-second buffer)
        if (accessToken != null && System.currentTimeMillis() < tokenExpiryTime - 60000) {
            logger.debug("Using cached bearer token");
            return accessToken;
        }

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
            logger.debug("Making POST request to token endpoint: {}", tokenUrl);
            TokenResponse response = restTemplate.postForObject(tokenUrl, request, TokenResponse.class);
            if (response != null && response.getAccessToken() != null) {
                accessToken = response.getAccessToken();
                tokenExpiryTime = System.currentTimeMillis() + (response.getExpiresIn() * 1000);
                logger.debug("Token fetched successfully: {}", accessToken);
                return accessToken;
            } else {
                logger.error("Token response is null or access_token is missing");
                return null;
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error fetching token: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error fetching token: {}", e.getMessage(), e);
            return null;
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