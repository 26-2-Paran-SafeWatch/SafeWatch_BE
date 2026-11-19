package com.safewatch.server.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 위험운전 이벤트. 형식은 AI 파트 이벤트 스키마(SafeWatch_AI docs/event-schema.md v0.7, 5.1 확정안)를 따른다.
 *
 * AI 가 정하는 값(위험 종류·등급·근거 수치)은 서버가 해석하지 않고 그대로 저장한다.
 * 기준을 바꾸는 쪽이 AI 이므로, 서버가 의미를 아는 순간 AI 가 바뀔 때마다 서버도 고쳐야 한다.
 */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)   // JPA 전용 기본 생성자
public class Event {

    /** 서버가 매기는 번호. AI 의 event_id 는 장치끼리 겹칠 수 있어서 eventKey 로 따로 둔다. */
    @Id
    private UUID id;

    @Column(name = "device_id")
    private UUID deviceId;

    /** AI 의 event_id (evt_YYYYMMDD_HHMMSS_NNN). 같은 장치 안에서만 고유 → (deviceId, eventKey) 로 중복 판별. */
    @Column(name = "event_key")
    private String eventKey;

    /** AI 가 감지한 시각(장치 시계) */
    @Column(name = "detected_at")
    private OffsetDateTime detectedAt;

    /** 서버가 받은 시각. 통신이 끊겼다 몰아서 올라오면 detectedAt 과 크게 차이날 수 있다. */
    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    /** 음주 의심 점수 0~100 */
    @Column(name = "risk_score")
    private Integer riskScore;

    /** low / medium / high. 점수 구간은 AI 가 정한다 — 앱은 점수 구간을 몰라도 된다. */
    @Column(name = "risk_level")
    private String riskLevel;

    /** 감지된 단서들. 보통 2개 이상 (AI 는 2개 이상 지표가 동시에 충족될 때만 이벤트를 만든다). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "risk_types")
    private List<String> riskTypes;

    /** 판단 근거 수치. 판단에 쓰인 것만 들어온다(예: 차선 인식이 불안하면 차선 수치는 빠짐). */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> indicators;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_vehicle")
    private Map<String, Object> targetVehicle;

    private Double latitude;
    private Double longitude;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    private Double heading;

    /** 영상 클립 정보(길이·해상도·블러 여부). 파일 자체는 아니다. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> clip;

    /** 영상 클립의 S3 파일 이름(예: clips/{id}.mp4). 장치가 클립을 올리면 채워진다. 주소가 아니라 이름만 둔다 — 버킷이 비공개라 재생 주소는 볼 때마다 ClipStorage 가 만든다. */
    @Column(name = "video_key")
    private String videoKey;

    @Column(name = "thumb_url")
    private String thumbUrl;

    /** 영상을 파기한 시각. null 이면 아직 보관 중(또는 처음부터 영상이 없음). */
    @Column(name = "video_deleted_at")
    private OffsetDateTime videoDeletedAt;

    /** 모델·규칙 버전 */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> meta;

    private String status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /**
     * 장치가 보낸 감지 결과로 새 이벤트를 만든다.
     *
     * id / receivedAt / status / createdAt 은 장치가 보내는 값이 아니라 서버가 정하는 값이므로
     * 여기서 채운다. 장치 시계는 틀릴 수 있으니 수신 시각만은 서버 시계를 쓴다.
     */
    public Event(UUID deviceId, String eventKey, OffsetDateTime detectedAt,
                 Integer riskScore, String riskLevel, List<String> riskTypes,
                 Map<String, Object> indicators, Map<String, Object> targetVehicle,
                 Double latitude, Double longitude, Double speedKmh, Double heading,
                 Map<String, Object> clip, Map<String, Object> meta) {
        OffsetDateTime now = OffsetDateTime.now();
        this.id = UUID.randomUUID();
        this.deviceId = deviceId;
        this.eventKey = eventKey;
        this.detectedAt = detectedAt;
        this.receivedAt = now;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.riskTypes = riskTypes;
        this.indicators = indicators;
        this.targetVehicle = targetVehicle;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh;
        this.heading = heading;
        this.clip = clip;
        this.meta = meta;
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
    /**
     * @return 이번에 파기한 영상의 S3 파일 이름(없으면 null). 호출한 쪽이 S3 에서도 지워야 한다.
     */
    public String changeStatus(String newStatus) {
        if (!ALLOWED_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("허용되지 않는 status: " + newStatus);
        }
        this.status = newStatus;
        // 신고하지 않기로 했으면 증거 영상을 둘 이유가 없다 → 즉시 파기.
        // 앱이 아니라 여기서 하는 이유: 어떤 경로로 상태를 바꾸든 규칙이 빠지지 않게.
        return STATUS_REVIEWED.equals(newStatus) ? purgeVideo() : null;
    }

    /**
     * 지금 영상을 받아도 되는가. 장치는 정차 후 WiFi 로 클립을 몇 시간 늦게 보낼 수 있어서,
     * 그 사이 이미 파기됐거나(무시·2주 경과) 사용자가 무시를 눌렀으면 받지 않는다 — 받자마자 지울 영상이다.
     */
    public boolean canAttachVideo() {
        return videoDeletedAt == null
                && !STATUS_REVIEWED.equals(status)
                && receivedAt.plus(VIDEO_RETENTION).isAfter(OffsetDateTime.now());
    }

    /** 장치가 올린 클립을 이벤트에 붙인다. 같은 클립을 다시 올리면 같은 이름이라 그대로 덮어쓴다. */
    public void attachVideo(String key) {
        if (!canAttachVideo()) {
            throw new IllegalStateException("영상을 받을 수 없는 이벤트: " + id);
        }
        this.videoKey = key;
    }

    /**
     * 영상(과 썸네일) 이름을 지우고 파기 시각을 남긴다. 이미 파기됐거나 영상이 없으면 아무것도 안 한다.
     * DB 에서만 지우므로, 돌려받은 이름으로 호출한 쪽이 S3 파일도 지운다(ClipStorage.delete).
     *
     * @return 지운 영상의 S3 파일 이름. 지운 게 없으면 null
     */
    public String purgeVideo() {
        if (videoKey == null && thumbUrl == null) {
            return null;
        }
        String purged = videoKey;
        this.videoKey = null;
        this.thumbUrl = null;
        this.videoDeletedAt = OffsetDateTime.now();
        return purged;
    }

    /** 자동 파기 예정 시각. 이미 파기됐거나 영상이 없으면 null. */
    public OffsetDateTime getVideoExpiresAt() {
        return (videoKey == null) ? null : receivedAt.plus(VIDEO_RETENTION);
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
