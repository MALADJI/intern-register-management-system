package com.internregister.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "interns")
public class Intern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long internId;

    private String name;
    @Column(unique = true)
    private String email;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "supervisor_id")
    private Supervisor supervisor;

    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;

    private String field;
    private String employer;

    @Column(name = "id_number")
    private String idNumber;

    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    @Column(name = "end_date")
    private java.time.LocalDate endDate;

    @OneToOne(mappedBy = "intern", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true)
    @JsonManagedReference
    @ToString.Exclude
    private InternContract contractDocument;

    private Boolean active = true;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] signature;

    @OneToMany(mappedBy = "intern", cascade = CascadeType.ALL)
    @ToString.Exclude
    @JsonManagedReference
    private List<Attendance> attendanceRecords;

    @OneToMany(mappedBy = "intern", cascade = CascadeType.ALL)
    @ToString.Exclude
    @JsonManagedReference
    private List<LeaveRequest> leaveRequests;

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
}
