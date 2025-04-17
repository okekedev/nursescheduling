package nursescheduler.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class FhirAppointmentFetchService {

    private final RestTemplate restTemplate;

    public FhirAppointmentFetchService() {
        this.restTemplate = new RestTemplate();
    }

    public String fetchAppointments() {
        String url = "https://api.hchb.com/fhir/r4/Appointment";
        try {
            String response = restTemplate.getForObject(url, String.class);
            return "Fetched appointments: " + response;
        } catch (Exception e) {
            return "Error fetching appointments: " + e.getMessage();
        }
    }
}