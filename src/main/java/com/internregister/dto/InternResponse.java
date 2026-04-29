package com.internregister.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InternResponse {
    private Long id;
    private String name;
    private String email;
    private Long departmentId;
    private String departmentName;
    private Long supervisorId;
    private String supervisorName;
    private Long locationId;
    private String locationName;
    private String field;
    private String employer;
    private String idNumber;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private String contractAgreement;
    private Boolean hasContract;
    private Boolean active;
    private Boolean hasLoggedIn;
}
