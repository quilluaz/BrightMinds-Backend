package citu.jisaz.brightminds_backend.service;

import citu.jisaz.brightminds_backend.dto.*;
import citu.jisaz.brightminds_backend.exception.BadRequestException;
import citu.jisaz.brightminds_backend.exception.ResourceNotFoundException;
import citu.jisaz.brightminds_backend.exception.UserNotFoundException;
import citu.jisaz.brightminds_backend.model.AssignedGame;
import citu.jisaz.brightminds_backend.model.Classroom;
import citu.jisaz.brightminds_backend.model.Game;
import citu.jisaz.brightminds_backend.model.User;
import citu.jisaz.brightminds_backend.repository.AssignedGameRepository;
import citu.jisaz.brightminds_backend.repository.ClassroomRepository;
import citu.jisaz.brightminds_backend.repository.GameRepository;
import citu.jisaz.brightminds_backend.repository.UserRepository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class ClassroomService {

    private static final Logger logger = LoggerFactory.getLogger(ClassroomService.class);

    private final Firestore db;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final AssignedGameRepository assignedGameRepository;
    private final GameRepository gameRepository;

    // Collection name constants
    private static final String USERS_COLLECTION = "users";
    private static final String CLASSROOMS_COLLECTION = "classrooms";
    private static final String ASSIGNED_GAMES_SUBCOLLECTION = "assignedGames";
    private static final String ENROLLED_STUDENTS_SUBCOLLECTION = "enrolledStudents";


    public ClassroomService(Firestore db,
                            ClassroomRepository classroomRepository,
                            UserRepository userRepository,
                            AssignedGameRepository assignedGameRepository,
                            GameRepository gameRepository) {
        this.db = db;
        this.classroomRepository = classroomRepository;
        this.userRepository = userRepository;
        this.assignedGameRepository = assignedGameRepository;
        this.gameRepository = gameRepository;
    }

    public ClassroomDTO createClassroom(CreateClassroomRequestDTO createRequest, String teacherId)
            throws ExecutionException, InterruptedException {
        logger.debug("Service: Attempting to create classroom with name '{}' by teacherId: {}", createRequest.getName(), teacherId);
        Classroom savedClassroom = db.runTransaction(transaction -> {
            DocumentReference teacherRef = db.collection(USERS_COLLECTION).document(teacherId);
            DocumentSnapshot teacherSnap = transaction.get(teacherRef).get();
            if (!teacherSnap.exists()) {
                logger.warn("Service TX: Teacher with ID: {} not found during classroom creation.", teacherId);
                throw new UserNotFoundException("Teacher not found with ID: " + teacherId);
            }
            User teacher = Objects.requireNonNull(teacherSnap.toObject(User.class),
                    "Teacher data could not be mapped for ID: " + teacherId);
            if (!"TEACHER".equalsIgnoreCase(teacher.getRole())) {
                logger.warn("Service TX: User with ID: {} is not a teacher. Role: {}", teacherId, teacher.getRole());
                throw new BadRequestException("User with ID: " + teacherId + " is not a teacher.");
            }

            DocumentReference newClassroomRef = db.collection(CLASSROOMS_COLLECTION).document();
            Classroom classroom = Classroom.builder()
                    .classroomId(newClassroomRef.getId())
                    .name(createRequest.getName())
                    .description(createRequest.getDescription())
                    .iconUrl(createRequest.getIconUrl())
                    .teacherId(teacher.getUserId())
                    .teacherName(teacher.getDisplayName())
                    .uniqueCode(generateUniqueClassroomCode())
                    .studentCount(0).activityCount(0)
                    .createdAt(null).updatedAt(null)
                    .build();
            transaction.set(newClassroomRef, classroom);
            logger.debug("Service TX: Classroom object set for new ID: {}", classroom.getClassroomId());

            List<String> teacherClassrooms = new ArrayList<>(teacher.getTeacherOfClassrooms());
            teacherClassrooms.add(classroom.getClassroomId());
            teacher.setTeacherOfClassrooms(teacherClassrooms);
            transaction.set(teacherRef, teacher);
            logger.debug("Service TX: Teacher {}'s classroom list updated with new classroomId: {}", teacherId, classroom.getClassroomId());
            return classroom;
        }).get();

        logger.info("Service: Classroom '{}' (ID: {}) created successfully by teacher {} (ID: {}) with code: {}",
                savedClassroom.getName(), savedClassroom.getClassroomId(), savedClassroom.getTeacherName(), teacherId, savedClassroom.getUniqueCode());
        return convertToDTO(classroomRepository.findById(savedClassroom.getClassroomId()).orElse(savedClassroom));
    }

    public ClassroomDTO getClassroomById(String classroomId) throws ExecutionException, InterruptedException {
        logger.debug("Service: Fetching classroom by ID: {}", classroomId);
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> {
                    logger.warn("Service: Classroom not found with ID: {}", classroomId);
                    return new ResourceNotFoundException("Classroom", "ID", classroomId);
                });
        return convertToDTO(classroom);
    }

    public List<ClassroomDTO> getClassroomsByTeacherId(String teacherId) throws ExecutionException, InterruptedException {
        logger.debug("Service: Fetching all classrooms for teacherId: {}", teacherId);
        return classroomRepository.findAllByTeacherId(teacherId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ClassroomDTO> getClassroomsByStudentId(String studentId) throws ExecutionException, InterruptedException {
        logger.debug("Service: Fetching enrolled classrooms for studentId: {}", studentId);
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> {
                    logger.warn("Service: Student not found with ID: {} while fetching their enrolled classrooms.", studentId);
                    return new UserNotFoundException("Student not found with ID: " + studentId);
                });
        if (student.getStudentOfClassrooms() == null || student.getStudentOfClassrooms().isEmpty()) {
            logger.debug("Service: Student {} has no enrolled classrooms listed in User object.", studentId);
            return new ArrayList<>();
        }
        List<ClassroomDTO> classrooms = new ArrayList<>();
        for (String classroomIdFromList : student.getStudentOfClassrooms()) {
            logger.trace("Service: Fetching details for student's enrolled classroomId: {} (studentId: {})", classroomIdFromList, studentId);
            classroomRepository.findById(classroomIdFromList).ifPresent(c -> classrooms.add(convertToDTO(c)));
        }
        return classrooms;
    }

    public ClassroomDTO updateClassroom(String classroomId, String teacherId, UpdateClassroomRequestDTO updateRequest)
            throws ExecutionException, InterruptedException {
        logger.debug("Service: Attempting to update classroomId: {} by teacherId: {}", classroomId, teacherId);
        Classroom updatedClassroom = db.runTransaction(transaction -> {
            DocumentReference classroomRef = db.collection(CLASSROOMS_COLLECTION).document(classroomId);
            DocumentSnapshot classroomSnap = transaction.get(classroomRef).get();
            if (!classroomSnap.exists()) {
                logger.warn("Service TX: Classroom with ID: {} not found for update operation.", classroomId);
                throw new ResourceNotFoundException("Classroom", "ID", classroomId);
            }
            Classroom classroom = Objects.requireNonNull(classroomSnap.toObject(Classroom.class),
                    "Classroom data could not be mapped for ID: " + classroomId);

            if (!Objects.equals(classroom.getTeacherId(), teacherId)) {
                logger.warn("Service TX: TeacherId: {} is not the owner of classroomId: {}. Actual owner is: {}", teacherId, classroomId, classroom.getTeacherId());
                throw new BadRequestException("User " + teacherId + " is not the owner of classroom " + classroomId);
            }
            boolean needsUpdateInDb = false;
            if (StringUtils.hasText(updateRequest.getName()) && !Objects.equals(updateRequest.getName(), classroom.getName())) {
                classroom.setName(updateRequest.getName()); needsUpdateInDb = true;
            }
            if (updateRequest.getDescription() != null && !Objects.equals(updateRequest.getDescription(), classroom.getDescription())) {
                classroom.setDescription(updateRequest.getDescription()); needsUpdateInDb = true;
            }
            if (updateRequest.getIconUrl() != null && !Objects.equals(updateRequest.getIconUrl(), classroom.getIconUrl())) {
                classroom.setIconUrl(updateRequest.getIconUrl()); needsUpdateInDb = true;
            }
            if (needsUpdateInDb) {
                transaction.set(classroomRef, classroom);
                logger.debug("Service TX: Classroom object updated in DB for ID: {}", classroomId);
            } else {
                logger.debug("Service TX: No fields changed for classroom ID: {}. No DB update performed.", classroomId);
            }
            return classroom;
        }).get();

        logger.info("Service: Classroom ID: {} updated successfully by teacherId: {}", classroomId, teacherId);
        return convertToDTO(classroomRepository.findById(updatedClassroom.getClassroomId()).orElse(updatedClassroom));
    }

    public ClassroomDTO enrollStudentByCode(String studentId, String classroomCode)
            throws ExecutionException, InterruptedException {
        logger.debug("Service: StudentId: {} attempting enrollment with classroomCode: {}", studentId, classroomCode);
        Classroom finalClassroomState = db.runTransaction(transaction -> {
            DocumentReference studentRef = db.collection(USERS_COLLECTION).document(studentId);
            DocumentSnapshot studentSnap = transaction.get(studentRef).get();
            if (!studentSnap.exists()) {
                logger.warn("Service TX: Student with ID: {} not found for enrollment by code.", studentId);
                throw new UserNotFoundException("Student not found with ID: " + studentId);
            }
            User student = Objects.requireNonNull(studentSnap.toObject(User.class),
                    "Student data could not be mapped for ID: " + studentId);
            if (!"STUDENT".equalsIgnoreCase(student.getRole())) {
                logger.warn("Service TX: User with ID: {} is not a student. Role: {}", studentId, student.getRole());
                throw new BadRequestException("User " + studentId + " is not a valid student.");
            }

            Query classroomQuery = db.collection(CLASSROOMS_COLLECTION).whereEqualTo("uniqueCode", classroomCode).limit(1);
            QuerySnapshot classroomQuerySnap = transaction.get(classroomQuery).get();
            if (classroomQuerySnap.isEmpty()) {
                logger.warn("Service TX: Classroom not found with code: {} for student enrollment.", classroomCode);
                throw new ResourceNotFoundException("Classroom not found with code: " + classroomCode);
            }
            DocumentSnapshot classroomSnap = classroomQuerySnap.getDocuments().getFirst();
            Classroom classroom = Objects.requireNonNull(classroomSnap.toObject(Classroom.class),
                    "Classroom data could not be mapped for code: " + classroomCode);

            // Refined Log (Original line: 225, now around here)
            if (student.getStudentOfClassrooms().contains(classroom.getClassroomId())) {
                logger.info("Service TX: Student {} (ID: {}) is already enrolled in classroom {} (ID: {}) via code. No enrollment changes made.",
                        student.getDisplayName(), student.getUserId(), classroom.getName(), classroom.getClassroomId());
                return classroom;
            }

            student.getStudentOfClassrooms().add(classroom.getClassroomId());
            transaction.set(studentRef, student);
            logger.debug("Service TX: Student {} (ID: {})'s classroom list updated with classroomId: {}.", student.getDisplayName(), student.getUserId(), classroom.getClassroomId());

            classroom.setStudentCount(classroom.getStudentCount() == null ? 1 : classroom.getStudentCount() + 1);
            transaction.set(classroomSnap.getReference(), classroom);
            logger.debug("Service TX: Classroom {} (ID: {}) student count incremented to {} after enrollment via code.", classroom.getName(), classroom.getClassroomId(), classroom.getStudentCount());

            DocumentReference enrollmentRef = classroomSnap.getReference()
                    .collection(ENROLLED_STUDENTS_SUBCOLLECTION).document(studentId);
            Map<String, Object> enrollmentData = new HashMap<>();
            enrollmentData.put("studentName", student.getDisplayName());
            enrollmentData.put("studentEmail", student.getEmail());
            enrollmentData.put("dateEnrolled", FieldValue.serverTimestamp());
            transaction.set(enrollmentRef, enrollmentData);
            logger.debug("Service TX: Enrollment subcollection record created for student {} in classroom {}.", studentId, classroom.getClassroomId());
            return classroom;
        }).get();

        logger.info("Service: Student (ID: {}) successfully enrolled by code in classroom '{}' (ID: {}). New student count: {}.",
                studentId, finalClassroomState.getName(), finalClassroomState.getClassroomId(), finalClassroomState.getStudentCount());
        return convertToDTO(classroomRepository.findById(finalClassroomState.getClassroomId()).orElse(finalClassroomState));
    }

    public ClassroomDTO addStudentToClassroomByEmail(String teacherId, String classroomId, String studentEmail)
            throws ExecutionException, InterruptedException {
        logger.debug("Service: TeacherId: {} adding student by email: {} to classroomId: {}", teacherId, studentEmail, classroomId);
        Classroom finalClassroomState = db.runTransaction(transaction -> {
            DocumentReference classroomRef = db.collection(CLASSROOMS_COLLECTION).document(classroomId);
            DocumentSnapshot classroomSnap = transaction.get(classroomRef).get();
            if (!classroomSnap.exists()) {
                logger.warn("Service TX: Classroom with ID: {} not found for adding student by email.", classroomId);
                throw new ResourceNotFoundException("Classroom", "ID", classroomId);
            }
            Classroom classroom = Objects.requireNonNull(classroomSnap.toObject(Classroom.class),
                    "Classroom data could not be mapped for ID: " + classroomId);

            if (!Objects.equals(classroom.getTeacherId(), teacherId)) {
                logger.warn("Service TX: TeacherId: {} is not the owner of classroomId: {}. Actual owner: {}", teacherId, classroomId, classroom.getTeacherId());
                throw new BadRequestException("User " + teacherId + " is not owner of classroom " + classroomId);
            }

            Query studentQuery = db.collection(USERS_COLLECTION).whereEqualTo("email", studentEmail).limit(1);
            QuerySnapshot studentQuerySnap = transaction.get(studentQuery).get();
            if (studentQuerySnap.isEmpty()) {
                logger.warn("Service TX: Student not found with email: {} for adding to classroom {}.", studentEmail, classroomId);
                throw new UserNotFoundException("Student not found with email: " + studentEmail);
            }
            DocumentSnapshot studentSnap = studentQuerySnap.getDocuments().getFirst();
            User student = Objects.requireNonNull(studentSnap.toObject(User.class),
                    "Student data could not be mapped for email: " + studentEmail);
            if (!"STUDENT".equalsIgnoreCase(student.getRole())) {
                logger.warn("Service TX: User with email: {} is not a student. Role: {}", studentEmail, student.getRole());
                throw new BadRequestException("User with email " + studentEmail + " is not a student.");
            }
            // Refined Log (Original line: 287, now around here)
            if (student.getStudentOfClassrooms().contains(classroomId)) {
                logger.info("Service TX: Student {} (Email: {}) added by teacher is already present in classroom {} (ID: {}). No enrollment changes made.",
                        student.getDisplayName(), studentEmail, classroom.getName(), classroomId);
                return classroom;
            }

            student.getStudentOfClassrooms().add(classroomId);
            transaction.set(studentSnap.getReference(), student);
            logger.debug("Service TX: Student {} (Email: {})'s classroom list updated with classroomId: {}.", student.getDisplayName(), studentEmail, classroomId);

            classroom.setStudentCount(classroom.getStudentCount() == null ? 1 : classroom.getStudentCount() + 1);
            transaction.set(classroomRef, classroom);
            logger.debug("Service TX: Classroom {} (ID: {}) student count incremented to {} after adding student by email.", classroom.getName(), classroomId, classroom.getStudentCount());


            DocumentReference enrollmentRef = classroomRef.collection(ENROLLED_STUDENTS_SUBCOLLECTION).document(student.getUserId());
            Map<String, Object> enrollmentData = new HashMap<>();
            enrollmentData.put("studentName", student.getDisplayName());
            enrollmentData.put("studentEmail", student.getEmail());
            enrollmentData.put("dateEnrolled", FieldValue.serverTimestamp());
            transaction.set(enrollmentRef, enrollmentData);
            logger.debug("Service TX: Enrollment subcollection record created for student {} (Email: {}) in classroom {}.", student.getUserId(), studentEmail, classroomId);
            return classroom;
        }).get();
        logger.info("Service: Student (Email: {}) successfully added by teacher to classroom {} (ID: {}). New student count: {}",
                studentEmail, finalClassroomState.getName(), finalClassroomState.getClassroomId(), finalClassroomState.getStudentCount());
        return convertToDTO(classroomRepository.findById(finalClassroomState.getClassroomId()).orElse(finalClassroomState));
    }

    public ClassroomDTO removeStudentFromClassroom(String teacherId, String classroomId, String studentIdToRemove)
            throws ExecutionException, InterruptedException {
        logger.debug("Service: TeacherId: {} removing studentId: {} from classroomId: {}", teacherId, studentIdToRemove, classroomId);
        Classroom finalClassroomState = db.runTransaction(transaction -> {
            DocumentReference classroomRef = db.collection(CLASSROOMS_COLLECTION).document(classroomId);
            DocumentSnapshot classroomSnap = transaction.get(classroomRef).get();
            if (!classroomSnap.exists()) {
                logger.warn("Service TX: Classroom with ID: {} not found for student removal operation.", classroomId);
                throw new ResourceNotFoundException("Classroom", "ID", classroomId);
            }
            Classroom classroom = Objects.requireNonNull(classroomSnap.toObject(Classroom.class),
                    "Classroom data could not be mapped for ID: " + classroomId);

            if (!Objects.equals(classroom.getTeacherId(), teacherId)) {
                logger.warn("Service TX: TeacherId: {} is not owner of classroomId: {}. Actual owner: {}", teacherId, classroomId, classroom.getTeacherId());
                throw new BadRequestException("User " + teacherId + " is not owner of classroom " + classroomId);
            }

            DocumentReference studentRef = db.collection(USERS_COLLECTION).document(studentIdToRemove);
            DocumentSnapshot studentSnap = transaction.get(studentRef).get();
            if (!studentSnap.exists()) {
                logger.warn("Service TX: Student with ID: {} to remove not found.", studentIdToRemove);
                throw new UserNotFoundException("Student to remove not found with ID: " + studentIdToRemove);
            }
            User student = Objects.requireNonNull(studentSnap.toObject(User.class),
                    "Student data could not be mapped for ID to remove: " + studentIdToRemove);
            // Refined Log (Original line: 318, now around here)
            if (!student.getStudentOfClassrooms().contains(classroomId)) {
                logger.info("Service TX: Student {} (ID: {}) for removal was not enrolled in classroom {} (ID: {}). No removal changes made.",
                        student.getDisplayName(), studentIdToRemove, classroom.getName(), classroomId);
                return classroom;
            }

            student.getStudentOfClassrooms().remove(classroomId);
            transaction.set(studentRef, student);
            logger.debug("Service TX: Student {} (ID: {}) removed from classroom {} list in User object.", student.getDisplayName(), studentIdToRemove, classroomId);

            classroom.setStudentCount(Math.max(0, classroom.getStudentCount() == null ? 0 : classroom.getStudentCount() - 1));
            transaction.set(classroomRef, classroom);
            logger.debug("Service TX: Classroom {} (ID: {}) student count decremented to {} after removing student.", classroom.getName(), classroomId, classroom.getStudentCount());

            DocumentReference enrollmentRef = classroomRef.collection(ENROLLED_STUDENTS_SUBCOLLECTION).document(studentIdToRemove);
            transaction.delete(enrollmentRef);
            logger.debug("Service TX: Enrollment subcollection record deleted for student {} in classroom {}.", studentIdToRemove, classroomId);
            return classroom;
        }).get();
        logger.info("Service: Student (ID: {}) successfully removed from classroom '{}' (ID: {}) by teacher. New student count: {}.",
                studentIdToRemove, finalClassroomState.getName(), finalClassroomState.getClassroomId(), finalClassroomState.getStudentCount());
        return convertToDTO(classroomRepository.findById(finalClassroomState.getClassroomId()).orElse(finalClassroomState));
    }

    public AssignedGameDTO assignGameToClassroom(String teacherId, String classroomId, AssignGameRequestDTO assignRequest)
            throws ExecutionException, InterruptedException {
        logger.debug("Service: TeacherId: {} assigning game (LibID: {}) to classroomId: {} with due date: {}, maxAttempts: {}",
                teacherId, assignRequest.getLibraryGameId(), classroomId, assignRequest.getDueDate(), assignRequest.getMaxAttemptsAllowed());
        AssignedGame savedAssignment = db.runTransaction(transaction -> {
            DocumentReference classroomRef = db.collection(CLASSROOMS_COLLECTION).document(classroomId);
            DocumentSnapshot classroomSnap = transaction.get(classroomRef).get();
            if (!classroomSnap.exists()) {
                logger.warn("Service TX: Classroom with ID: {} not found for game assignment.", classroomId);
                throw new ResourceNotFoundException("Classroom", "ID", classroomId);
            }
            Classroom classroom = Objects.requireNonNull(classroomSnap.toObject(Classroom.class),
                    "Classroom data could not be mapped for ID: " + classroomId);

            if (!Objects.equals(classroom.getTeacherId(), teacherId)) {
                logger.warn("Service TX: TeacherId: {} is not owner of classroomId: {}. Actual owner: {}", teacherId, classroomId, classroom.getTeacherId());
                throw new BadRequestException("User " + teacherId + " is not owner of classroom " + classroomId);
            }

            Game libraryGame = gameRepository.findById(assignRequest.getLibraryGameId())
                    .orElseThrow(() -> {
                        // Refined Log (Original line: 371, now around here)
                        logger.warn("Service TX: Library game (ID: {}) for assignment to classroom {} was not found in GameRepository.",
                                assignRequest.getLibraryGameId(), classroomId);
                        return new ResourceNotFoundException("Game", "Library ID", assignRequest.getLibraryGameId());
                    });

            DocumentReference newAssignedGameRef = classroomRef.collection(ASSIGNED_GAMES_SUBCOLLECTION).document();
            AssignedGame.AssignedGameBuilder builder = AssignedGame.builder()
                    .assignedGameId(newAssignedGameRef.getId())
                    .libraryGameId(libraryGame.getLibraryGameId()).classroomId(classroomId)
                    .gameTitle(libraryGame.getTitle()).gameDescription(libraryGame.getDescription())
                    .gameUrlOrIdentifier(libraryGame.getGameUrlOrIdentifier())
                    .maxXpAwarded(libraryGame.getMaxXpAwarded())
                    .totalPointsPossible(libraryGame.getTotalPointsPossible())
                    .dueDate(assignRequest.getDueDate())
                    .dateAssigned(null); // @ServerTimestamp

            // Use maxAttemptsAllowed from DTO
            builder.maxAttemptsAllowed(assignRequest.getMaxAttemptsAllowed());

            AssignedGame newAssignment = builder.build();
            transaction.set(newAssignedGameRef, newAssignment);
            logger.debug("Service TX: New game (LibID: {}) assigned as ID: {} to classroomId: {}. MaxAttempts set to: {}",
                    libraryGame.getLibraryGameId(), newAssignment.getAssignedGameId(), classroomId, newAssignment.getMaxAttemptsAllowed());

            classroom.setActivityCount(classroom.getActivityCount() == null ? 1 : classroom.getActivityCount() + 1);
            transaction.set(classroomRef, classroom);
            logger.debug("Service TX: Classroom {} (ID: {}) activity count incremented to {} after game assignment.", classroom.getName(), classroomId, classroom.getActivityCount());
            return newAssignment;
        }).get();
        logger.info("Service: Game '{}' (LibID: {}) assigned as new ID: {} to classroomId: {} by teacherId: {}. MaxAttempts: {}.",
                savedAssignment.getGameTitle(), savedAssignment.getLibraryGameId(), savedAssignment.getAssignedGameId(),
                classroomId, teacherId, savedAssignment.getMaxAttemptsAllowed());
        return convertToAssignedGameDTO(assignedGameRepository.findById(classroomId, savedAssignment.getAssignedGameId())
                .orElse(savedAssignment));
    }

    public void removeGameFromClassroom(String teacherId, String classroomId, String assignedGameId)
            throws ExecutionException, InterruptedException {
        logger.debug("Service: TeacherId: {} removing assignedGameId: {} from classroomId: {}", teacherId, assignedGameId, classroomId);
        db.runTransaction(transaction -> {
            DocumentReference classroomRef = db.collection(CLASSROOMS_COLLECTION).document(classroomId);
            DocumentSnapshot classroomSnap = transaction.get(classroomRef).get();
            if (!classroomSnap.exists()) {
                logger.warn("Service TX: Classroom with ID: {} not found for game removal.", classroomId);
                throw new ResourceNotFoundException("Classroom", "ID", classroomId);
            }
            Classroom classroom = Objects.requireNonNull(classroomSnap.toObject(Classroom.class),
                    "Classroom data could not be mapped for ID: " + classroomId);

            if (!Objects.equals(classroom.getTeacherId(), teacherId)) {
                logger.warn("Service TX: TeacherId: {} is not owner of classroomId: {}. Actual owner: {}", teacherId, classroomId, classroom.getTeacherId());
                throw new BadRequestException("User " + teacherId + " is not owner of classroom " + classroomId);
            }
            DocumentReference assignedGameRef = classroomRef.collection(ASSIGNED_GAMES_SUBCOLLECTION).document(assignedGameId);
            DocumentSnapshot assignedGameSnap = transaction.get(assignedGameRef).get();
            if (!assignedGameSnap.exists()) {
                // Refined Log (Original line: 426, now around here)
                logger.warn("Service TX: Assigned game (ID: {}) for removal from classroom {} was not found in subcollection.",
                        assignedGameId, classroomId);
                throw new ResourceNotFoundException("Assigned Game", "ID", assignedGameId + " in classroom " + classroomId);
            }
            transaction.delete(assignedGameRef);
            logger.debug("Service TX: Assigned game ID: {} deleted from classroomId: {} subcollection.", assignedGameId, classroomId);

            classroom.setActivityCount(Math.max(0, classroom.getActivityCount() == null ? 0 : classroom.getActivityCount() - 1));
            transaction.set(classroomRef, classroom);
            logger.debug("Service TX: Classroom {} (ID: {}) activity count decremented to {} after game removal.", classroom.getName(), classroomId, classroom.getActivityCount());
            return null;
        }).get();
        logger.info("Service: Assigned game (ID: {}) successfully unassigned from classroom (ID: {}) by teacher (ID: {}).",
                assignedGameId, classroomId, teacherId);
    }

    public List<AssignedGameDTO> getAssignedGamesForClassroom(String classroomId)
            throws ExecutionException, InterruptedException {
        logger.debug("Service: Fetching assigned games for classroomId: {}", classroomId);
        classroomRepository.findById(classroomId)
                .orElseThrow(() -> {
                    logger.warn("Service: Classroom not found with ID: {} when fetching its assigned games.", classroomId);
                    return new ResourceNotFoundException("Classroom", "ID", classroomId);
                });
        return assignedGameRepository.findAllByClassroomId(classroomId).stream()
                .map(this::convertToAssignedGameDTO)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getEnrolledStudents(String classroomId, String teacherIdVerifying)
            throws ExecutionException, InterruptedException {
        logger.debug("Service: TeacherId: {} viewing enrolled students for classroomId: {}", teacherIdVerifying, classroomId);
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> {
                    logger.warn("Service: Classroom not found with ID: {} when teacher {} viewing students.", classroomId, teacherIdVerifying);
                    return new ResourceNotFoundException("Classroom", "ID", classroomId);
                });
        if(!Objects.equals(classroom.getTeacherId(), teacherIdVerifying)){
            logger.warn("Service: TeacherId: {} is not authorized to view students for classroomId: {}. Actual owner: {}",
                    teacherIdVerifying, classroomId, classroom.getTeacherId());
            throw new BadRequestException("User " + teacherIdVerifying + " is not authorized to view students for this classroom.");
        }

        CollectionReference enrolledStudentsRef = db.collection(CLASSROOMS_COLLECTION).document(classroomId)
                .collection(ENROLLED_STUDENTS_SUBCOLLECTION);
        ApiFuture<QuerySnapshot> future = enrolledStudentsRef.get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<UserDTO> students = new ArrayList<>();
        if (documents.isEmpty()) {
            logger.debug("Service: No students found in {} subcollection for classroomId: {}", ENROLLED_STUDENTS_SUBCOLLECTION, classroomId);
        }
        for (QueryDocumentSnapshot doc : documents) {
            String studentId = doc.getId();
            logger.trace("Service: Fetching user details for enrolled studentId: {} from subcollection of classroomId: {}", studentId, classroomId);
            userRepository.findById(studentId).ifPresent(user -> students.add(convertToUserDTO(user)));
        }
        return students;
    }

    public boolean isTeacherOwnerOfClassroom(String teacherId, String classroomId) throws ExecutionException, InterruptedException {
        logger.trace("Service: Verifying ownership: teacherId: {} for classroomId: {}", teacherId, classroomId);
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> {
                    logger.warn("Service ownership check: Classroom not found with ID: {} during ownership verification.", classroomId); // Added context
                    return new ResourceNotFoundException("Classroom", "id", classroomId);
                });
        boolean isOwner = classroom.getTeacherId().equals(teacherId);
        logger.trace("Service ownership check result for teacherId: {}, classroomId: {}: isOwner={}", teacherId, classroomId, isOwner);
        return isOwner;
    }

    public boolean isStudentEnrolled(String studentId, String classroomId) throws ExecutionException, InterruptedException {
        logger.trace("Service: Verifying enrollment: studentId: {} in classroomId: {}", studentId, classroomId);
        User student = userRepository.findById(studentId).orElse(null);
        if (student == null) {
            logger.warn("Service enrollment check: Student not found with ID: {} during enrollment verification in classroom {}.", studentId, classroomId); // Added context
            return false;
        }
        boolean isEnrolled = student.getStudentOfClassrooms() != null && student.getStudentOfClassrooms().contains(classroomId);
        logger.trace("Service enrollment check result for studentId: {}, classroomId: {}: isEnrolled={}", studentId, classroomId, isEnrolled);
        return isEnrolled;
    }


    private String generateUniqueClassroomCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private ClassroomDTO convertToDTO(Classroom classroom) {
        if (classroom == null) return null;
        ClassroomDTO dto = new ClassroomDTO();
        dto.setClassroomId(classroom.getClassroomId());
        dto.setName(classroom.getName());
        dto.setTeacherId(classroom.getTeacherId());
        dto.setTeacherName(classroom.getTeacherName());
        dto.setUniqueCode(classroom.getUniqueCode());
        dto.setDescription(classroom.getDescription());
        dto.setIconUrl(classroom.getIconUrl());
        dto.setCreatedAt(classroom.getCreatedAt());
        dto.setUpdatedAt(classroom.getUpdatedAt());
        dto.setStudentCount(classroom.getStudentCount());
        dto.setActivityCount(classroom.getActivityCount());
        return dto;
    }

    private UserDTO convertToUserDTO(User user) {
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

    private AssignedGameDTO convertToAssignedGameDTO(AssignedGame assignedGame) {
        if (assignedGame == null) return null;
        AssignedGameDTO dto = new AssignedGameDTO();
        dto.setAssignedGameId(assignedGame.getAssignedGameId());
        dto.setLibraryGameId(assignedGame.getLibraryGameId());
        dto.setGameTitle(assignedGame.getGameTitle());
        dto.setMaxAttemptsAllowed(assignedGame.getMaxAttemptsAllowed());
        dto.setDateAssigned(assignedGame.getDateAssigned());
        dto.setDueDate(assignedGame.getDueDate());
        dto.setGameDescription(assignedGame.getGameDescription());
        dto.setGameUrlOrIdentifier(assignedGame.getGameUrlOrIdentifier());
        dto.setMaxXpAwarded(assignedGame.getMaxXpAwarded());
        dto.setTotalPointsPossible(assignedGame.getTotalPointsPossible());
        return dto;
    }
}