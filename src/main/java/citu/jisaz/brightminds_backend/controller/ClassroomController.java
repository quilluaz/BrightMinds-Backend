package citu.jisaz.brightminds_backend.controller;

import citu.jisaz.brightminds_backend.dto.*;
import citu.jisaz.brightminds_backend.model.User;
import citu.jisaz.brightminds_backend.service.ClassroomService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/classrooms")
public class ClassroomController {

    private static final Logger logger = LoggerFactory.getLogger(ClassroomController.class);
    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_TEACHER')") // Only teachers can create classrooms
    public ResponseEntity<ClassroomDTO> createClassroom(
            @Valid @RequestBody CreateClassroomRequestDTO createRequest,
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String teacherId = principal.getUserId();
        logger.info("Teacher {} attempting to create classroom: {}", teacherId, createRequest.getName());
        ClassroomDTO newClassroom = classroomService.createClassroom(createRequest, teacherId);
        return ResponseEntity.status(HttpStatus.CREATED).body(newClassroom);
    }

    @GetMapping("/{classroomId}")
    @PreAuthorize("isAuthenticated() and (@classroomService.isTeacherOwnerOfClassroom(authentication.principal.userId, #classroomId) or @classroomService.isStudentEnrolled(authentication.principal.userId, #classroomId))")
    public ResponseEntity<ClassroomDTO> getClassroomById(
            @PathVariable String classroomId,
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        logger.info("User {} fetching classroom by ID: {}", principal.getUserId(), classroomId);
        ClassroomDTO classroom = classroomService.getClassroomById(classroomId);
        return ResponseEntity.ok(classroom);
    }

    @PutMapping("/{classroomId}")
    // Only the teacher who owns the classroom can update it.
    @PreAuthorize("hasRole('ROLE_TEACHER') and @classroomService.isTeacherOwnerOfClassroom(authentication.principal.userId, #classroomId)")
    public ResponseEntity<ClassroomDTO> updateClassroom(
            @PathVariable String classroomId,
            @Valid @RequestBody UpdateClassroomRequestDTO updateRequest,
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String teacherId = principal.getUserId();
        logger.info("Teacher {} attempting to update classroom: {}", teacherId, classroomId);
        ClassroomDTO updatedClassroom = classroomService.updateClassroom(classroomId, teacherId, updateRequest);
        return ResponseEntity.ok(updatedClassroom);
    }

    @GetMapping("/my-teaching")
    @PreAuthorize("hasRole('ROLE_TEACHER')") // Only teachers can see the classrooms they teach
    public ResponseEntity<List<ClassroomDTO>> getMyTeachingClassrooms(
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String teacherId = principal.getUserId();
        logger.info("Teacher {} fetching their classrooms", teacherId);
        List<ClassroomDTO> classrooms = classroomService.getClassroomsByTeacherId(teacherId);
        return ResponseEntity.ok(classrooms);
    }

    @GetMapping("/my-enrolled")
    @PreAuthorize("hasRole('ROLE_STUDENT')") // Only students can see the classrooms they are enrolled in
    public ResponseEntity<List<ClassroomDTO>> getMyEnrolledClassrooms(
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String studentId = principal.getUserId();
        logger.info("Student {} fetching their enrolled classrooms", studentId);
        List<ClassroomDTO> classrooms = classroomService.getClassroomsByStudentId(studentId);
        return ResponseEntity.ok(classrooms);
    }

    @PostMapping("/enroll")
    @PreAuthorize("hasRole('ROLE_STUDENT')") // Only students can enroll in a classroom
    public ResponseEntity<ClassroomDTO> enrollStudentByCode(
            @Valid @RequestBody EnrollStudentRequestDTO enrollRequest,
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String studentId = principal.getUserId();
        logger.info("Student {} attempting to enroll using code: {}", studentId, enrollRequest.getClassroomCode());
        ClassroomDTO classroom = classroomService.enrollStudentByCode(studentId, enrollRequest.getClassroomCode());
        return ResponseEntity.ok(classroom);
    }

