package com.caltdeepl.helpdesk.user.domain;

/** ユーザーのロール。権限差は Task #3 で付ける。DB の CHECK 制約（users_role_valid）と値を揃える。 */
public enum Role {
    AGENT,
    MANAGER
}
