package com.safewatch.server.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 앱에 돌려줄 위험 이벤트의 모양.
 *
 * Event 엔티티를 그대로 내보내지 않고 이 객체로 옮겨 담아 내보낸다.
 * 엔티티는 "DB 에 어떻게 저장할지"를 정하는 것이고, 이 객체는 "밖에 무엇을 보여줄지"를 정하는 것이라
 * 서로 목적이 다르다. 한 클래스가 둘을 겸하면 DB 에 있는 값이 전부 그대로 밖으로 나간다.
 * 예: target_vehicle·meta 는 디버깅용이라 저장만 하고 앱에는 보내지 않는다.
 *
 * 요청은 AI 형식(snake_case)이지만 응답은 앱용이라 camelCase 를 유지한다.
 */
public record EventResponse(

        UUID id,
        UUID deviceId,
        /** AI 가 매긴 이벤트 번호 (evt_...) */
        String eventKey,

        /** AI 가 감지한 시각(장치 시계) */
        OffsetDateTime detectedAt,
        /** 서버가 받은 시각 */
        OffsetDateTime receivedAt,

        Integer riskScore,
        /** low / medium / high */
        String riskLevel,
        /** lane_departure / weaving / swerving / drifting / speed_irregular 중 여러 개 */
        List<String> riskTypes,
        /** 판단 근거 수치 (쓰인 것만) */
        Map<String, Object> indicators,

        Double latitude,
        Double longitude,
        Double speedKmh,
        Double heading,

        /** 클립 정보 (길이·해상도·블러 여부) */
        Map<String, Object> clip,
        String videoUrl,
        String thumbUrl,
        /** 자동 파기 예정 시각 (보관 중일 때만, 아니면 null) */
        OffsetDateTime videoExpiresAt,
        /** 영상을 파기한 시각 (파기됐을 때만, 아니면 null) */
        OffsetDateTime videoDeletedAt,

        String status,
        OffsetDateTime createdAt
) {

    /** 엔티티를 응답 객체로 옮겨 담는다. */
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getDeviceId(),
                event.getEventKey(),
                event.getDetectedAt(),
                event.getReceivedAt(),
                event.getRiskScore(),
                event.getRiskLevel(),
                event.getRiskTypes(),
                event.getIndicators(),
                event.getLatitude(),
                event.getLongitude(),
                event.getSpeedKmh(),
                event.getHeading(),
                event.getClip(),
                event.getVideoUrl(),
                event.getThumbUrl(),
                event.getVideoExpiresAt(),
                event.getVideoDeletedAt(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }

    /** 목록용. 여러 건을 한 번에 옮겨 담는다. */
    public static List<EventResponse> from(List<Event> events) {
        return events.stream().map(EventResponse::from).toList();
    }
}
