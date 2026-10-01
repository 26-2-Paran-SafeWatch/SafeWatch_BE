package com.safewatch.server.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/auth/login 요청 본문.
 *
 * 가입 때와 달리 비밀번호에 길이 검사를 걸지 않는다.
 * 규칙이 바뀌기 전에 가입한 사람도 로그인은 할 수 있어야 하고,
 * 어차피 맞는지 틀린지는 저장된 해시와 비교해서 판단한다.
 */
public record LoginRequest(

        @NotBlank(message = "이메일은 필수입니다")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다")
        String password
) {
}
