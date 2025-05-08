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
public class AssignedGame {

    @DocumentId
    private String assignedGameId;

    private String libraryGameId;
    private String classroomId;
    private String gameTitle;
    private String gameDescription;
    private String gameUrlOrIdentifier;
    private Integer maxXpAwarded;
    private Integer totalPointsPossible;
    private Integer maxAttemptsAllowed;

    @ServerTimestamp
    private Date dateAssigned;
    private Date dueDate;
}