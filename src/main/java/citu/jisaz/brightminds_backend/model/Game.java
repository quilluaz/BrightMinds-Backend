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
public class Game {

    @DocumentId
    private String libraryGameId;

    private String title;
    private String description;
    private Integer gradeLevel;
    private String difficulty; // "EASY", "MEDIUM", "HARD"
    private String gameUrlOrIdentifier;
    private Integer maxXpAwarded;
    private Integer totalPointsPossible;

    @ServerTimestamp
    private Date createdAt;
    @ServerTimestamp
    private Date updatedAt;
}