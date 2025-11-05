package com.internregister.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long attendanceId;

    private LocalDateTime date;
    private LocalDateTime timeIn;
    private LocalDateTime timeOut;
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;
    private String location;
    private Double latitude;  // Geolocation latitude
    private Double longitude; // Geolocation longitude
    private String signature;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }

    @ManyToOne
    @JoinColumn(name = "intern_id")
    @ToString.Exclude
    @JsonBackReference
    private Intern intern;
}
