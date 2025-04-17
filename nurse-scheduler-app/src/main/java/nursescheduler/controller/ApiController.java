package nursescheduler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nursescheduler.service.FhirAppointmentFetchService;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private FhirAppointmentFetchService fhirAppointmentFetchService;

    @GetMapping("/fetch-test")
    public String fetchTest() {
        return "Fetch test endpoint works!";
    }

    @GetMapping("/fetch-appointments")
    public String fetchAppointments() {
    return "Error fetching appointments";
    }   
}