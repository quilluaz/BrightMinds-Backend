package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.StudentGameAttempt;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public interface StudentGameAttemptRepository {
    StudentGameAttempt save(StudentGameAttempt attempt) throws ExecutionException, InterruptedException;
    Optional<StudentGameAttempt> findById(String attemptId) throws ExecutionException, InterruptedException;
    List<StudentGameAttempt> findAllByStudentIdAndClassroomId(String studentId, String classroomId) throws ExecutionException, InterruptedException;
    List<StudentGameAttempt> findAllByStudentIdAndAssignedGameId(String studentId, String assignedGameId) throws ExecutionException, InterruptedException;

    List<StudentGameAttempt> findAllByClassroomIdAndAssignedGameId(String classroomId, String assignedGameId) throws ExecutionException, InterruptedException;
}