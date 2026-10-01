package com.safewatch.server.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/signup 요청 본문.
 *
 * 비밀번호는 여기서만 평문으로 존재한다. 컨트롤러가 곧바로 해시로 바꾸고,
 * User 엔티티에는 해시된 값만 들어간다. 평문은 DB 에 닿지 않는다.
 */
public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 올바르지 않습니다")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다")
        String password,

        @Size(max = 50, message = "이름은 50자 이하여야 합니다")
        String displayName
) {
}
