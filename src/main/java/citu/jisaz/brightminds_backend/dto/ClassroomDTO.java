package citu.jisaz.brightminds_backend.dto;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class ClassroomDTO {
    private String classroomId;
    private String name;
    private String teacherId;
    private String teacherName;
    private String uniqueCode;
    private String description;
    private String iconUrl;
    private Date createdAt;
    private Date updatedAt;
    private Integer studentCount;
    private Integer activityCount;
    private List<SimpleStudentDTO> students;
    private List<SimpleGameDTO> assignedGames;
}