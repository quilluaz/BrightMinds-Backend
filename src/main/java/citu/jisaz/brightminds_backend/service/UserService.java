package citu.jisaz.brightminds_backend.service;

import citu.jisaz.brightminds_backend.config.GamificationConfig;
import citu.jisaz.brightminds_backend.dto.CreateUserRequestDTO;
import citu.jisaz.brightminds_backend.dto.UpdateUserRequestDTO;
import citu.jisaz.brightminds_backend.dto.UserDTO;
import citu.jisaz.brightminds_backend.exception.EmailAlreadyExistsException;
import citu.jisaz.brightminds_backend.exception.UserNotFoundException;
import citu.jisaz.brightminds_backend.exception.BadRequestException;
import citu.jisaz.brightminds_backend.model.User;
import citu.jisaz.brightminds_backend.repository.UserRepository;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final GamificationConfig gamificationConfig;
    private final String masterTeacherEnrollmentCode;

    public UserService(FirebaseAuth firebaseAuth,
                       UserRepository userRepository,
                       GamificationConfig gamificationConfig,
                       @Value("${brightminds.teacher.enrollment-code}") String masterTeacherEnrollmentCode) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.gamificationConfig = gamificationConfig;
        this.masterTeacherEnrollmentCode = masterTeacherEnrollmentCode;
    }

    // ... existing createUser, getUserById, getUserByEmail, updateUser, validateNewEmailAvailability, awardXpAndLevelUp, convertToDTO methods ...

    public UserDTO createUser(CreateUserRequestDTO createUserRequest)
            throws FirebaseAuthException, ExecutionException, InterruptedException, EmailAlreadyExistsException, BadRequestException {

        logger.info("Attempting to create user with email: {} and role: {}",
                createUserRequest.getEmail(), createUserRequest.getRole());

        userRepository.findByEmail(createUserRequest.getEmail()).ifPresent(u -> {
            logger.warn("Email {} already exists in application database.", createUserRequest.getEmail());
            throw new EmailAlreadyExistsException("User with email " + createUserRequest.getEmail() + " already exists.");
        });

        if ("TEACHER".equalsIgnoreCase(createUserRequest.getRole())) {
            if (!this.masterTeacherEnrollmentCode.equals(createUserRequest.getTeacherEnrollmentCode())) {
                logger.warn("Invalid or missing teacher enrollment code for email: {} during registration as TEACHER.",
                        createUserRequest.getEmail());
                throw new BadRequestException("Invalid or missing teacher enrollment code required for TEACHER role.");
            }
            logger.info("Teacher enrollment code validated for email: {}", createUserRequest.getEmail());
        } else if (!"STUDENT".equalsIgnoreCase(createUserRequest.getRole())) {
            throw new BadRequestException("Invalid role specified: " + createUserRequest.getRole() +
                    ". Role must be STUDENT or TEACHER.");
        }

        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(createUserRequest.getEmail())
                .setEmailVerified(false)
                .setPassword(createUserRequest.getPassword())
                .setDisplayName(createUserRequest.getDisplayName())
                .setDisabled(false);
        UserRecord userRecord;
        try {
            userRecord = firebaseAuth.createUser(request);
        } catch (FirebaseAuthException e) {
            logger.error("Firebase Auth user creation failed for email {}: {} (Code: {})",
                    createUserRequest.getEmail(), e.getMessage(), e.getAuthErrorCode());
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new EmailAlreadyExistsException("User with email " + createUserRequest.getEmail() +
                        " already exists in Firebase Authentication.");
            }
            throw e;
        }

        User appUser = User.builder()
                .userId(userRecord.getUid())
                .email(userRecord.getEmail()).displayName(userRecord.getDisplayName())
                .role(createUserRequest.getRole().toUpperCase())
                .avatarUrl(createUserRequest.getAvatarUrl()).themePreference("LIGHT")
                .studentOfClassrooms(new ArrayList<>()).teacherOfClassrooms(new ArrayList<>())
                .build();

        if ("STUDENT".equalsIgnoreCase(appUser.getRole())) {
            appUser.setLevel(1); appUser.setCurrentXp(0L);
            appUser.setXpToNextLevel(gamificationConfig.calculateXpForNextLevel(1));
        }

        User savedAppUser = userRepository.save(appUser);
        logger.info("User '{}' ({}) created successfully with UID: {} and role: {}",
                savedAppUser.getDisplayName(), savedAppUser.getEmail(), savedAppUser.getUserId(), savedAppUser.getRole());
        return convertToDTO(savedAppUser);
    }

    public UserDTO getUserById(String userId) throws ExecutionException, InterruptedException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
        return convertToDTO(user);
    }

    public UserDTO getUserByEmail(String email) throws ExecutionException, InterruptedException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return convertToDTO(user);
    }

    public UserDTO updateUser(String userId, UpdateUserRequestDTO updateUserRequest)
            throws FirebaseAuthException, ExecutionException, InterruptedException {
        logger.info("Updating user with ID: {}", userId);

        User existingAppUser = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId + " for update."));

        UserRecord.UpdateRequest authUpdateRequest = new UserRecord.UpdateRequest(userId);
        boolean authNeedsUpdate = false;

        if (StringUtils.hasText(updateUserRequest.getDisplayName())) {
            authUpdateRequest.setDisplayName(updateUserRequest.getDisplayName());
            existingAppUser.setDisplayName(updateUserRequest.getDisplayName());
            authNeedsUpdate = true;
        }
        if (StringUtils.hasText(updateUserRequest.getPassword())) {
            authUpdateRequest.setPassword(updateUserRequest.getPassword());
            authNeedsUpdate = true;
        }
        if (StringUtils.hasText(updateUserRequest.getEmail()) &&
                !updateUserRequest.getEmail().equalsIgnoreCase(existingAppUser.getEmail())) {
            validateNewEmailAvailability(userId, updateUserRequest.getEmail());
            authUpdateRequest.setEmail(updateUserRequest.getEmail());
            existingAppUser.setEmail(updateUserRequest.getEmail());
            authNeedsUpdate = true;
        }

        if (authNeedsUpdate) {
            firebaseAuth.updateUser(authUpdateRequest);
            logger.info("Firebase Auth record updated for UID: {}", userId);
        }

        if (StringUtils.hasText(updateUserRequest.getAvatarUrl())) {
            existingAppUser.setAvatarUrl(updateUserRequest.getAvatarUrl());
        }
        if (StringUtils.hasText(updateUserRequest.getThemePreference())) {
            existingAppUser.setThemePreference(updateUserRequest.getThemePreference());
        }

        User updatedAppUser = userRepository.save(existingAppUser);
        logger.info("Firestore record updated for user: {}", updatedAppUser.getDisplayName());
        return convertToDTO(updatedAppUser);
    }

    private void validateNewEmailAvailability(String currentUserId, String newEmail)
            throws FirebaseAuthException, ExecutionException, InterruptedException {
        try {
            UserRecord userByNewEmail = firebaseAuth.getUserByEmail(newEmail);
            if (!userByNewEmail.getUid().equals(currentUserId)) {
                throw new EmailAlreadyExistsException("New email " + newEmail + " is already in use by another Firebase Auth user.");
            }
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                logger.error("FirebaseAuthException while checking new email availability for {}: {} (Code: {})",
                        newEmail, e.getMessage(), e.getAuthErrorCode());
                throw e;
            }
            // If USER_NOT_FOUND, proceed to check app database
        }
        userRepository.findByEmail(newEmail).ifPresent(appUser -> {
            if (!appUser.getUserId().equals(currentUserId)) {
                throw new EmailAlreadyExistsException("New email " + newEmail + " is already in use by another application user.");
            }
        });
    }

    public UserDTO awardXpAndLevelUp(String studentId, long xpEarned)
            throws ExecutionException, InterruptedException {
        if (xpEarned <= 0) {
            logger.debug("No XP awarded ({} XP) for student {}. Returning current state.", xpEarned, studentId);
            // Fetch and return current state to ensure DTO consistency
            return getUserById(studentId);
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new UserNotFoundException("Student not found with ID: " + studentId + " for awarding XP."));

        if (!"STUDENT".equalsIgnoreCase(student.getRole())) {
            logger.warn("Attempted to award XP to non-student user (ID: {}, Role: {}).", studentId, student.getRole());
            return convertToDTO(student); // Return DTO of the non-student user
        }

        // Ensure gamification fields are initialized if null
        student.setCurrentXp(Objects.requireNonNullElse(student.getCurrentXp(), 0L));
        student.setLevel(Objects.requireNonNullElse(student.getLevel(), 1));
        if (student.getLevel() <= 0) student.setLevel(1); // Ensure level is at least 1
        student.setXpToNextLevel(Objects.requireNonNullElse(student.getXpToNextLevel(),
                gamificationConfig.calculateXpForNextLevel(student.getLevel())));
        if (student.getXpToNextLevel() <= 0) { // Recalculate if invalid
            student.setXpToNextLevel(gamificationConfig.calculateXpForNextLevel(student.getLevel()));
        }


        long currentXpBeforeUpdate = student.getCurrentXp();
        int currentLevelBeforeUpdate = student.getLevel();
        long xpToNextLevelBeforeUpdate = student.getXpToNextLevel();

        student.setCurrentXp(currentXpBeforeUpdate + xpEarned);
        boolean leveledUp = false;

        while (student.getCurrentXp() >= student.getXpToNextLevel()) {
            leveledUp = true;
            long xpOver = student.getCurrentXp() - student.getXpToNextLevel();
            int oldLevel = student.getLevel();
            student.setLevel(oldLevel + 1);
            student.setCurrentXp(xpOver); // Set to overflow XP
            student.setXpToNextLevel(gamificationConfig.calculateXpForNextLevel(student.getLevel()));
            if (student.getXpToNextLevel() <= 0) { // Safety check for calculateXpForNextLevel
                logger.error("Calculated XpToNextLevel is invalid (<=0) for level {}. Setting to a default.", student.getLevel());
                student.setXpToNextLevel(Long.MAX_VALUE); // Prevent infinite loop
            }
        }

        User updatedStudent = userRepository.save(student);

        if (leveledUp) {
            logger.info("Student {} leveled up! Initial: Lvl {} (XP {}/{}), Awarded: {} XP. Final: Lvl {} (XP {}/{})",
                    studentId, currentLevelBeforeUpdate, currentXpBeforeUpdate, xpToNextLevelBeforeUpdate, xpEarned,
                    updatedStudent.getLevel(), updatedStudent.getCurrentXp(), updatedStudent.getXpToNextLevel());
        } else {
            logger.info("Student {} awarded {} XP. Initial: Lvl {} (XP {}/{}), Final: Lvl {} (XP {}/{})",
                    studentId, xpEarned, currentLevelBeforeUpdate, currentXpBeforeUpdate, xpToNextLevelBeforeUpdate,
                    updatedStudent.getLevel(), updatedStudent.getCurrentXp(), updatedStudent.getXpToNextLevel());
        }
        return convertToDTO(updatedStudent);
    }


    /**
     * Checks if the user with the given ID has the role "STUDENT".
     * This method is intended for use in @PreAuthorize expressions.
     *
     * @param userId The ID of the user to check.
     * @return true if the user is a student, false otherwise.
     * @throws ExecutionException if user retrieval fails.
     * @throws InterruptedException if user retrieval is interrupted.
     */
    public boolean isUserStudent(String userId) throws ExecutionException, InterruptedException {
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("isUserStudent check called with null or empty userId.");
            return false;
        }
        logger.trace("Service: Checking if user ID: {} is a student.", userId);
        try {
            User user = userRepository.findById(userId)
                    .orElse(null); // Don't throw UserNotFoundException directly in PreAuthorize checks, let it return false
            if (user != null && "STUDENT".equalsIgnoreCase(user.getRole())) {
                logger.trace("Service: User ID: {} is a student.", userId);
                return true;
            }
            logger.trace("Service: User ID: {} is not a student or not found. Role: {}", userId, user != null ? user.getRole() : "N/A");
            return false;
        } catch (Exception e) {
            // In a PreAuthorize context, it's often better to return false on error
            // than to let an exception propagate and cause a 500, unless the exception
            // itself is an AccessDeniedException or similar.
            logger.error("Service: Error checking if user {} is student: {}", userId, e.getMessage());
            return false;
        }
    }

    public UserDTO convertToDTO(User user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        dto.setUserId(user.getUserId()); dto.setDisplayName(user.getDisplayName());
        dto.setEmail(user.getEmail()); dto.setRole(user.getRole());
        dto.setAvatarUrl(user.getAvatarUrl()); dto.setThemePreference(user.getThemePreference());
        dto.setCreatedAt(user.getCreatedAt()); dto.setUpdatedAt(user.getUpdatedAt());
        if ("STUDENT".equalsIgnoreCase(user.getRole())) {
            dto.setLevel(user.getLevel());
            dto.setCurrentXp(user.getCurrentXp());
            dto.setXpToNextLevel(user.getXpToNextLevel());
        }
        return dto;
    }
}