package citu.jisaz.brightminds_backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min; // Ensure this import is present
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Date;

@Data
public class AssignGameRequestDTO {
    @NotBlank
    private String libraryGameId;

    @NotNull(message = "Due date cannot be null")
    @Future(message = "Due date must be in the future")
    private Date dueDate;

    @Min(value = 1, message = "Maximum attempts must be at least 1, or leave blank for default.")
    private Integer maxAttemptsAllowed;
}