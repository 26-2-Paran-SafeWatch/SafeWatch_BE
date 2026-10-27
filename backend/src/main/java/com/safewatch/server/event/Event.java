package com.safewatch.server.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Duration;
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

    /** 영상을 파기한 시각. null 이면 아직 보관 중(또는 처음부터 영상이 없음). */
    @Column(name = "video_deleted_at")
    private OffsetDateTime videoDeletedAt;

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
        // 신고하지 않기로 했으면 증거 영상을 둘 이유가 없다 → 즉시 파기.
        // 앱이 아니라 여기서 하는 이유: 어떤 경로로 상태를 바꾸든 규칙이 빠지지 않게.
        if (STATUS_REVIEWED.equals(newStatus)) {
            purgeVideo();
        }
    }

    /**
     * 영상(과 썸네일) 주소를 지우고 파기 시각을 남긴다. 이미 파기됐거나 영상이 없으면 아무것도 안 한다.
     *
     * ponytail: DB 의 주소만 지운다. 실제 파일은 저장소(S3 등)에 남아 있으므로, 저장소가 정해지면
     * 여기서 파일 삭제까지 해야 진짜 파기가 된다. (노션 9,10주차 "영상 보관·파기 정책")
     */
    public void purgeVideo() {
        if (videoUrl == null && thumbUrl == null) {
            return;
        }
        this.videoUrl = null;
        this.thumbUrl = null;
        this.videoDeletedAt = OffsetDateTime.now();
    }

    /** 자동 파기 예정 시각. 이미 파기됐거나 영상이 없으면 null. */
    public OffsetDateTime getVideoExpiresAt() {
        return (videoUrl == null) ? null : receivedAt.plus(VIDEO_RETENTION);
    }

    /**
     * 영상 보관 기간. 신고 여부와 관계없이 이 기간이 지나면 자동 파기한다. 이용약관에 명시할 값.
     *
     * 기준은 detectedAt(촬영 시각)이 아니라 receivedAt(서버가 받은 시각)이다.
     * 라즈베리파이는 시계 배터리가 없어 부팅 직후 시각이 틀릴 수 있고(5,6주차),
     * 장치 시계가 과거로 찍히면 받자마자 파기되는 사고가 난다. 보통은 두 시각 차이가 몇 초다.
     */
    public static final Duration VIDEO_RETENTION = Duration.ofDays(14);

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
