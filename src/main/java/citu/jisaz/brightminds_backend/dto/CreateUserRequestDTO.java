package citu.jisaz.brightminds_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUserRequestDTO {
    @NotBlank(message = "Display name cannot be blank")
    @Size(min = 2, max = 50, message = "Display name must be between 2 and 50 characters")
    private String displayName;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    @NotBlank(message = "Role cannot be blank")
    @Pattern(regexp = "STUDENT|TEACHER", message = "Role must be either STUDENT or TEACHER")
    private String role;

    private String avatarUrl;
    private String teacherEnrollmentCode;
}