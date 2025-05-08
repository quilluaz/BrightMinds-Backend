package citu.jisaz.brightminds_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EnrollStudentRequestDTO {
    private String classroomCode;
    private String studentEmail;
}