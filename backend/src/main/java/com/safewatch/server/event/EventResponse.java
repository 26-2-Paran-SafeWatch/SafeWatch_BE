package com.safewatch.server.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 앱에 돌려줄 위험 이벤트의 모양.
 *
 * Event 엔티티를 그대로 내보내지 않고 이 객체로 옮겨 담아 내보낸다.
 * 엔티티는 "DB 에 어떻게 저장할지"를 정하는 것이고, 이 객체는 "밖에 무엇을 보여줄지"를 정하는 것이라
 * 서로 목적이 다르다. 한 클래스가 둘을 겸하면 DB 에 있는 값이 전부 그대로 밖으로 나간다.
 *
 * 요청 쪽은 이미 EventCreateRequest / EventStatusUpdateRequest 로 분리돼 있다.
 * 이 클래스는 그 대칭을 응답 쪽에 맞춘 것이다.
 */
public record EventResponse(

        UUID id,
        UUID deviceId,

        /** AI 가 감지한 시각(장치 시계) */
        OffsetDateTime detectedAt,
        /** 서버가 받은 시각 */
        OffsetDateTime receivedAt,

        String riskType,
        Integer riskScore,

        Double latitude,
        Double longitude,

        String plateNumber,
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
                event.getDetectedAt(),
                event.getReceivedAt(),
                event.getRiskType(),
                event.getRiskScore(),
                event.getLatitude(),
                event.getLongitude(),
                event.getPlateNumber(),
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
