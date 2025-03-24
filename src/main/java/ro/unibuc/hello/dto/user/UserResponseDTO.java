package ro.unibuc.hello.dto.user;

import java.time.Instant;
import java.util.List;

import ro.unibuc.hello.enums.Role;
import ro.unibuc.hello.model.User;

public class UserResponseDTO {
    private String firstName;
    private String lastName;
    private String mail;
    private String phoneNumber;
    private Instant createdAt;
    private Double avgRating;
    private List<Role> roles;
    
    public UserResponseDTO(String firstName, String lastName, String mail, 
                            String phoneNumber, Instant createdAt, 
                            Double rating) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.mail = mail;
        this.phoneNumber = phoneNumber;
        this.createdAt = createdAt;
        this.avgRating = rating;
    }

    public static UserResponseDTO toDTO(User user) {
        UserResponseDTO dto = new UserResponseDTO(
            user.getFirstName(),
            user.getLastName(),
            user.getMail(),
            user.getPhoneNumber(),
            user.getCreatedAt(),    
            user.getAvgRating()
        );
        
        return dto;
    }   

    public Double getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(Double rating) {
        this.avgRating = rating;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    

    public List<Role> getRoles() {
        return roles;
    }

    public void setRole(List<Role> roles) {
        this.roles = roles;
    }
}
