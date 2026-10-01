package com.safewatch.server.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)   // JPA 전용 기본 생성자
public class Event {

    /** 장치(라즈베리파이)가 생성한 UUID 를 그대로 쓴다. 서버가 매기지 않는다. */
    @Id
    private UUID id;

    @Column(name = "device_id")
    private UUID deviceId;

    /** AI 가 감지한 시각(장치 시계) */
    @Column(name = "detected_at")
    private OffsetDateTime detectedAt;

    /** 서버가 받은 시각. 통신이 끊겼다 몰아서 올라오면 detectedAt 과 크게 차이날 수 있다. */
    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "risk_type")
    private String riskType;

    @Column(name = "risk_score")
    private Integer riskScore;

    private Double latitude;
    private Double longitude;

    @Column(name = "plate_number")
    private String plateNumber;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "thumb_url")
    private String thumbUrl;

    private String status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /**
     * 장치가 보낸 감지 결과로 새 이벤트를 만든다.
     *
     * receivedAt / status / createdAt 은 장치가 보내는 값이 아니라 서버가 정하는 값이므로
     * 여기서 채운다. 장치 시계는 틀릴 수 있으니 수신 시각만은 서버 시계를 쓴다.
     */
    public Event(UUID id, UUID deviceId, OffsetDateTime detectedAt,
                 String riskType, Integer riskScore,
                 Double latitude, Double longitude,
                 String plateNumber, String videoUrl, String thumbUrl) {
        OffsetDateTime now = OffsetDateTime.now();
        this.id = id;
        this.deviceId = deviceId;
        this.detectedAt = detectedAt;
        this.receivedAt = now;
        this.riskType = riskType;
        this.riskScore = riskScore;
        this.latitude = latitude;
        this.longitude = longitude;
        this.plateNumber = plateNumber;
        this.videoUrl = videoUrl;
        this.thumbUrl = thumbUrl;
        this.status = STATUS_NEW;
        this.createdAt = now;
    }

    /**
     * 사용자가 이벤트를 확인하고 처리한 결과를 반영한다.
     *
     * 세터를 열어두지 않고 이 메서드만 두는 이유는, 이벤트에서 바깥이 바꿔도 되는 값은
     * status 하나뿐이기 때문이다. 감지 결과(위험도·위치·시각)는 장치가 보낸 사실이므로
     * 나중에 고쳐지면 안 된다.
     */
    public void changeStatus(String newStatus) {
        if (!ALLOWED_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("허용되지 않는 status: " + newStatus);
        }
        this.status = newStatus;
    }

    /** 아직 사용자가 확인하지 않음 */
    public static final String STATUS_NEW = "new";
    /** 사용자가 확인하고 넘김(무시) */
    public static final String STATUS_REVIEWED = "reviewed";
    /** 사용자가 신고함 */
    public static final String STATUS_REPORTED = "reported";

    /** schema.sql 의 CHECK 제약과 같은 값이어야 한다. */
    public static final java.util.Set<String> ALLOWED_STATUSES =
            java.util.Set.of(STATUS_NEW, STATUS_REVIEWED, STATUS_REPORTED);
}
