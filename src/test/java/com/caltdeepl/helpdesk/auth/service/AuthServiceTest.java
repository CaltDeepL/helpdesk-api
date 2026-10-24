package com.caltdeepl.helpdesk.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.caltdeepl.helpdesk.common.error.AppException;
import com.caltdeepl.helpdesk.common.error.ErrorCode;
import com.caltdeepl.helpdesk.user.domain.Role;
import com.caltdeepl.helpdesk.user.domain.User;
import com.caltdeepl.helpdesk.user.repository.UserRepository;
import com.caltdeepl.helpdesk.user.service.UserView;

class AuthServiceTest {

    private static final String PASSWORD = "correct-horse-battery";

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private UserRepository userRepository;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("登録ではメールを正規化し、パスワードを bcrypt で保存し、ロールは AGENT にする")
    void registerStoresHashedPasswordAsAgent() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserView view = authService.register("  Alice@Example.COM ", PASSWORD, "Alice");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.passwordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.passwordHash())).isTrue();
        assertThat(user.role()).isEqualTo(Role.AGENT);
        assertThat(view.email()).isEqualTo("alice@example.com");
        assertThat(view.role()).isEqualTo(Role.AGENT);
    }

    @Test
    @DisplayName("登録済みのメールアドレスは 409")
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("alice@example.com", PASSWORD, "Alice"))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONFLICT));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("同時登録で一意制約に違反した場合も 409")
    void registerMapsUniqueViolationToConflict() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.register("alice@example.com", PASSWORD, "Alice"))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("bcrypt が扱えない 72 バイト超のパスワードは 400")
    void registerRejectsPasswordOver72Bytes() {
        String longPassword = "あ".repeat(25); // 75 バイト

        assertThatThrownBy(() -> authService.register("alice@example.com", longPassword, "Alice"))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("正しい資格情報ならトークンを発行する")
    void loginIssuesToken() {
        User user = User.register("alice@example.com", passwordEncoder.encode(PASSWORD), "Alice", Role.AGENT);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        IssuedToken token = new IssuedToken("token", 3600);
        when(jwtService.issue(user.id(), Role.AGENT)).thenReturn(token);

        assertThat(authService.login(" Alice@example.com", PASSWORD)).isEqualTo(token);
    }

    @Test
    @DisplayName("パスワード誤りと未登録のメールは、同じ 401 を返す（ユーザーの有無を漏らさない）")
    void loginFailuresAreIndistinguishable() {
        User user = User.register("alice@example.com", passwordEncoder.encode(PASSWORD), "Alice", Role.AGENT);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        AppException wrongPassword = catchThrowableOfType(
                AppException.class, () -> authService.login("alice@example.com", "wrong-password"));
        AppException unknownEmail =
                catchThrowableOfType(AppException.class, () -> authService.login("nobody@example.com", PASSWORD));

        assertThat(wrongPassword.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(unknownEmail.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
        verify(jwtService, never()).issue(any(), any());
    }
}
