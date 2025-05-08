package citu.jisaz.brightminds_backend.service;

import citu.jisaz.brightminds_backend.config.GamificationConfig;
import citu.jisaz.brightminds_backend.dto.StudentGameAttemptDTO;
import citu.jisaz.brightminds_backend.dto.UserDTO;
import citu.jisaz.brightminds_backend.exception.BadRequestException;
import citu.jisaz.brightminds_backend.exception.ResourceNotFoundException;
import citu.jisaz.brightminds_backend.exception.UserNotFoundException;
import citu.jisaz.brightminds_backend.model.AssignedGame;
import citu.jisaz.brightminds_backend.model.StudentGameAttempt;
import citu.jisaz.brightminds_backend.model.User;
import citu.jisaz.brightminds_backend.repository.StudentGameAttemptRepository;
import citu.jisaz.brightminds_backend.repository.UserRepository;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class StudentGameAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(StudentGameAttemptService.class);

    private final Firestore db;
    private final StudentGameAttemptRepository attemptRepository;
    private final UserService userService;
    private final GamificationConfig gamificationConfig;
    private final UserRepository userRepository;

    // Collection name constants. Ideally, repository-specific constants like
    // STUDENT_GAME_ATTEMPTS_COLLECTION would be in the respective repository interface.
    private static final String STUDENT_GAME_ATTEMPTS_COLLECTION = "studentGameAttempts";
    private static final String USERS_COLLECTION = "users";
    private static final String CLASSROOMS_COLLECTION = "classrooms";
    private static final String ASSIGNED_GAMES_SUBCOLLECTION = "assignedGames";


    @Value("${gamification.default-max-game-attempts:3}")
    private int defaultMaxGameAttempts;

    public StudentGameAttemptService(Firestore db,
                                     StudentGameAttemptRepository attemptRepository,
                                     UserService userService,
                                     GamificationConfig gamificationConfig,
                                     UserRepository userRepository) {
        this.db = db;
        this.attemptRepository = attemptRepository;
        this.userService = userService;
        this.gamificationConfig = gamificationConfig;
        this.userRepository = userRepository;
    }

    public UserDTO processGameAttempt(StudentGameAttemptDTO attemptDTO)
            throws ExecutionException, InterruptedException {

        logger.info("Processing game attempt for student {}, classroom {}, assigned game {}",
                attemptDTO.getStudentId(), attemptDTO.getClassroomId(), attemptDTO.getAssignedGameId());

        User updatedStudentModelFromTransaction = db.runTransaction(transaction -> {
            DocumentReference studentRef = db.collection(USERS_COLLECTION).document(attemptDTO.getStudentId());
            DocumentSnapshot studentSnap = transaction.get(studentRef).get();
            if (!studentSnap.exists()) {
                throw new UserNotFoundException("Student not found with ID: " + attemptDTO.getStudentId());
            }
            User student = Objects.requireNonNull(studentSnap.toObject(User.class),
                    "Student data mapping failed for ID: " + attemptDTO.getStudentId());
            // User.role is a String "TEACHER" or "STUDENT"
            if (!"STUDENT".equalsIgnoreCase(student.getRole())) {
                throw new BadRequestException("User " + student.getDisplayName() + " (ID: " + student.getUserId() + ") is not a student.");
            }

            DocumentReference assignedGameRef = db.collection(CLASSROOMS_COLLECTION)
                    .document(attemptDTO.getClassroomId())
                    .collection(ASSIGNED_GAMES_SUBCOLLECTION) // This is the subcollection for assigned games
                    .document(attemptDTO.getAssignedGameId());
            DocumentSnapshot assignedGameSnap = transaction.get(assignedGameRef).get();
            if (!assignedGameSnap.exists()) {
                throw new ResourceNotFoundException("AssignedGame not found with ID " + attemptDTO.getAssignedGameId()
                        + " in classroom " + attemptDTO.getClassroomId());
            }
            // AssignedGame model fields are confirmed from AssignedGame.java
            AssignedGame assignedGame = Objects.requireNonNull(assignedGameSnap.toObject(AssignedGame.class),
                    "AssignedGame data mapping failed for ID: " + attemptDTO.getAssignedGameId());

            if (assignedGame.getDueDate() != null && new Date().after(assignedGame.getDueDate())) {
                logger.warn("Game attempt for assigned game {} by student {} is overdue. Due: {}, Submitted: {}",
                        assignedGame.getAssignedGameId(), student.getUserId(), assignedGame.getDueDate(), new Date());
            }

            CollectionReference attemptsCollectionRef = db.collection(STUDENT_GAME_ATTEMPTS_COLLECTION);
            Query existingAttemptsQuery = attemptsCollectionRef
                    .whereEqualTo("studentId", student.getUserId())
                    .whereEqualTo("assignedGameId", assignedGame.getAssignedGameId());

            QuerySnapshot existingAttemptsSnap = transaction.get(existingAttemptsQuery).get();
            int currentAttemptCount = existingAttemptsSnap.size();

            int maxAttemptsAllowed = assignedGame.getMaxAttemptsAllowed() != null && assignedGame.getMaxAttemptsAllowed() >= 0
                    ? assignedGame.getMaxAttemptsAllowed()
                    : defaultMaxGameAttempts;

            if (maxAttemptsAllowed > 0 && currentAttemptCount >= maxAttemptsAllowed) {
                logger.warn("Student {} has reached max attempts ({}) for assigned game {}.",
                        student.getUserId(), maxAttemptsAllowed, assignedGame.getAssignedGameId());
                throw new BadRequestException("Maximum attempts (" + maxAttemptsAllowed + ") reached for this game.");
            }

            long xpEarned = 0;
            Integer gameMaxXp = assignedGame.getMaxXpAwarded();
            Integer gameTotalPoints = assignedGame.getTotalPointsPossible();
            Integer studentScore = attemptDTO.getScore();

            if (attemptDTO.getTotalPointsPossible() != null && !attemptDTO.getTotalPointsPossible().equals(gameTotalPoints)) {
                logger.warn("Total points possible in DTO ({}) does not match game record ({}). Using game record value for XP calculation for game {}.",
                        attemptDTO.getTotalPointsPossible(), gameTotalPoints, assignedGame.getAssignedGameId());
            }

            if (gameMaxXp != null && gameMaxXp > 0 &&
                    gameTotalPoints != null && gameTotalPoints > 0 &&
                    studentScore != null && studentScore >= 0) {
                studentScore = Math.min(studentScore, gameTotalPoints); // Ensure score doesn't exceed max
                double scorePercentage = (double) studentScore / gameTotalPoints;
                xpEarned = Math.round(scorePercentage * gameMaxXp);
                xpEarned = Math.max(0, Math.min(xpEarned, gameMaxXp)); // Clamp XP
            } else {
                logger.warn("Could not calculate XP for assigned game {} (student {}). Game MaxXP: {}, Game TotalPoints: {}, Student Score: {}",
                        assignedGame.getAssignedGameId(), student.getUserId(), gameMaxXp, gameTotalPoints, studentScore);
            }

            DocumentReference newAttemptRef = attemptsCollectionRef.document();
            // StudentGameAttempt model fields startedAt and completedAt have @ServerTimestamp
            StudentGameAttempt newAttempt = StudentGameAttempt.builder()
                    .attemptId(newAttemptRef.getId())
                    .studentId(student.getUserId())
                    .classroomId(attemptDTO.getClassroomId())
                    .assignedGameId(assignedGame.getAssignedGameId())
                    .libraryGameId(assignedGame.getLibraryGameId()) // From AssignedGame model
                    .score(studentScore)
                    .totalPointsPossible(gameTotalPoints) // Store authoritative total points
                    .xpEarned(xpEarned)
                    .status("COMPLETED")
                    .startedAt(null) // Firestore will set this due to @ServerTimestamp
                    .completedAt(null) // Firestore will set this due to @ServerTimestamp
                    .build();
            transaction.set(newAttemptRef, newAttempt);

            if (xpEarned > 0) {
                if (student.getCurrentXp() == null) student.setCurrentXp(0L);
                if (student.getLevel() == null || student.getLevel() <= 0) student.setLevel(1);
                // GamificationConfig.calculateXpForNextLevel confirmed from GamificationConfig.java
                if (student.getXpToNextLevel() == null || student.getXpToNextLevel() <= 0) {
                    student.setXpToNextLevel(gamificationConfig.calculateXpForNextLevel(student.getLevel()));
                }

                long newCurrentXp = student.getCurrentXp() + xpEarned;
                student.setCurrentXp(newCurrentXp);
                boolean leveledUpInTransaction = false;
                int oldLevelForLog = student.getLevel();

                while (student.getCurrentXp() >= student.getXpToNextLevel()) {
                    leveledUpInTransaction = true;
                    long xpOver = student.getCurrentXp() - student.getXpToNextLevel();
                    student.setLevel(student.getLevel() + 1);
                    student.setCurrentXp(xpOver);
                    student.setXpToNextLevel(gamificationConfig.calculateXpForNextLevel(student.getLevel()));
                }
                // User model has updatedAt with @ServerTimestamp, so no need to set it manually.
                // Firestore handles it if the document is updated.
                transaction.set(studentRef, student);

                if (leveledUpInTransaction) {
                    logger.info("TRANSACTION: Student {} leveled up from Lvl {} to Lvl {}. XP: {}/{}",
                            student.getUserId(), oldLevelForLog, student.getLevel(), student.getCurrentXp(), student.getXpToNextLevel());
                } else {
                    logger.info("TRANSACTION: Student {} awarded {} XP. New XP: {}/{}, Level: {}.",
                            student.getUserId(), xpEarned, student.getCurrentXp(), student.getXpToNextLevel(), student.getLevel());
                }
            }
            return student;
        }).get();

        logger.info("Game attempt processed successfully for student {}. Final Level: {}, XP: {}/{}",
                updatedStudentModelFromTransaction.getUserId(),
                updatedStudentModelFromTransaction.getLevel(),
                updatedStudentModelFromTransaction.getCurrentXp(),
                updatedStudentModelFromTransaction.getXpToNextLevel());
        // UserService.convertToDTO confirmed from UserService.java
        return userService.convertToDTO(updatedStudentModelFromTransaction);
    }

    public List<StudentGameAttemptDTO> getAttemptsForStudentInClassroom(String studentId, String classroomId)
            throws ExecutionException, InterruptedException {
        // UserRepository.findById confirmed from UserRepository.java
        userRepository.findById(studentId)
                .orElseThrow(() -> new UserNotFoundException("Student not found with ID: " + studentId));
        logger.debug("Fetching attempts for student {} in classroom {}", studentId, classroomId);
        List<StudentGameAttempt> attempts = attemptRepository.findAllByStudentIdAndClassroomId(studentId, classroomId);
        return attempts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<StudentGameAttemptDTO> getAttemptsForStudentOnAssignedGame(String studentId, String assignedGameId)
            throws ExecutionException, InterruptedException {
        userRepository.findById(studentId)
                .orElseThrow(() -> new UserNotFoundException("Student not found with ID: " + studentId));
        logger.debug("Fetching attempts for student {} on assigned game {}", studentId, assignedGameId);
        List<StudentGameAttempt> attempts = attemptRepository.findAllByStudentIdAndAssignedGameId(studentId, assignedGameId);
        return attempts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public StudentGameAttemptDTO getAttemptById(String attemptId) throws ExecutionException, InterruptedException {
        logger.debug("Fetching attempt by ID: {}", attemptId);
        StudentGameAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Student game attempt not found with ID: " + attemptId));
        return convertToDTO(attempt);
    }

    public List<StudentGameAttemptDTO> getAttemptsByClassroomIdAndAssignedGameId(String classroomId, String assignedGameId)
            throws ExecutionException, InterruptedException {
        logger.debug("Fetching all attempts for assigned game {} in classroom {}", assignedGameId, classroomId);
        // StudentGameAttemptRepository.findAllByClassroomIdAndAssignedGameId confirmed from StudentGameAttemptRepository.java
        List<StudentGameAttempt> attempts = attemptRepository.findAllByClassroomIdAndAssignedGameId(classroomId, assignedGameId);
        return attempts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public StudentGameAttemptDTO convertToDTO(StudentGameAttempt attempt) {
        if (attempt == null) {
            return null;
        }
        // StudentGameAttemptDTO fields confirmed from StudentGameAttemptDTO.java
        // StudentGameAttempt model fields confirmed from StudentGameAttempt.java
        StudentGameAttemptDTO dto = new StudentGameAttemptDTO();
        dto.setAttemptId(attempt.getAttemptId());
        dto.setStudentId(attempt.getStudentId());
        dto.setClassroomId(attempt.getClassroomId());
        dto.setAssignedGameId(attempt.getAssignedGameId());
        dto.setLibraryGameId(attempt.getLibraryGameId());
        dto.setScore(attempt.getScore());
        dto.setTotalPointsPossible(attempt.getTotalPointsPossible());
        dto.setXpEarned(attempt.getXpEarned());
        dto.setStatus(attempt.getStatus());
        dto.setStartedAt(attempt.getStartedAt());
        dto.setCompletedAt(attempt.getCompletedAt());
        return dto;
    }
}