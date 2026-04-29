package com.internregister.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class BulkInternInviteRequest {
    private List<InternInviteData> invites;
    private String message;

    @Getter
    @Setter
    public static class InternInviteData {
        private String email;
        private String name;
        private String password;
    }
}
