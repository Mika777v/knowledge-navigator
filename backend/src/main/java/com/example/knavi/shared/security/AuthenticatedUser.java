package com.example.knavi.shared.security;

import java.util.UUID;

/** 認証済みユーザーのうち、認可の判断に使う属性。セッションの principal が実装する */
public interface AuthenticatedUser {

    UUID id();

    /** 所属部署（未所属なら null） */
    UUID departmentId();

    /** ロール名（EMPLOYEE / MANAGER / CONTENT_EDITOR / SYSTEM_ADMIN） */
    String role();
}
