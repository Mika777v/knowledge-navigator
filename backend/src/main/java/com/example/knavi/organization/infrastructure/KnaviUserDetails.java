package com.example.knavi.organization.infrastructure;

import com.example.knavi.organization.domain.User;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** セッションに保存する認証済みユーザー。最新の属性は /me で DB から読み直す */
public record KnaviUserDetails(UUID id, String email, String passwordHash, String role, boolean active)
        implements UserDetails, Serializable {

    static KnaviUserDetails from(User user) {
        return new KnaviUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().name(),
                user.isActive());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        // パスワードが未設定（SSO 専用など）のユーザーはローカル認証できない
        return active && passwordHash != null;
    }
}
