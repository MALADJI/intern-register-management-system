package com.internregister.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "email_leave_updates")
    private boolean emailLeaveUpdates = true;

    @Column(name = "email_attendance_alerts")
    private boolean emailAttendanceAlerts = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private Frequency frequency = Frequency.INSTANT;

    public enum Frequency { INSTANT, DAILY, WEEKLY }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public boolean isEmailLeaveUpdates() { return emailLeaveUpdates; }
    public void setEmailLeaveUpdates(boolean emailLeaveUpdates) { this.emailLeaveUpdates = emailLeaveUpdates; }

    public boolean isEmailAttendanceAlerts() { return emailAttendanceAlerts; }
    public void setEmailAttendanceAlerts(boolean emailAttendanceAlerts) { this.emailAttendanceAlerts = emailAttendanceAlerts; }

    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }
}


