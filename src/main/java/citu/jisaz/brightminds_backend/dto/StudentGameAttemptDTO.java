package citu.jisaz.brightminds_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.Date;

@Data
public class StudentGameAttemptDTO {
    @NotBlank(message = "Student ID is required")
    private String studentId;
    @NotBlank(message = "Classroom ID is required")
    private String classroomId;
    @NotBlank(message = "Assigned Game ID is required")
    private String assignedGameId;
    @NotNull(message = "Score cannot be null")
    @Min(value = 0, message = "Score cannot be negative")
    private Integer score;
    @NotNull(message = "Total points possible cannot be null")
    @Min(value = 1, message = "Total points possible must be at least 1")
    private Integer totalPointsPossible;
    // private Integer timeTakenSeconds; // Optional: if the game provides this
    private String attemptId;
    private String libraryGameId;
    private Long xpEarned;
    private String status;
    private Date startedAt;
    private Date completedAt;
}