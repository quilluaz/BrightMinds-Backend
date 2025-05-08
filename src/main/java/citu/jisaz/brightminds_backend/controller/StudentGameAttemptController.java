package citu.jisaz.brightminds_backend.controller;

import citu.jisaz.brightminds_backend.dto.StudentGameAttemptDTO;
import citu.jisaz.brightminds_backend.dto.UserDTO;
import citu.jisaz.brightminds_backend.exception.BadRequestException;
import citu.jisaz.brightminds_backend.model.User;
import citu.jisaz.brightminds_backend.service.ClassroomService;
import citu.jisaz.brightminds_backend.service.StudentGameAttemptService;
import jakarta.validation.Valid;
import org.slf4j.Logger; // Added Logger
import org.slf4j.LoggerFactory; // Added Logger
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// import org.springframework.security.access.AccessDeniedException; // Can be removed if @PreAuthorize handles all cases
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1") // Base path
public class StudentGameAttemptController {

    private static final Logger logger = LoggerFactory.getLogger(StudentGameAttemptController.class); // Added Logger

    private final StudentGameAttemptService studentGameAttemptService;

    public StudentGameAttemptController(StudentGameAttemptService studentGameAttemptService, ClassroomService classroomService) {
        this.studentGameAttemptService = studentGameAttemptService;
    }

    // Endpoint: POST /api/v1/game-attempts
    @PostMapping("/game-attempts")
    // Only a student can submit an attempt, and the studentId in the DTO must match the authenticated principal's userId.
    @PreAuthorize("hasRole('ROLE_STUDENT') and #attemptDTO.studentId == authentication.principal.userId")
    public ResponseEntity<UserDTO> submitGameAttempt(
            @Valid @RequestBody StudentGameAttemptDTO attemptDTO,
            Authentication authentication) throws ExecutionException, InterruptedException {
        // User principal = (User) authentication.getPrincipal(); // For logging if needed
        // logger.info("Student {} submitting game attempt for assignedGameId: {}", principal.getUserId(), attemptDTO.getAssignedGameId());
        UserDTO updatedStudent = studentGameAttemptService.processGameAttempt(attemptDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(updatedStudent);
    }

    // Endpoint: GET /api/v1/game-attempts/my-attempts
    @GetMapping("/game-attempts/my-attempts")
    // Only a student can view their own attempts.
    // If classroomId is provided, we also check if the student is enrolled in that classroom.
    @PreAuthorize("hasRole('ROLE_STUDENT') and (#classroomId == null or @classroomService.isStudentEnrolled(authentication.principal.userId, #classroomId))")
    public ResponseEntity<List<StudentGameAttemptDTO>> getMyGameAttempts(
            @RequestParam(required = false) String classroomId,
            @RequestParam(required = false) String assignedGameId, // This param helps filter but auth is mainly on student & classroom
            Authentication authentication) throws ExecutionException, InterruptedException {

        User authenticatedUser = (User) authentication.getPrincipal();
        String studentId = authenticatedUser.getUserId();
        // logger.info("Student {} fetching their game attempts. ClassroomId: {}, AssignedGameId: {}", studentId, classroomId, assignedGameId);

        List<StudentGameAttemptDTO> attempts;

        if (assignedGameId != null) {
            // Service should ideally also check if this assignedGameId belongs to a classroom the student is in,
            // or if the attemptDTOs returned are filtered by studentId implicitly.
            // Current PreAuthorize is a good start.
            attempts = studentGameAttemptService.getAttemptsForStudentOnAssignedGame(studentId, assignedGameId);
        } else if (classroomId != null) {
            // PreAuthorize already checks enrollment for this case.
            attempts = studentGameAttemptService.getAttemptsForStudentInClassroom(studentId, classroomId);
        } else {
            // If neither is provided, it means get ALL attempts for the student across ALL their involvements.
            // This is fine as long as the service method getAttemptsForStudentOnAssignedGame and
            // getAttemptsForStudentInClassroom correctly scope to the studentId.
            // For a more generic "all my attempts" without parameters:
            // attempts = studentGameAttemptService.getAllAttemptsForStudent(studentId); // Assuming such a method exists
            throw new BadRequestException("Either classroomId or assignedGameId must be provided, or use a dedicated 'all my attempts' endpoint if available.");
        }
        return ResponseEntity.ok(attempts);
    }

    // Endpoint: GET /api/v1/classrooms/{classroomId}/assigned-games/{assignedGameId}/attempts
    @GetMapping("/classrooms/{classroomId}/assigned-games/{assignedGameId}/attempts")
    // Only a teacher who owns the classroom can view attempts for an assigned game in that classroom.
    @PreAuthorize("hasRole('ROLE_TEACHER') and @classroomService.isTeacherOwnerOfClassroom(authentication.principal.userId, #classroomId)")
    public ResponseEntity<List<StudentGameAttemptDTO>> getAttemptsForAssignedGameInClassroom(
            @PathVariable String classroomId,
            @PathVariable String assignedGameId,
            Authentication authentication) throws ExecutionException, InterruptedException {

        User authenticatedTeacher = (User) authentication.getPrincipal();
        String teacherId = authenticatedTeacher.getUserId();
        // logger.info("Teacher {} viewing attempts for assignedGameId {} in classroom {}", teacherId, assignedGameId, classroomId);

        // The explicit check below is now handled by @PreAuthorize, can be removed for cleaner code.
        // if (!classroomService.isTeacherOwnerOfClassroom(teacherId, classroomId)) {
        //     throw new AccessDeniedException("Authenticated teacher does not own classroom " + classroomId +
        //             " or classroom does not exist.");
        // }

        List<StudentGameAttemptDTO> attempts = studentGameAttemptService.getAttemptsByClassroomIdAndAssignedGameId(classroomId, assignedGameId);
        return ResponseEntity.ok(attempts);
    }
}