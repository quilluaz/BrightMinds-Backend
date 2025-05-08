package citu.jisaz.brightminds_backend.repository;

import citu.jisaz.brightminds_backend.model.User;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository("firestoreUserRepository")
public class FirestoreUserRepositoryImpl implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(FirestoreUserRepositoryImpl.class);
    private static final String COLLECTION_NAME = "users";
    private final CollectionReference usersCollection;

    public FirestoreUserRepositoryImpl(Firestore db) {
        this.usersCollection = db.collection(COLLECTION_NAME);
    }

    @Override
    public User save(User user) throws ExecutionException, InterruptedException {
        if (user.getUserId() == null || user.getUserId().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty when saving.");
        }
        DocumentReference userDocRef = usersCollection.document(user.getUserId());
        DocumentSnapshot currentSnapshot = userDocRef.get().get();

        if (!currentSnapshot.exists()) {
            user.setCreatedAt(null);
        } else {
            User existingUser = currentSnapshot.toObject(User.class);
            if (existingUser != null && existingUser.getCreatedAt() != null) {
                user.setCreatedAt(existingUser.getCreatedAt());
            } else if (existingUser == null) {
                logger.error("Existing user document {} could not be mapped to User object. Proceeding with potential new createdAt.", user.getUserId());
                user.setCreatedAt(null);
            }
        }
        user.setUpdatedAt(null);

        ApiFuture<WriteResult> writeFuture = userDocRef.set(user);
        WriteResult writeResult = writeFuture.get();

        if (user.getCreatedAt() == null) {
            user.setCreatedAt(writeResult.getUpdateTime().toDate());
        }
        user.setUpdatedAt(writeResult.getUpdateTime().toDate());
        return user;
    }

    @Override
    public Optional<User> findById(String userId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = usersCollection.document(userId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        if (document.exists()) {
            return Optional.ofNullable(document.toObject(User.class));
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(String email) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = usersCollection.whereEqualTo("email", email).limit(1).get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        if (!documents.isEmpty()) {
            return Optional.of(documents.getFirst().toObject(User.class));
        }
        return Optional.empty();
    }
}