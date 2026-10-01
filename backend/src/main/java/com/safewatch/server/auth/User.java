package com.safewatch.server.auth;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 앱 사용자 계정. users 테이블과 짝을 이룬다.
 *
 * 비밀번호는 절대 평문으로 담지 않는다. 이 클래스가 받는 값은 이미 해시된 문자열이고,
 * 해시하는 일은 AuthController 가 PasswordEncoder 로 처리한다.
 * 엔티티가 해시까지 맡으면 "해시된 값인지 아닌지"를 헷갈릴 여지가 생긴다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA 전용 기본 생성자
public class User {

    @Id
    private UUID id;

    /** 로그인 ID. DB 에 UNIQUE 제약이 걸려 있다. */
    private String email;

    /** BCrypt 로 해시된 비밀번호. 응답으로 절대 내보내지 않는다. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /**
     * 새 계정을 만든다.
     *
     * id 는 서버가 만든다. 이벤트는 장치가 UUID 를 만들어 보내지만, 계정은 서버에서만 생기므로
     * 여기서 생성한다. createdAt 도 서버 시각을 쓴다.
     *
     * @param passwordHash 평문이 아니라 이미 해시된 값
     */
    public User(String email, String passwordHash, String displayName) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.createdAt = OffsetDateTime.now();
    }
}
