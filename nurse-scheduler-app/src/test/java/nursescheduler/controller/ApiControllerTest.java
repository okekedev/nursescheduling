package nursescheduler.controller;

import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testFetchTestEndpoint() throws Exception {
        mockMvc.perform(get("/api/fetch-test"))
               .andExpect(status().isOk())
               .andExpect(content().string("Fetch test endpoint works!"));
    }

    @Test
    public void testFetchAppointmentsEndpoint() throws Exception {
        mockMvc.perform(get("/api/fetch-appointments"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Error fetching appointments")));
    }
}