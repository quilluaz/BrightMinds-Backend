package citu.jisaz.brightminds_backend.dto;

import lombok.Data;
import java.util.Date;

@Data
public class UserDTO {
    private String userId;
    private String displayName;
    private String email;
    private String role;
    private String avatarUrl;
    private String themePreference;
    private Date createdAt;
    private Date updatedAt;

    private Integer level;
    private Long currentXp;
    private Long xpToNextLevel;
}