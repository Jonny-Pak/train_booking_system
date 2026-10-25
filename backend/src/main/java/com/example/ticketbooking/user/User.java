package com.example.ticketbooking.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() { }

    public static User customer(String fullName, String email, String passwordHash,
                                String phone, Instant createdAt) {
        User user = new User();
        user.fullName = fullName;
        user.email = email;
        user.passwordHash = passwordHash;
        user.phone = phone;
        user.role = Role.CUSTOMER;
        user.createdAt = createdAt;
        return user;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    @JsonIgnore
    public String getPasswordHash() { return passwordHash; }
    public String getPhone() { return phone; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
