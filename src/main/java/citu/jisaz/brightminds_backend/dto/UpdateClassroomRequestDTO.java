package citu.jisaz.brightminds_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateClassroomRequestDTO {
    @Size(min = 3, max = 100, message = "Classroom name must be between 3 and 100 characters")
    private String name;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    private String iconUrl;
}