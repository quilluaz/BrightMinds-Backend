package citu.jisaz.brightminds_backend.security;

import citu.jisaz.brightminds_backend.exception.FirebaseAuthenticationException;
import citu.jisaz.brightminds_backend.model.User;
import citu.jisaz.brightminds_backend.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class FirebaseIdTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseIdTokenAuthenticationFilter.class);
    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    public FirebaseIdTokenAuthenticationFilter(FirebaseAuth firebaseAuth, UserRepository userRepository) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String idTokenString = extractTokenFromRequest(request);

        if (!StringUtils.hasText(idTokenString)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idTokenString, true);
            String uid = decodedToken.getUid();

            User appUser = userRepository.findById(uid)
                    .orElseThrow(() -> {
                        logger.warn("Firebase token verified for UID {}, but no user in application DB.", uid);
                        return new FirebaseAuthenticationException("User account not found in application records. UID: " + uid, true);
                    });

            List<GrantedAuthority> authorities = new ArrayList<>();
            if (StringUtils.hasText(appUser.getRole())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().toUpperCase()));
            } else {
                logger.warn("User {} (UID: {}) has no role defined. Application may behave unexpectedly.", appUser.getDisplayName(), uid);
            }

            FirebaseAuthenticationToken authentication = new FirebaseAuthenticationToken(appUser, decodedToken, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            logger.debug("User {} (UID: {}) authenticated with authorities: {}", appUser.getDisplayName(), uid, authorities);

        } catch (FirebaseAuthException e) {
            handleAuthException(response, "Invalid or revoked Firebase ID Token: " + e.getMessage(), e, HttpStatus.UNAUTHORIZED);
            return;
        } catch (FirebaseAuthenticationException e) {
            HttpStatus status = e.isUserNotFoundInDb() ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
            handleAuthException(response, e.getMessage(), e, status);
            return;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Repository error during token verification for user: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            handleAuthException(response, "Error processing authentication due to internal server issue.", e, HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void handleAuthException(HttpServletResponse response, String message, Exception e, HttpStatus status) throws IOException {
        logger.warn("Authentication failure: {}. Exception: {}. Status: {}", message, e.getClass().getSimpleName(), status.value());
        SecurityContextHolder.clearContext();
        response.setStatus(status.value());
        response.setContentType("application/json");
        // Ensure your global exception handler provides a consistent error response structure
        response.getWriter().write(String.format("{\"error\": \"%s\", \"message\": \"%s\"}", status.getReasonPhrase(), escapeJson(message)));
    }

    private String escapeJson(String Sinput) {
        if (Sinput == null) return "";
        return Sinput.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}