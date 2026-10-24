package com.caltdeepl.helpdesk.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caltdeepl.helpdesk.common.error.AppException;
import com.caltdeepl.helpdesk.common.error.ErrorCode;
import com.caltdeepl.helpdesk.user.domain.Role;
import com.caltdeepl.helpdesk.user.domain.User;
import com.caltdeepl.helpdesk.user.repository.UserRepository;
import com.caltdeepl.helpdesk.user.service.UserView;

/** ユーザー登録とログイン（FR-1.1 / FR-1.2 / FR-1.4）。 */
@Service
public class AuthService {

    /** bcrypt は 72 バイトを超える入力を扱えない。 */
    static final int MAX_PASSWORD_BYTES = 72;

    private static final String INVALID_CREDENTIALS_MESSAGE = "メールアドレスまたはパスワードが正しくありません";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** 未登録のメールでも照合を 1 回行い、応答時間からユーザーの有無を推測させないためのハッシュ。 */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-equalization");
    }

    /** 登録する。ロールは常に AGENT（ロールの付け替えは Task #3）。 */
    @Transactional
    public UserView register(String email, String password, String displayName) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "パスワードは " + MAX_PASSWORD_BYTES + " バイト以内にしてください");
        }
        String normalizedEmail = normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw emailAlreadyRegistered();
        }

        User user = User.register(normalizedEmail, passwordEncoder.encode(password), displayName.strip(), Role.AGENT);
        try {
            return UserView.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            // existsByEmail と INSERT の間に同じメールが登録された場合
            throw emailAlreadyRegistered();
        }
    }

    /** 資格情報を照合してトークンを発行する。失敗理由（未登録・パスワード誤り）は区別して返さない。 */
    @Transactional(readOnly = true)
    public IssuedToken login(String email, String password) {
        Optional<User> user = userRepository.findByEmail(normalize(email));
        String hash = user.map(User::passwordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(password, hash);

        return user.filter(found -> matches)
                .map(found -> jwtService.issue(found.id(), found.role()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static AppException emailAlreadyRegistered() {
        return new AppException(ErrorCode.CONFLICT, "このメールアドレスは既に登録されています");
    }
}
