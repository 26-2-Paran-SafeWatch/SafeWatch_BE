package com.safewatch.server.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 토큰 검사가 진짜만 통과시키는지. 가짜·위조·만료는 전부 예외여야 한다(→ 401). */
class JwtProviderTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123";   // 32바이트 이상
    private final JwtProvider jwt = new JwtProvider(SECRET, 60);
    private final User user = new User("a@test.com", "hash", "테스트");

    @Test
    void 진짜_토큰은_사용자_id_를_돌려준다() {
        assertEquals(user.getId(), jwt.parseUserId(jwt.createToken(user)));
    }

    @Test
    void 다른_열쇠로_서명한_토큰은_거절() {
        String forged = new JwtProvider("other-secret-other-secret-other-secret-99", 60).createToken(user);
        assertThrows(JwtException.class, () -> jwt.parseUserId(forged));
    }

    @Test
    void 내용을_다른_사람으로_바꿔치기한_토큰은_거절() {
        String[] mine = jwt.createToken(user).split("\\.");
        String[] other = jwt.createToken(new User("b@test.com", "hash", "남")).split("\\.");
        String tampered = mine[0] + "." + other[1] + "." + mine[2];   // 남의 내용 + 내 서명
        assertThrows(JwtException.class, () -> jwt.parseUserId(tampered));
    }

    @Test
    void 만료된_토큰은_거절() {
        String expired = new JwtProvider(SECRET, -1).createToken(user);   // 1분 전에 이미 만료
        assertThrows(JwtException.class, () -> jwt.parseUserId(expired));
    }
}
