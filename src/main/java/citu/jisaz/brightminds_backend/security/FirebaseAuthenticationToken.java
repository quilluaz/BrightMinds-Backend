package citu.jisaz.brightminds_backend.security;

import com.google.firebase.auth.FirebaseToken;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import citu.jisaz.brightminds_backend.model.User;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

@Getter
public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {

    private final User principalUser;
    private final FirebaseToken firebaseTokenDetails; // Storing the verified token details

    public FirebaseAuthenticationToken(User principalUser,
                                       FirebaseToken firebaseTokenDetails,
                                       Collection<? extends GrantedAuthority> authorities) {
        super(authorities != null ? Collections.unmodifiableCollection(authorities) : Collections.emptyList());

        Objects.requireNonNull(principalUser, "Principal User (appUser) cannot be null for FirebaseAuthenticationToken.");
        Objects.requireNonNull(principalUser.getUserId(), "Principal User's ID (userId) cannot be null for FirebaseAuthenticationToken.");
        Objects.requireNonNull(firebaseTokenDetails, "FirebaseToken details cannot be null for FirebaseAuthenticationToken.");

        this.principalUser = principalUser;
        this.firebaseTokenDetails = firebaseTokenDetails;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this.principalUser;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new IllegalArgumentException(
                    "Cannot set this token to authenticated. It is constructed as authenticated.");
        }
        super.setAuthenticated(false);
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
    }

}