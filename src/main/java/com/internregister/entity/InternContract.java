package com.internregister.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "intern_contracts")
public class InternContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id", nullable = false)
    @JsonBackReference
    private Intern intern;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String contractAgreement;

    private java.time.LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = java.time.LocalDateTime.now();
    }
}
