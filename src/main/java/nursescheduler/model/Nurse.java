package nursescheduler.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "nurse")
public class Nurse {

    @Id
    private Long id;
    private String name;
    private Double latitude;
    private Double longitude;
    private Boolean fieldStaff; // Add this line - stores whether the nurse is field staff

    // Default constructor
    public Nurse() {
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    // Add these getter and setter for fieldStaff
    public Boolean getFieldStaff() {
        return fieldStaff;
    }

    public void setFieldStaff(Boolean fieldStaff) {
        this.fieldStaff = fieldStaff;
    }
}