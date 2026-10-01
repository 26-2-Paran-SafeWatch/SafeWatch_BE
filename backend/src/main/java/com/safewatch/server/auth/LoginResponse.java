package com.safewatch.server.auth;

/**
 * 로그인 성공 응답.
 *
 * 토큰만 주지 않고 사용자 정보도 함께 담는다. 앱이 로그인 직후 이름을 화면에 띄우려고
 * 요청을 한 번 더 보내지 않아도 되기 때문이다.
 *
 * user 는 UserResponse 라서 비밀번호 해시가 들어갈 자리가 없다.
 */
public record LoginResponse(

        /** 앞으로 요청 헤더에 담아 보낼 토큰 */
        String accessToken,

        /** 헤더에 쓸 형식. "Authorization: Bearer <토큰>" 으로 보낸다. */
        String tokenType,

        /** 몇 초 뒤에 만료되는지 */
        long expiresIn,

        UserResponse user
) {

    public static LoginResponse of(String accessToken, long expiresIn, User user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, UserResponse.from(user));
    }
}
