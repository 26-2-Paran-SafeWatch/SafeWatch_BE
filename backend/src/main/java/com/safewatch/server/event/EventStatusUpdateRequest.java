package com.safewatch.server.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH /api/events/{id} 요청 본문. 앱에서 신고/무시 버튼을 눌렀을 때 보낸다.
 *
 * - reported : 신고함
 * - reviewed : 확인하고 넘김(무시)
 * - new      : 아직 확인 안 함 (되돌리기용)
 *
 * 검증 값은 schema.sql 의 status CHECK 제약과 같게 맞춰 두었다.
 */
public record EventStatusUpdateRequest(

        @NotBlank(message = "status 는 필수입니다")
        @Pattern(regexp = "new|reviewed|reported",
                 message = "status 는 new, reviewed, reported 중 하나여야 합니다")
        String status
) {
}
