package com.example.knavi.shared.security;

/**
 * セッションに保存した principal が、今もそのまま使えるかを確かめる（organization モジュールが実装する）。
 * ロール・所属の変更や無効化を、ログイン中のセッションにもすぐ反映するために使う
 */
public interface SessionPrincipalChecker {

    /** ユーザーが有効で、ロールと所属がセッションに保存したときから変わっていなければ true */
    boolean isCurrent(AuthenticatedUser user);
}
