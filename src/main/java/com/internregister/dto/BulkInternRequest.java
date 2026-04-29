package com.internregister.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkInternRequest {
    @jakarta.validation.Valid
    private List<InternRequest> interns;
    private String defaultPassword;
    private Boolean sendInvites = false;
}
