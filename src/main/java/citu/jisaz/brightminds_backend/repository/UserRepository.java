package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.User;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public interface UserRepository {
    User save(User user) throws ExecutionException, InterruptedException;
    Optional<User> findById(String userId) throws ExecutionException, InterruptedException;
    Optional<User> findByEmail(String email) throws ExecutionException, InterruptedException;
    // Potentially: void deleteById(String userId) throws ExecutionException, InterruptedException;
    // Potentially: List<User> findAll();
}