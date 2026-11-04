package com.example.knavi.organization.application;

import com.example.knavi.shared.web.ApiErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * デモ環境（ADR-0015）の設定。デモは全員共通のパスワードで使うため、パスワードの変更・ユーザーの更新のように
 * 他の見る人を締め出せる操作を止める。ローカル・本番の想定では無効（{@code knavi.demo.enabled=false}）。
 */
@Component
public class DemoMode {

    private final boolean enabled;

    DemoMode(@Value("${knavi.demo.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    /** デモ環境なら、操作を 403 で断る。{@code action} は「パスワードを変更」のように、「できません」の前に入る言葉 */
    public void rejectIfEnabled(String action) {
        if (enabled) {
            throw ApiErrors.forbidden("デモ環境では" + action + "できません（データは毎日初期化されます）。");
        }
    }
}
