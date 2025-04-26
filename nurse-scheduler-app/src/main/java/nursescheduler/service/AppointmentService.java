package nursescheduler.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nursescheduler.model.Appointment;

@Service
public class AppointmentService {

    private final TokenService tokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final String FHIR_APPOINTMENT_URL = "https://api.hchb.com/fhir/r4/appointment";

    @Autowired
    public AppointmentService(TokenService tokenService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Appointment> fetchAppointments() {
        try {
            String token = tokenService.getBearerToken();
            if (token == null) {
                throw new RuntimeException("Failed to obtain bearer token");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                FHIR_APPOINTMENT_URL,
                HttpMethod.GET,
                entity,
                String.class
            );

            return parseAppointments(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Error fetching appointments: " + e.getMessage(), e);
        }
    }

    private List<Appointment> parseAppointments(String responseBody) throws Exception {
        List<Appointment> appointments = new ArrayList<>();
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode entryArray = root.path("entry");

        for (JsonNode entry : entryArray) {
            JsonNode resource = entry.path("resource");
            Appointment appointment = new Appointment();

            appointment.setVisitId(resource.path("id").asText());
            appointment.setStatus(resource.path("status").asText());
            
            // Parse participants
            JsonNode participants = resource.path("participant");
            for (JsonNode participant : participants) {
                JsonNode actor = participant.path("actor");
                String reference = actor.path("reference").asText();
                if (reference.startsWith("Practitioner")) {
                    appointment.setPractitionerId(reference.replace("Practitioner/", ""));
                } else if (reference.startsWith("Patient")) {
                    appointment.setPatientId(reference.replace("Patient/", ""));
                } else if (reference.startsWith("Location")) {
                    appointment.setLocationId(reference.replace("Location/", ""));
                }
            }

            // Parse start time and duration
            String startTime = resource.path("start").asText();
            if (!startTime.isEmpty()) {
                appointment.setStartTime(LocalDateTime.parse(startTime));
            }

            JsonNode minutesDuration = resource.path("minutesDuration");
            if (!minutesDuration.isMissingNode()) {
                appointment.setDuration(minutesDuration.asInt());
            }

            // Parse service type
            JsonNode serviceTypeArray = resource.path("serviceType");
            if (serviceTypeArray.isArray() && serviceTypeArray.size() > 0) {
                JsonNode coding = serviceTypeArray.get(0).path("coding");
                if (coding.isArray() && coding.size() > 0) {
                    appointment.setServiceType(coding.get(0).path("code").asText());
                }
            }

            appointments.add(appointment);
        }

        return appointments;
    }
}