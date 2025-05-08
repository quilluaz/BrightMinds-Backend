package citu.jisaz.brightminds_backend.controller;

import citu.jisaz.brightminds_backend.dto.CreateUserRequestDTO;
import citu.jisaz.brightminds_backend.dto.UpdateUserRequestDTO;
import citu.jisaz.brightminds_backend.dto.UserDTO;
import citu.jisaz.brightminds_backend.model.User;
import citu.jisaz.brightminds_backend.service.UserService;
import com.google.firebase.auth.FirebaseAuthException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService; // Ensure userService is private final and injected

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> registerUser(@Valid @RequestBody CreateUserRequestDTO createUserRequest)
            throws FirebaseAuthException, ExecutionException, InterruptedException {
        logger.info("Request to register user: {}", createUserRequest.getEmail());
        UserDTO newUser = userService.createUser(createUserRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        String userId = principal.getUserId();
        logger.info("Request to fetch current user details for UID: {}", userId);
        UserDTO user = userService.getUserById(userId); // userService.getUserById, not this.getUserById
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{userId}")
    // A user can get their own details.
    // A teacher can get details of any user IF that user is a student.
    @PreAuthorize("isAuthenticated() and " +
            "(#userId == authentication.principal.userId or " +
            "(hasRole('ROLE_TEACHER') and @userService.isUserStudent(#userId)))")
    public ResponseEntity<UserDTO> getUserById(@PathVariable String userId, Authentication authentication)
            throws ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal(); // This is the authenticated user making the request
        logger.info("User {} (Role: {}) requesting details for user ID: {}.",
                principal.getUserId(), principal.getRole(), userId);
        UserDTO user = userService.getUserById(userId); // Fetch the details of the target userId
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("isAuthenticated() and #userId == authentication.principal.userId")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequestDTO updateUserRequest,
            Authentication authentication)
            throws FirebaseAuthException, ExecutionException, InterruptedException {
        User principal = (User) authentication.getPrincipal();
        logger.info("Request by user {} to update own profile (ID: {}).", principal.getUserId(), userId);
        UserDTO updatedUser = userService.updateUser(userId, updateUserRequest);
        return ResponseEntity.ok(updatedUser);
    }
}