package com.safewatch.server.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 장치(라즈베리파이)가 POST /api/events 로 보내는 감지 결과.
 *
 * AI 파트 이벤트 스키마(SafeWatch_AI docs/event-schema.md v0.7, 5.1 확정안)를 <b>그대로</b> 받는다.
 * AI 의 src/event/builder.py 가 만든 JSON 을 변환 없이 보내면 된다. 그래서 이 요청만 필드 이름이
 * snake_case 다(AI 문서 4장 규약). 앱으로 나가는 응답(EventResponse)은 기존대로 camelCase.
 *
 * 검증 규칙은 DB 의 CHECK 제약(schema.sql)과 같은 값으로 맞춰 두었다.
 * 여기서 먼저 걸러야 잘못된 요청이 400 으로 돌아간다. 그냥 두면 DB 까지 내려가서 500 이 난다.
 *
 * 지금 받지 않는 것: 영상 파일 자체. 전송 방식(AI 문서 1장 A/B/C안)이 정해지면 그때 붙인다.
 */
public record EventCreateRequest(

        @NotBlank(message = "event_id 는 필수입니다")
        @JsonProperty("event_id")
        String eventId,

        @NotBlank(message = "device_id 는 필수입니다")
        @JsonProperty("device_id")
        String deviceId,

        @NotNull(message = "timestamp 는 필수입니다 (ISO 8601, 타임존 포함)")
        OffsetDateTime timestamp,

        @NotNull(message = "risk 는 필수입니다")
        @Valid
        Risk risk,

        /** 판단 근거 수치. 쓰인 것만 오므로 어떤 키가 올지 고정하지 않는다. */
        Map<String, Object> indicators,

        @JsonProperty("target_vehicle")
        Map<String, Object> targetVehicle,

        /** GPS 를 못 잡으면 통째로 빠진다(AI 문서 3.5). */
        @Valid
        Location location,

        /** 클립 정보. 클립을 아직 못 만들면 빠진다. */
        Map<String, Object> clip,

        Map<String, Object> meta
) {

    public record Risk(
            @NotNull(message = "risk.score 는 필수입니다")
            @Min(value = 0, message = "risk.score 는 0 이상이어야 합니다")
            @Max(value = 100, message = "risk.score 는 100 이하여야 합니다")
            Integer score,

            @NotBlank(message = "risk.level 은 필수입니다")
            @Pattern(regexp = "low|medium|high", message = "risk.level 은 low, medium, high 중 하나여야 합니다")
            String level,

            @NotEmpty(message = "risk.types 는 하나 이상이어야 합니다")
            List<@Pattern(regexp = "lane_departure|weaving|swerving|drifting|speed_irregular",
                    message = "risk.types 는 lane_departure, weaving, swerving, drifting, speed_irregular 중에서만 쓸 수 있습니다")
                    String> types
    ) {
    }

    public record Location(
            @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
            Double latitude,

            @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
            Double longitude,

            @JsonProperty("speed_kmh")
            Double speedKmh,

            Double heading
    ) {
    }

    /** deviceId 는 요청의 device_id(장치 이름)로 서버가 찾은 장치 번호. */
    public Event toEntity(UUID deviceId) {
        Location loc = (location != null) ? location : new Location(null, null, null, null);
        return new Event(deviceId, eventId, timestamp,
                risk.score(), risk.level(), risk.types(),
                indicators, targetVehicle,
                loc.latitude(), loc.longitude(), loc.speedKmh(), loc.heading(),
                clip, meta);
    }
}
