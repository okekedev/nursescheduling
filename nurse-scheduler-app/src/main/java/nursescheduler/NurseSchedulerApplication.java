package nursescheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"nursescheduler"})
public class NurseSchedulerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(NurseSchedulerApplication.class, args);
    }
}