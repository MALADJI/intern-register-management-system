package com.internregister.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InternRequest {
    private String name;
    private String email;
    private Long departmentId;
    private Long supervisorId;
    private Long locationId;
    private String field;
    private String employer;
    private String idNumber;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate endDate;
    private String password;
}
