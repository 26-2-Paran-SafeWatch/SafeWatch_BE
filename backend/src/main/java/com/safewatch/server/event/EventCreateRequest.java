package com.safewatch.server.event;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 장치(라즈베리파이)가 POST /api/events 로 보내는 감지 결과.
 *
 * 검증 규칙은 DB 의 CHECK 제약(schema.sql)과 같은 값으로 맞춰 두었다.
 * 여기서 먼저 걸러야 잘못된 요청이 400 으로 돌아간다. 그냥 두면 DB 까지 내려가서 500 이 난다.
 *
 * ※ AI 파트(설만수)의 실제 모델 출력 JSON 이 확정되면 이 필드 구성을 그에 맞춰 수정한다.
 *   현재는 우리 스키마 기준이다.
 */
public record EventCreateRequest(

        @NotNull(message = "id 는 장치가 생성한 UUID 여야 합니다")
        UUID id,

        @NotNull(message = "deviceId 는 필수입니다")
        UUID deviceId,

        @NotNull(message = "detectedAt 은 필수입니다")
        OffsetDateTime detectedAt,

        @NotBlank(message = "riskType 은 필수입니다")
        @Pattern(regexp = "drunk_suspect|lane_departure|sudden_accel|swerving",
                 message = "riskType 은 drunk_suspect, lane_departure, sudden_accel, swerving 중 하나여야 합니다")
        String riskType,

        @NotNull(message = "riskScore 는 필수입니다")
        @Min(value = 0, message = "riskScore 는 0 이상이어야 합니다")
        @Max(value = 100, message = "riskScore 는 100 이하여야 합니다")
        Integer riskScore,

        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
        Double latitude,

        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
        Double longitude,

        String plateNumber,
        String videoUrl,
        String thumbUrl
) {
    public Event toEntity() {
        return new Event(id, deviceId, detectedAt, riskType, riskScore,
                latitude, longitude, plateNumber, videoUrl, thumbUrl);
    }
}
