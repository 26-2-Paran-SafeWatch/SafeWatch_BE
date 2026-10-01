package com.safewatch.server.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사용자 정보를 밖으로 내보낼 때 쓰는 모양.
 *
 * ★ passwordHash 필드가 없다. 이것이 이 클래스의 존재 이유다.
 *
 * User 엔티티를 그대로 응답에 쓰면 users 테이블에 있는 값이 전부 나가므로
 * 회원가입 응답 한 번에 비밀번호 해시가 함께 나간다. 아무도 그렇게 하라고 시키지 않아도
 * 자동으로 그렇게 된다. 그래서 내보낼 것만 골라 담는 객체를 따로 둔다.
 */
public record UserResponse(

        UUID id,
        String email,
        String displayName,
        OffsetDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt()
        );
    }
}
