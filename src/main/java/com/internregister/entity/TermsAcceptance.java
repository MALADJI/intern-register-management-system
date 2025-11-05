package com.internregister.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "terms_acceptance")
public class TermsAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private boolean accepted = false;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "version", nullable = false)
    private String version = "v1";

    @Column(name = "ip_address")
    private String ipAddress;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}


