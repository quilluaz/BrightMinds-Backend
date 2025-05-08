package citu.jisaz.brightminds_backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleStudentDTO {
    private String userId;
    private String displayName;
    private String email;
    private String avatarUrl;
}