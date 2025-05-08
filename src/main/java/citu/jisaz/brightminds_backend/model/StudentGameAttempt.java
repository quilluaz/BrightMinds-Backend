package citu.jisaz.brightminds_backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.annotation.ServerTimestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentGameAttempt {

    @DocumentId
    private String attemptId; // Auto-generated
    private String studentId; // FK to User
    private String classroomId; // FK to Classroom
    private String assignedGameId; // FK to Classroom's assignedGame
    private String libraryGameId; // FK to the global Game library
    private Integer score;
    private Integer totalPointsPossible;
    private Long xpEarned;
    private String status; // e.g., "COMPLETED", "IN_PROGRESS"
    // private Integer timeTakenSeconds; // Example: if external game provides this

    @ServerTimestamp
    private Date startedAt;
    @ServerTimestamp
    private Date completedAt;
}