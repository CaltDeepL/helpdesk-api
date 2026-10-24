package com.caltdeepl.helpdesk.auth.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.caltdeepl.helpdesk.auth.service.AuthService;
import com.caltdeepl.helpdesk.auth.service.IssuedToken;
import com.caltdeepl.helpdesk.user.domain.Role;
import com.caltdeepl.helpdesk.user.service.UserView;

/** ユーザー登録とログイン。認証なしで呼べる（SecurityConfig）。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request.email(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.email(), request.password()));
    }

    record RegisterRequest(
            @NotBlank(message = "メールアドレスは必須です")
                    @Email(message = "メールアドレスの形式が正しくありません")
                    @Size(max = 255, message = "メールアドレスは 255 文字以内にしてください")
                    String email,
            @NotBlank(message = "パスワードは必須です") @Size(min = 8, max = 72, message = "パスワードは 8〜72 文字にしてください")
                    String password,
            @NotBlank(message = "表示名は必須です") @Size(max = 100, message = "表示名は 100 文字以内にしてください") String displayName) {}

    record LoginRequest(
            @NotBlank(message = "メールアドレスは必須です") String email, @NotBlank(message = "パスワードは必須です") String password) {}

    record UserResponse(UUID id, String email, String displayName, Role role) {

        static UserResponse from(UserView user) {
            return new UserResponse(user.id(), user.email(), user.displayName(), user.role());
        }
    }

    record TokenResponse(String accessToken, String tokenType, long expiresIn) {

        static TokenResponse from(IssuedToken token) {
            return new TokenResponse(token.accessToken(), token.tokenType(), token.expiresIn());
        }
    }
}
