package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.Game;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
// Import WriteResult if you implement save/delete
// import com.google.cloud.firestore.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository // This annotation is crucial for Spring to detect it as a bean
public class FirestoreGameRepositoryImpl implements GameRepository {

    private static final Logger logger = LoggerFactory.getLogger(FirestoreGameRepositoryImpl.class);
    private static final String COLLECTION_NAME = "libraryGames"; // Or whatever your collection is named
    private final CollectionReference gamesCollection;

    public FirestoreGameRepositoryImpl(Firestore db) {
        this.gamesCollection = db.collection(COLLECTION_NAME);
    }

    @Override
    public Optional<Game> findById(String libraryGameId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = gamesCollection.document(libraryGameId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            Game game = document.toObject(Game.class);
            logger.debug("Found game by ID {}: {}", libraryGameId, game);
            return Optional.ofNullable(game);
        }
        logger.debug("No game found with ID: {}", libraryGameId);
        return Optional.empty();
    }

    @Override
    public List<Game> findAll() throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = gamesCollection.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        List<Game> games = documents.stream()
                .map(doc -> doc.toObject(Game.class))
                .collect(Collectors.toList());
        logger.debug("Found {} games in the library.", games.size());
        return games;
    }

    // Optional: Implement save and delete if needed later
    /*
    @Override
    public Game save(Game game) throws ExecutionException, InterruptedException {
        if (game.getLibraryGameId() == null || game.getLibraryGameId().isEmpty()) {
            DocumentReference docRef = gamesCollection.document();
            game.setLibraryGameId(docRef.getId());
        }
        // Handle createdAt/updatedAt similar to FirestoreClassroomRepositoryImpl if needed
        // For library games, these might be less critical if manually populated.
        ApiFuture<WriteResult> future = gamesCollection.document(game.getLibraryGameId()).set(game);
        future.get(); // Wait for completion
        logger.info("Game saved with ID: {}", game.getLibraryGameId());
        return game;
    }

    @Override
    public void deleteById(String libraryGameId) throws ExecutionException, InterruptedException {
        logger.info("Attempting to delete game with ID: {}", libraryGameId);
        ApiFuture<WriteResult> writeResult = gamesCollection.document(libraryGameId).delete();
        writeResult.get();
        logger.info("Successfully deleted game with ID: {}", libraryGameId);
    }
    */
}