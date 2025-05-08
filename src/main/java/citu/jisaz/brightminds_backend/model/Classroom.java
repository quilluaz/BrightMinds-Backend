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
public class Classroom {

    @DocumentId
    private String classroomId;

    private String name;
    private String teacherId;
    private String teacherName;
    private String uniqueCode;
    private String description;
    private String iconUrl;

    @ServerTimestamp
    private Date createdAt;
    @ServerTimestamp
    private Date updatedAt;

    private Integer studentCount;
    private Integer activityCount;
}