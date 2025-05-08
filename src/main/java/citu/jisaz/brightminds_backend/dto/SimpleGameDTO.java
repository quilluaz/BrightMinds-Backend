package citu.jisaz.brightminds_backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleGameDTO {
    private String assignedGameId;
    private String libraryGameId;
    private String gameTitle;
    private Date dueDate;
    private Integer maxAttemptsAllowed;
}