package com.safewatch.server.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 로그인에 성공한 사람에게 줄 토큰(JWT)을 만든다.
 *
 * 토큰은 "이 사람은 누구다"를 적어 서버가 서명한 문자열이다. 서버가 로그인 상태를 기억하지 않아도
 * 토큰만 보면 누구인지 알 수 있다. 차량 앱처럼 여러 기기에서 붙는 상황에 맞는 방식이다.
 *
 * 주의: 토큰 안의 내용은 누구나 열어볼 수 있다. 암호화가 아니라 서명이기 때문이다.
 * 서명 덕분에 "고칠 수 없다"는 보장은 되지만 "감춰진다"는 보장은 없다.
 * 그래서 비밀번호처럼 숨겨야 할 값은 토큰에 넣지 않는다.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expiryMinutes;

    public JwtProvider(@Value("${safewatch.jwt.secret}") String secret,
                       @Value("${safewatch.jwt.expiry-minutes}") long expiryMinutes) {
        // HS256 서명은 열쇠가 32바이트 이상이어야 한다. 짧으면 여기서 바로 예외가 난다.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = expiryMinutes;
    }

    /**
     * 사용자에게 줄 토큰을 만든다.
     *
     * subject 에 사용자 id 를 넣는다. 나중에 토큰을 받았을 때 이 값으로 누구인지 찾는다.
     * 이메일도 함께 넣어두면 사용자를 매번 DB 에서 찾지 않아도 되는 경우가 생긴다.
     */
    public String createToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiryMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /** 앱에게 "이 토큰은 몇 초 뒤에 만료된다"고 알려주기 위한 값. */
    public long getExpirySeconds() {
        return expiryMinutes * 60;
    }
}
