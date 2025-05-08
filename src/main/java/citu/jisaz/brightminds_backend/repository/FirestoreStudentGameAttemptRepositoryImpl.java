package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.StudentGameAttempt;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class FirestoreStudentGameAttemptRepositoryImpl implements StudentGameAttemptRepository {

    private final CollectionReference attemptsCollection;
    private static final String COLLECTION_NAME = "studentGameAttempts";

    public FirestoreStudentGameAttemptRepositoryImpl(Firestore db) {
        this.attemptsCollection = db.collection(COLLECTION_NAME);
    }

    @Override
    public StudentGameAttempt save(StudentGameAttempt attempt) throws ExecutionException, InterruptedException {
        if (attempt.getAttemptId() == null || attempt.getAttemptId().isEmpty()) {
            DocumentReference docRef = attemptsCollection.document();
            attempt.setAttemptId(docRef.getId());
            ApiFuture<WriteResult> future = docRef.set(attempt);
            future.get();
        } else {
            ApiFuture<WriteResult> future = attemptsCollection.document(attempt.getAttemptId()).set(attempt);
            future.get();
        }
        return attempt;
    }

    @Override
    public Optional<StudentGameAttempt> findById(String attemptId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = attemptsCollection.document(attemptId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            return Optional.ofNullable(document.toObject(StudentGameAttempt.class));
        }
        return Optional.empty();
    }

    @Override
    public List<StudentGameAttempt> findAllByStudentIdAndClassroomId(String studentId, String classroomId) throws ExecutionException, InterruptedException {
        Query query = attemptsCollection
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("classroomId", classroomId);
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        return querySnapshot.get().getDocuments().stream()
                .map(doc -> doc.toObject(StudentGameAttempt.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<StudentGameAttempt> findAllByStudentIdAndAssignedGameId(String studentId, String assignedGameId) throws ExecutionException, InterruptedException {
        Query query = attemptsCollection
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("assignedGameId", assignedGameId);
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        return querySnapshot.get().getDocuments().stream()
                .map(doc -> doc.toObject(StudentGameAttempt.class))
                .collect(Collectors.toList());
    }

    // Implementation of the new method
    @Override
    public List<StudentGameAttempt> findAllByClassroomIdAndAssignedGameId(String classroomId, String assignedGameId) throws ExecutionException, InterruptedException {
        Query query = attemptsCollection
                .whereEqualTo("classroomId", classroomId)
                .whereEqualTo("assignedGameId", assignedGameId);
        // Add ordering if needed, e.g., by completion date
        // query = query.orderBy("completedAt", Query.Direction.DESCENDING);
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        return querySnapshot.get().getDocuments().stream()
                .map(doc -> doc.toObject(StudentGameAttempt.class))
                .collect(Collectors.toList());
    }
}