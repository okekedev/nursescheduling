package nursescheduler.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);
    private final RestTemplate restTemplate;

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

    public TokenService() {
        this.restTemplate = new RestTemplate();
        logger.info("TokenService initialized - standalone token fetch");
        logger.info("Properties: tokenUrl={}, clientId={}, agencySecret={}, resourceSecurityId={}",
                tokenUrl, clientId, agencySecret, resourceSecurityId);
    }

    public String getBearerToken() {
        // Check if token is valid (with 60-second buffer)
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
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            logger.debug("Raw token response: {}", response.getBody());
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String token = (String) response.getBody().get("access_token");
                Integer expiresIn = (Integer) response.getBody().get("expires_in");
                if (token != null) {
                    accessToken = token;
                    tokenExpiryTime = System.currentTimeMillis() + (expiresIn != null ? expiresIn * 1000L : 3600 * 1000L);
                    logger.info("Bearer token fetched successfully: {}", token);
                    return token;
                }
                logger.error("No access_token in response: {}", response.getBody());
                return null;
            }
            logger.error("Invalid token response: {}", response.getBody());
            return null;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error fetching token: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error fetching token: {}", e.getMessage(), e);
            return null;
        }
    }
}