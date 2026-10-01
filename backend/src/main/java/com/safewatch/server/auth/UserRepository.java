package com.safewatch.server.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * 이미 가입된 이메일인지 확인한다.
     *
     * DB 에도 UNIQUE 제약이 있어 중복이면 어차피 막히지만, 그때는 500 에 가까운 형태로 터진다.
     * 미리 확인해서 "이미 가입된 이메일입니다" 라고 알려주기 위한 메서드다.
     */
    boolean existsByEmail(String email);

    /** 로그인할 때 이메일로 사용자를 찾는다. 없으면 비어 있는 값이 온다. */
    Optional<User> findByEmail(String email);
}
