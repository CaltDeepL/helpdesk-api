package com.caltdeepl.helpdesk.user.service;

import java.util.UUID;

import com.caltdeepl.helpdesk.user.domain.Role;
import com.caltdeepl.helpdesk.user.domain.User;

/** controller に渡すユーザー情報。パスワードのハッシュは含めない。 */
public record UserView(UUID id, String email, String displayName, Role role) {

    public static UserView from(User user) {
        return new UserView(user.id(), user.email(), user.displayName(), user.role());
    }
}
