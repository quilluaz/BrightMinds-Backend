package citu.jisaz.brightminds_backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.annotation.ServerTimestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @DocumentId
    private String userId;

    private String displayName;
    private String email;
    private String role; // "TEACHER" or "STUDENT"
    private String avatarUrl;
    private String themePreference;

    @ServerTimestamp
    private Date createdAt;

    @ServerTimestamp
    private Date updatedAt;

    private Integer level;
    private Long currentXp;
    private Long xpToNextLevel;

    @Builder.Default
    private List<String> studentOfClassrooms = new ArrayList<>();

    @Builder.Default
    private List<String> teacherOfClassrooms = new ArrayList<>();

    public static User initializeStudentGamification(User user) {
        if ("STUDENT".equalsIgnoreCase(user.getRole())) {
            user.setLevel(1);
            user.setCurrentXp(0L);
            user.setXpToNextLevel(100L);
        }
        return user;
    }
}