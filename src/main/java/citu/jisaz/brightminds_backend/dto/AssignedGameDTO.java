package citu.jisaz.brightminds_backend.dto;

import lombok.Data;
import java.util.Date;

@Data
public class AssignedGameDTO {
    private String assignedGameId;
    private String libraryGameId;
    private String gameTitle;
    private Date dateAssigned;
    private Date dueDate;
    private String gameDescription;
    private String gameUrlOrIdentifier;
    private Integer maxXpAwarded;
    private Integer totalPointsPossible;
    private Integer maxAttemptsAllowed;
    // private String studentStatus; // If this DTO is also for student's view of an assigned game
    // private Integer studentScore;  // If this DTO is also for student's view
}