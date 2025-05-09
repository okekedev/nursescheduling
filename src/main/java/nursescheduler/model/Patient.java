package nursescheduler.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity class representing a patient
 */
@Entity
@Table(name = "patient")
public class Patient {
    
    @Id
    private Long id;
    
    private String name;
    private String address;
    private String city;
    private String state;
    private String zip;
    private String county;
    private String phone;
    
    // Visit details
    private String time;     // Appointment time, e.g. "09:00 AM"
    private int duration;    // Visit duration in minutes
    
    // Location coordinates for mapping and routing
    private double latitude;
    private double longitude;
    
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
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    public String getZip() {
        return zip;
    }
    
    public void setZip(String zip) {
        this.zip = zip;
    }
    
    public String getCounty() {
        return county;
    }
    
    public void setCounty(String county) {
        this.county = county;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public String getTime() {
        return time;
    }
    
    public void setTime(String time) {
        this.time = time;
    }
    
    public int getDuration() {
        return duration;
    }
    
    public void setDuration(int duration) {
        this.duration = duration;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}