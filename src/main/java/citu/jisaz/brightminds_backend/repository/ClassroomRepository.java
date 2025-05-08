package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.Classroom;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public interface ClassroomRepository {
    Classroom save(Classroom classroom) throws ExecutionException, InterruptedException;
    Optional<Classroom> findById(String classroomId) throws ExecutionException, InterruptedException;
    List<Classroom> findAllByTeacherId(String teacherId) throws ExecutionException, InterruptedException;
    Optional<Classroom> findByUniqueCode(String uniqueCode) throws ExecutionException, InterruptedException;
    void deleteById(String classroomId) throws ExecutionException, InterruptedException;
}