    @PostMapping("/{classroomId}/students")
    // Only the teacher who owns the classroom can add students.
    @PreAuthorize("hasRole('ROLE_TEACHER') and @classroomService.isTeacherOwnerOfClassroom(authentication.principal.userId, #classroomId)")
    public ResponseEntity<ClassroomDTO> addStudentToClassroomByEmail(
            @PathVariable String classroomId,
            @Valid @RequestBody EnrollStudentRequestDTO enrollRequest, // DTO contains studentEmail
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String teacherId = principal.getUserId();
        logger.info("Teacher {} attempting to add student {} to classroom {}",
                teacherId, enrollRequest.getStudentEmail(), classroomId);
        ClassroomDTO classroom = classroomService.addStudentToClassroomByEmail(teacherId, classroomId, enrollRequest.getStudentEmail());
        return ResponseEntity.ok(classroom);
    }

    @DeleteMapping("/{classroomId}/students/{studentIdToRemove}")
    // Only the teacher who owns the classroom can remove students.
    // Additionally, a teacher cannot remove themselves if they are listed as a student (edge case, likely not applicable here).
    // A student cannot remove another student.
    @PreAuthorize("hasRole('ROLE_TEACHER') and @classroomService.isTeacherOwnerOfClassroom(authentication.principal.userId, #classroomId)")
    public ResponseEntity<ClassroomDTO> removeStudentFromClassroom(
            @PathVariable String classroomId,
            @PathVariable String studentIdToRemove,
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String teacherId = principal.getUserId();
        logger.info("Teacher {} attempting to remove student {} from classroom {}",
                teacherId, studentIdToRemove, classroomId);
        ClassroomDTO classroom = classroomService.removeStudentFromClassroom(teacherId, classroomId, studentIdToRemove);
        return ResponseEntity.ok(classroom);
    }

    @GetMapping("/{classroomId}/students")
    // Only the teacher who owns the classroom can view the list of enrolled students.
    @PreAuthorize("hasRole('ROLE_TEACHER') and @classroomService.isTeacherOwnerOfClassroom(authentication.principal.userId, #classroomId)")
    public ResponseEntity<List<UserDTO>> getEnrolledStudents(
            @PathVariable String classroomId,
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String teacherId = principal.getUserId();
        logger.info("Teacher {} attempting to view students for classroom {}", teacherId, classroomId);
        List<UserDTO> students = classroomService.getEnrolledStudents(classroomId, teacherId);
        return ResponseEntity.ok(students);
    }

    @PostMapping("/{classroomId}/games")
    // Only the teacher who owns the classroom can assign games.
    @PreAuthorize("hasRole('ROLE_TEACHER') and @classroomService.isTeacherOwnerOfClassroom(authentication.principal.userId, #classroomId)")
    public ResponseEntity<AssignedGameDTO> assignGameToClassroom(
            @PathVariable String classroomId,
            @Valid @RequestBody AssignGameRequestDTO assignRequest,
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String teacherId = principal.getUserId();
        logger.info("Teacher {} attempting to assign game {} to classroom {}",
                teacherId, assignRequest.getLibraryGameId(), classroomId);
        AssignedGameDTO assignedGame = classroomService.assignGameToClassroom(teacherId, classroomId, assignRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignedGame);
    }

    @GetMapping("/{classroomId}/games")
    // Teacher who owns it OR student enrolled in it can get the list of assigned games.
    @PreAuthorize("isAuthenticated() and (@classroomService.isTeacherOwnerOfClassroom(authentication.principal.userId, #classroomId) or @classroomService.isStudentEnrolled(authentication.principal.userId, #classroomId))")
    public ResponseEntity<List<AssignedGameDTO>> getAssignedGames(
            @PathVariable String classroomId,
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        logger.info("User {} fetching assigned games for classroom: {}", principal.getUserId(), classroomId);
        List<AssignedGameDTO> assignedGames = classroomService.getAssignedGamesForClassroom(classroomId);
        return ResponseEntity.ok(assignedGames);
    }

    @DeleteMapping("/{classroomId}/games/{assignedGameId}")
    // Only the teacher who owns the classroom can remove (unassign) games.
    @PreAuthorize("hasRole('ROLE_TEACHER') and @classroomService.isTeacherOwnerOfClassroom(authentication.principal.userId, #classroomId)")
    public ResponseEntity<Void> removeGameFromClassroom(
            @PathVariable String classroomId,
            @PathVariable String assignedGameId,
            Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String teacherId = principal.getUserId();
        logger.info("Teacher {} attempting to remove assigned game {} from classroom {}",
                teacherId, assignedGameId, classroomId);
        classroomService.removeGameFromClassroom(teacherId, classroomId, assignedGameId);
        return ResponseEntity.noContent().build();
    }
}