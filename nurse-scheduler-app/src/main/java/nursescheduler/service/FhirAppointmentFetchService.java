package nursescheduler.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import nursescheduler.model.Appointment;
import nursescheduler.repository.AppointmentRepository;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Appointment.AppointmentParticipantComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FhirAppointmentFetchService {

    private static final Logger logger = LoggerFactory.getLogger(FhirFetchService.class);
    private final FhirContext fhirContext = FhirContext.forR4();
    private final IParser parser = fhirContext.newJsonParser();

    @Value("${hchb.api-url}")
    private String apiUrl;

    @Autowired
    private FhirAuthService authService;

    @Autowired
    private AppointmentRepository appointmentRepo;

    public void fetchAndSaveAppointments() {
        logger.info("Starting appointment fetch...");
        LocalDate startDate = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1); // Start of week
        LocalDate endDate = startDate.plusDays(4); // End of week (Mon-Fri)

        List<org.hl7.fhir.r4.model.Appointment> appointments = new ArrayList<>();
        LocalDate currentDay = startDate;

        while (!currentDay.isAfter(endDate)) {
            appointments.addAll(fetchAppointmentsForDay(currentDay));
            currentDay = currentDay.plusDays(1);
            try {
                Thread.sleep(2000); // Rate limiting
            } catch (InterruptedException e) {
                logger.error("Sleep interrupted: {}", e.getMessage());
            }
        }

        processAppointments(appointments);
        logger.info("Completed fetching {} appointments", appointments.size());
    }

    private List<org.hl7.fhir.r4.model.Appointment> fetchAppointmentsForDay(LocalDate day) {
        List<org.hl7.fhir.r4.model.Appointment> dayAppointments = new ArrayList<>();
        String dayStr = day.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String url = String.format("%s?_count=500&date=ge%s&date=le%s", apiUrl, dayStr, dayStr);
        RestTemplate restTemplate = new RestTemplate();
        int page = 1;

        while (url != null) {
            logger.info("Fetching page {} for day {}: {}", page, dayStr, url);
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + authService.getBearerToken());
            headers.add("Accept", "application/fhir+json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            try {
                String response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
                Bundle bundle = parser.parseResource(Bundle.class, response);
                dayAppointments.addAll(bundle.getEntry().stream()
                        .filter(e -> e.getResource() instanceof org.hl7.fhir.r4.model.Appointment)
                        .map(e -> (org.hl7.fhir.r4.model.Appointment) e.getResource())
                        .toList());

                url = bundle.getLink(Bundle.LINK_NEXT) != null ? bundle.getLink(Bundle.LINK_NEXT).getUrl() : null;
                page++;
                if (url != null) {
                    Thread.sleep(2000); // Rate limiting
                }
            } catch (Exception e) {
                logger.error("Failed to fetch page {} for day {}: {}", page, dayStr, e.getMessage());
                break;
            }
        }

        logger.info("Fetched {} appointments for day {}", dayAppointments.size(), dayStr);
        return dayAppointments;
    }

    private void processAppointments(List<org.hl7.fhir.r4.model.Appointment> appointments) {
        for (org.hl7.fhir.r4.model.Appointment appt : appointments) {
            Appointment appointment = new Appointment();
            appointment.setVisitId(appt.getIdElement().getIdPart());
            appointment.setStatus(appt.getStatus() != null ? appt.getStatus().toCode() : "");

            // Start time and duration
            if (appt.getStart() != null) {
                appointment.setStartTime(LocalDateTime.parse(
                        appt.getStartElement().asStringValue(), DateTimeFormatter.ISO_DATE_TIME));
            }
            appointment.setDuration(appt.getMinutesDuration() != null ? appt.getMinutesDuration() : 0);

            // Service type
            if (!appt.getServiceType().isEmpty() && !appt.getServiceType().get(0).getCoding().isEmpty()) {
                appointment.setServiceType(appt.getServiceType().get(0).getCoding().get(0).getDisplay());
            }

            // Extract extensions
            String patientId = null;
            String locationId = null;
            String detailedStatus = null;
            for (Extension ext : appt.getExtension()) {
                if ("https://api.hchb.com/fhir/r4/StructureDefinition/subject".equals(ext.getUrl())) {
                    patientId = ext.getValue().castToReference(ext.getValue()).getReference();
                    if (patientId != null && patientId.startsWith("Patient/")) {
                        patientId = patientId.replace("Patient/", "");
                    }
                } else if ("https://api.hchb.com/fhir/r4/StructureDefinition/service-location".equals(ext.getUrl())) {
                    locationId = ext.getValue().castToReference(ext.getValue()).getReference();
                    if (locationId != null && locationId.startsWith("Location/")) {
                        locationId = locationId.replace("Location/", "");
                    }
                } else if ("https://api.hchb.com/fhir/r4/StructureDefinition/detailed-status".equals(ext.getUrl())) {
                    for (Extension subExt : ext.getExtension()) {
                        if ("StatusValue".equals(subExt.getUrl())) {
                            detailedStatus = subExt.getValue().castToString(subExt.getValue()).getValue();
                        }
                    }
                }
            }

            // Participants (Practitioner)
            String practitionerId = null;
            for (AppointmentParticipantComponent participant : appt.getParticipant()) {
                if (participant.getActor() != null && participant.getActor().getReference().startsWith("Practitioner/")) {
                    practitionerId = participant.getActor().getReference().replace("Practitioner/", "");
                }
            }

            // Set IDs
            appointment.setPractitionerId(practitionerId);
            appointment.setPatientId(patientId);
            appointment.setLocationId(locationId);
            appointment.setDetailedStatus(detailedStatus);

            appointmentRepo.save(appointment);
        }
    }
}