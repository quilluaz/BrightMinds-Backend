package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.AssignedGame;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository("firestoreAssignedGameRepository")
public class FirestoreAssignedGameRepositoryImpl implements AssignedGameRepository {

    private static final Logger logger = LoggerFactory.getLogger(FirestoreAssignedGameRepositoryImpl.class);
    private static final String PARENT_COLLECTION_NAME = "classrooms";
    private static final String SUBCOLLECTION_NAME = "assignedGames";
    private final Firestore db;

    public FirestoreAssignedGameRepositoryImpl(Firestore db) {
        this.db = db;
    }

    private CollectionReference getAssignedGamesCollection(String classroomId) {
        return db.collection(PARENT_COLLECTION_NAME).document(classroomId).collection(SUBCOLLECTION_NAME);
    }

    @Override
    public AssignedGame save(String classroomId, AssignedGame assignedGame) throws ExecutionException, InterruptedException {
        CollectionReference assignedGamesCollection = getAssignedGamesCollection(classroomId);

        if (assignedGame.getAssignedGameId() == null || assignedGame.getAssignedGameId().isEmpty()) {
            DocumentReference newAssignedGameRef = assignedGamesCollection.document();
            assignedGame.setAssignedGameId(newAssignedGameRef.getId());
        }
        if (assignedGame.getClassroomId() == null || !assignedGame.getClassroomId().equals(classroomId)) {
            assignedGame.setClassroomId(classroomId);
        }

        DocumentReference assignedGameDocRef = assignedGamesCollection.document(assignedGame.getAssignedGameId());
        DocumentSnapshot currentSnapshot = assignedGameDocRef.get().get();

        if (!currentSnapshot.exists()) {
            assignedGame.setDateAssigned(null);
        } else {
            AssignedGame existingAssignment = currentSnapshot.toObject(AssignedGame.class);
            if (existingAssignment != null && existingAssignment.getDateAssigned() != null) {
                assignedGame.setDateAssigned(existingAssignment.getDateAssigned());
            } else if (existingAssignment == null){
                logger.error("Existing assigned game {}/{} could not be mapped. Proceeding with potential new dateAssigned.", classroomId, assignedGame.getAssignedGameId());
                assignedGame.setDateAssigned(null);
            }
        }

        ApiFuture<WriteResult> writeFuture = assignedGameDocRef.set(assignedGame);
        WriteResult writeResult = writeFuture.get();

        if (assignedGame.getDateAssigned() == null) {
            assignedGame.setDateAssigned(writeResult.getUpdateTime().toDate());
        }
        return assignedGame;
    }

    @Override
    public Optional<AssignedGame> findById(String classroomId, String assignedGameId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = getAssignedGamesCollection(classroomId).document(assignedGameId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            return Optional.ofNullable(document.toObject(AssignedGame.class));
        }
        return Optional.empty();
    }

    @Override
    public List<AssignedGame> findAllByClassroomId(String classroomId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = getAssignedGamesCollection(classroomId)
                .orderBy("dateAssigned", Query.Direction.DESCENDING) // Query.Direction.DESCENDING
                .get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        return documents.stream()
                .map(doc -> doc.toObject(AssignedGame.class))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String classroomId, String assignedGameId) throws ExecutionException, InterruptedException {
        logger.info("Attempting to delete assigned game {} from classroom {}", assignedGameId, classroomId);
        ApiFuture<WriteResult> writeResult = getAssignedGamesCollection(classroomId).document(assignedGameId).delete();
        writeResult.get();
        logger.info("Successfully deleted assigned game {} from classroom {}", assignedGameId, classroomId);
    }
}