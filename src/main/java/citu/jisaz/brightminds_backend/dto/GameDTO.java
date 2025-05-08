package citu.jisaz.brightminds_backend.dto;

import lombok.Data;
import java.util.Date;

@Data
public class GameDTO {
    private String libraryGameId;
    private String title;
    private String description;
    private Integer gradeLevel;
    private String difficulty;
    private String gameUrlOrIdentifier;
    private Integer maxXpAwarded;
    private Integer totalPointsPossible;
    private Date createdAt;
    private Date updatedAt;
}