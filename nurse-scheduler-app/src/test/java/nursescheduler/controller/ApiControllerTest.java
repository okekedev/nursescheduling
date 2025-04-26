package nursescheduler.controller;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nursescheduler.service.TokenService;

@WebMvcTest(ApiController.class)
public class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @Test
    public void testFetchTestEndpoint() throws Exception {
        mockMvc.perform(get("/api/fetch-test"))
                .andExpect(status().isOk())
                .andExpect(content().string("Fetch test endpoint works!"));
    }

    @Test
    public void testTokenEndpoint() throws Exception {
        // Mock the TokenService behavior
        when(tokenService.getBearerToken()).thenReturn("mocked-token");

        mockMvc.perform(get("/api/test-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Token: mocked-token"));
    }
}