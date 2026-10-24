package com.caltdeepl.helpdesk.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.caltdeepl.helpdesk.user.domain.User;

/** メールアドレスは保存前に小文字へ正規化するため、完全一致で検索する。一意性は users_email_lower_key が保証する。 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
