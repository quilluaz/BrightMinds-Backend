package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.Game;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public interface GameRepository {
    Optional<Game> findById(String libraryGameId) throws ExecutionException, InterruptedException;
    List<Game> findAll() throws ExecutionException, InterruptedException; // Or implement pagination
    // Game save(Game game) throws ExecutionException, InterruptedException; // If admins/devs add games via API
    // void deleteById(String libraryGameId) throws ExecutionException, InterruptedException; // If needed
}