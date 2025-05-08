package citu.jisaz.brightminds_backend.config;

import com.google.cloud.firestore.Firestore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FirestoreShutdownManager {

    private static final Logger logger = LoggerFactory.getLogger(FirestoreShutdownManager.class);
    private final Firestore firestore;

    @Autowired // Use constructor injection
    public FirestoreShutdownManager(Firestore firestore) {
        this.firestore = firestore;
    }

    @PreDestroy // This method runs just before the application context closes
    public void shutdown() {
        logger.info("Shutting down Firestore client...");
        try {
            firestore.close();
            logger.info("Firestore client closed successfully.");
        } catch (Exception e) {
            // Log error but don't prevent shutdown
            logger.error("Error closing Firestore client during application shutdown.", e);
        }
    }
}