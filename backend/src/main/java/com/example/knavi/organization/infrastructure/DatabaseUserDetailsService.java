package com.example.knavi.organization.infrastructure;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** users テーブルのメールアドレスとパスワードハッシュで認証する（MVP のローカル認証） */
@Service
class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    DatabaseUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmailIgnoreCase(email)
                .map(KnaviUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
    }
}
