package com.caltdeepl.helpdesk.user.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.caltdeepl.helpdesk.user.domain.Role;
import com.caltdeepl.helpdesk.user.service.UserService;
import com.caltdeepl.helpdesk.user.service.UserView;

/** 認証済みユーザー自身の情報。 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MeResponse.from(userService.findById(UUID.fromString(jwt.getSubject())));
    }

    record MeResponse(UUID id, String email, String displayName, Role role) {

        static MeResponse from(UserView user) {
            return new MeResponse(user.id(), user.email(), user.displayName(), user.role());
        }
    }
}
