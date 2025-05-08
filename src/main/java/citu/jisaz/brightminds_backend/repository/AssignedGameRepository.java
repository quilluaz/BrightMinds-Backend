package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.AssignedGame;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public interface AssignedGameRepository {
    AssignedGame save(String classroomId, AssignedGame assignedGame) throws ExecutionException, InterruptedException;
    Optional<AssignedGame> findById(String classroomId, String assignedGameId) throws ExecutionException, InterruptedException;
    List<AssignedGame> findAllByClassroomId(String classroomId) throws ExecutionException, InterruptedException;
    void deleteById(String classroomId, String assignedGameId) throws ExecutionException, InterruptedException;
}