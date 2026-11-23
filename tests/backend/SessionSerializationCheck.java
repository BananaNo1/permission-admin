import com.leis.permission.SessionUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

class SessionSerializationCheck {
    public static void main(String[] args) throws Exception {
        String hash = "regression-test-password-hash";
        var principal = new SessionUser(11L, "test-user", hash, 2L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, "test-password", List.of());
        verifyRoundTrip(authentication, hash);

        authentication.eraseCredentials();
        if (authentication.getCredentials() != null || principal.getPassword() != null) {
            throw new AssertionError("Spring Security did not erase credentials");
        }
        verifyRoundTrip(authentication, hash);
        System.out.println("PASS session identity survives serialization without password hash");
    }

    private static void verifyRoundTrip(UsernamePasswordAuthenticationToken authentication, String hash)
            throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(authentication);
        }
        if (bytes.toString(StandardCharsets.ISO_8859_1).contains(hash)) {
            throw new AssertionError("Password hash was serialized");
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = (UsernamePasswordAuthenticationToken) input.readObject();
            var principal = (SessionUser) restored.getPrincipal();
            if (!restored.isAuthenticated() || !Long.valueOf(11L).equals(principal.id())
                    || !"test-user".equals(principal.username()) || !Long.valueOf(2L).equals(principal.authVersion())
                    || principal.getPassword() != null) {
                throw new AssertionError("Session identity was not restored safely");
            }
        }
    }
}
