package com.caltdeepl.helpdesk.user.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/**
 * API の利用者（サポート担当者・管理者）。
 *
 * <p>主キーは UUID v7（V1__init.sql の方針）。created_at / updated_at は DB の DEFAULT とトリガが設定するため、ここでは扱わない。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected User() {
        // JPA 用
    }

    private User(String email, String passwordHash, String displayName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    /**
     * 新しいユーザーを作る。
     *
     * @param email 正規化済み（前後の空白を除き小文字）のメールアドレス
     * @param passwordHash {@code PasswordEncoder} でハッシュ化した値。平文を渡さない
     */
    public static User register(String email, String passwordHash, String displayName, Role role) {
        return new User(email, passwordHash, displayName, role);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public Role role() {
        return role;
    }
}
