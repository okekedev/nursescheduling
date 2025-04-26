package nursescheduler.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nursescheduler.service.TokenService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final TokenService tokenService;

    public ApiController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/fetch-test")
    public String fetchTest() {
        return "Fetch test endpoint works!";
    }

    @GetMapping("/test-token")
    public String testToken() {
        String token = tokenService.getBearerToken();
        return token != null ? "Token: " + token : "Failed to fetch token";
    }
}