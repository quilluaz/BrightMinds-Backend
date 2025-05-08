package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.Classroom;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot; // Keep this
import com.google.cloud.firestore.QuerySnapshot;       // Keep this
import com.google.cloud.firestore.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Date; // Import Date
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository("firestoreClassroomRepository") // Ensure the bean name is consistent if used elsewhere
public class FirestoreClassroomRepositoryImpl implements ClassroomRepository {

    private static final Logger logger = LoggerFactory.getLogger(FirestoreClassroomRepositoryImpl.class);
    private static final String COLLECTION_NAME = "classrooms";
    private final CollectionReference classroomsCollection;

    public FirestoreClassroomRepositoryImpl(Firestore db) {
        this.classroomsCollection = db.collection(COLLECTION_NAME);
    }

    @Override
    public Classroom save(Classroom classroom) throws ExecutionException, InterruptedException {
        DocumentReference classroomDocRef;
        boolean isNewClassroom = false;

        if (classroom.getClassroomId() == null || classroom.getClassroomId().isEmpty()) {
            classroomDocRef = classroomsCollection.document(); // Generate new ID
            classroom.setClassroomId(classroomDocRef.getId());
            isNewClassroom = true;
            classroom.setCreatedAt(null); // Firestore will set this on creation via @ServerTimestamp
            logger.debug("Saving new classroom with generated ID: {}", classroom.getClassroomId());
        } else {
            classroomDocRef = classroomsCollection.document(classroom.getClassroomId());
            // For updates, preserve existing createdAt. Fetch it if not already on the classroom object.
            // This step is important if the input 'classroom' object might not have 'createdAt' populated.
            if (classroom.getCreatedAt() == null) {
                DocumentSnapshot snapshot = classroomDocRef.get().get();
                if (snapshot.exists()) {
                    Classroom existing = snapshot.toObject(Classroom.class);
                    if (existing != null) {
                        classroom.setCreatedAt(existing.getCreatedAt()); // Preserve original creation timestamp
                    }
                } else {
                    // This case implies an update to a non-existent doc with a pre-set ID, treat as new.
                    isNewClassroom = true;
                    classroom.setCreatedAt(null);
                    logger.warn("Attempting to update classroom with ID {} but it does not exist. Treating as new.", classroom.getClassroomId());
                }
            }
            logger.debug("Updating existing classroom with ID: {}", classroom.getClassroomId());
        }

        // For both new and existing, ensure updatedAt is handled by @ServerTimestamp
        classroom.setUpdatedAt(null);

        ApiFuture<WriteResult> writeFuture = classroomDocRef.set(classroom);
        writeFuture.get(); // Wait for the write to complete

        // Fetch the persisted classroom to get server-generated timestamps
        DocumentSnapshot persistedSnapshot = classroomDocRef.get().get();
        Classroom persistedClassroom = persistedSnapshot.toObject(Classroom.class);

        if (persistedClassroom == null) {
            logger.error("Failed to fetch classroom {} after save operation.", classroom.getClassroomId());
            // Fallback to the input classroom, though timestamps might be client-side if @ServerTimestamp didn't behave as expected
            // or if mapping failed. This is a defensive measure.
            if (isNewClassroom && classroom.getCreatedAt() == null) classroom.setCreatedAt(new Date()); // Approximate
            if (classroom.getUpdatedAt() == null) classroom.setUpdatedAt(new Date()); // Approximate
            return classroom;
        }
        logger.info("Classroom {} saved/updated successfully at {}. CreatedAt: {}, UpdatedAt: {}",
                persistedClassroom.getClassroomId(), persistedSnapshot.getUpdateTime(), persistedClassroom.getCreatedAt(), persistedClassroom.getUpdatedAt());
        return persistedClassroom;
    }

    // ... other methods (findById, findAllByTeacherId, findByUniqueCode, deleteById) remain the same for now
    // but ensure they correctly use getters from Classroom.java like classroom.getClassroomId() etc.
    // The existing implementations of these methods look fine, assuming Classroom model has correct getters.

    @Override
    public Optional<Classroom> findById(String classroomId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = classroomsCollection.document(classroomId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            return Optional.ofNullable(document.toObject(Classroom.class));
        }
        return Optional.empty();
    }

    @Override
    public List<Classroom> findAllByTeacherId(String teacherId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = classroomsCollection.whereEqualTo("teacherId", teacherId).get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        return documents.stream()
                .map(doc -> doc.toObject(Classroom.class))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Classroom> findByUniqueCode(String uniqueCode) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = classroomsCollection.whereEqualTo("uniqueCode", uniqueCode).limit(1).get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        if (!documents.isEmpty()) {
            return Optional.of(documents.getFirst().toObject(Classroom.class));
        }
        return Optional.empty();
    }

    @Override
    public void deleteById(String classroomId) throws ExecutionException, InterruptedException {
        logger.info("Attempting to delete classroom with ID: {}", classroomId);
        ApiFuture<WriteResult> writeResult = classroomsCollection.document(classroomId).delete();
        writeResult.get();
        logger.info("Successfully deleted classroom with ID: {}", classroomId);
    }
}