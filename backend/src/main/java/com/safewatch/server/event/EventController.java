package com.safewatch.server.event;

import com.safewatch.server.device.Device;
import com.safewatch.server.device.DeviceRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 위험 이벤트 API.
 *
 * 경로에 /api 를 직접 쓰지 않는다. WebConfig 가 모든 @RestController 에 자동으로 붙인다.
 * 따라서 실제 주소는 /api/events 다.
 *
 * 밖으로 나가는 값은 항상 EventResponse 로 옮겨 담아 내보낸다. 엔티티를 그대로 내보내면
 * DB 에 있는 값이 전부 그대로 노출된다.
 */
@RestController
@RequestMapping("/events")
public class EventController {

    /** 클립 최대 크기. 15초 클립이 카메라 4Mbps 기준 7~8MB 라 넉넉히 잡았다. */
    private static final long MAX_CLIP_BYTES = 50L * 1024 * 1024;

    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final ClipStorage clipStorage;

    public EventController(EventRepository eventRepository, DeviceRepository deviceRepository,
                           ClipStorage clipStorage) {
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.clipStorage = clipStorage;
    }

    /**
     * 목록 조회. since 를 주면 그 시각 이후에 수신한 것만 돌려준다(앱 증분 동기화용).
     */
    @GetMapping
    public List<EventResponse> getEvents(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since) {

        List<Event> events = (since == null)
                ? eventRepository.findAllByOrderByReceivedAtDesc()
                : eventRepository.findByReceivedAtAfterOrderByReceivedAtDesc(since);

        return events.stream().map(this::toResponse).toList();
    }

    /** 상세 조회. */
    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable UUID id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * 이벤트 처리 상태를 바꾼다. 앱에서 신고/무시 버튼을 눌렀을 때 호출한다.
     *
     * PATCH 를 쓰는 이유는 이벤트 전체를 갈아끼우는 게 아니라 status 한 칸만 고치기 때문이다.
     * 감지 결과(위험도·위치·시각)는 장치가 보낸 사실이라 바꿀 수 없다.
     */
    @PatchMapping("/{id}")
    public EventResponse updateStatus(@PathVariable UUID id,
                                      @Valid @RequestBody EventStatusUpdateRequest request) {
        Event event = findOrThrow(id);
        String purged = event.changeStatus(request.status());
        Event saved = eventRepository.save(event);
        if (purged != null) {
            clipStorage.delete(purged);   // 무시 → 영상 파일도 즉시 파기
        }
        return toResponse(saved);
    }

    /**
     * 장치가 감지한 위험 이벤트를 등록한다. 호출자는 앱이 아니라 라즈베리파이다.
     *
     * 본문은 AI 이벤트 JSON 그대로다(EventCreateRequest 참고).
     *
     * 같은 장치가 같은 event_id 를 다시 보내면 새로 만들지 않고 기존 것을 200 으로 돌려준다.
     * 차량은 통신이 자주 끊겨 장치가 같은 이벤트를 재전송할 수 있는데,
     * 이때 409 를 주면 장치가 실패로 보고 계속 재시도한다. 중복 전송이 안전하도록 이렇게 처리한다.
     */
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventCreateRequest request) {
        UUID deviceId = deviceIdOf(request.deviceId());

        Optional<Event> existing = eventRepository.findByDeviceIdAndEventKey(deviceId, request.eventId());
        if (existing.isPresent()) {
            return ResponseEntity.ok(toResponse(existing.get()));
        }

        Event saved = eventRepository.save(request.toEntity(deviceId));

        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(saved.getId()).toUri())
                .body(toResponse(saved));
    }

    /**
     * 장치가 이벤트의 영상 클립(mp4)을 올린다. 본문은 mp4 파일 그대로.
     *
     *   PUT /api/events/clip?device_id=safewatch-dev-001&event_id=evt_20261119_143022_001
     *   Content-Type: video/mp4
     *
     * 장치는 이벤트 JSON 을 먼저 보내고, 클립은 정차 후 WiFi 로 나중에 보낼 수 있다(AI 문서 C안).
     * 그래서 (device_id, event_id) 로 먼저 와 있던 이벤트를 찾아 붙인다.
     *
     * 응답 — 장치(HW 큐)는 4xx 면 포기, 5xx·네트워크 오류면 나중에 다시 보낸다.
     *   200 저장됨 (같은 클립 재전송도 200 — 같은 파일 이름에 덮어씀)
     *   400 등록되지 않은 장치 / 404 이벤트 JSON 이 아직 없음 / 410 이미 파기됐거나 사용자가 무시함
     *   411 Content-Length 없음 / 413 50MB 초과 / 415 mp4 가 아님 / 5xx S3 오류
     *
     * 장치는 S3 에 직접 올리지 않고 서버를 거친다 — 장치에 AWS 열쇠를 넣지 않고, HW 가 요청 한 번이면 되게.
     * ponytail: 영상이 서버를 지나간다. 장치가 많아져 서버 대역폭이 모자라면 S3 임시 업로드 주소(presigned PUT)로 바꾼다.
     */
    @PutMapping(value = "/clip", consumes = "video/mp4")
    public EventResponse uploadClip(@RequestParam("device_id") String deviceCode,
                                    @RequestParam("event_id") String eventKey,
                                    HttpServletRequest request) throws IOException {
        long length = request.getContentLengthLong();
        if (length <= 0) {
            throw new ResponseStatusException(HttpStatus.LENGTH_REQUIRED, "Content-Length 가 필요합니다");
        }
        if (length > MAX_CLIP_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "클립은 50MB 이하여야 합니다: " + length + " bytes");
        }

        UUID deviceId = deviceIdOf(deviceCode);
        Event event = eventRepository.findByDeviceIdAndEventKey(deviceId, eventKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "이벤트가 없습니다. 이벤트 JSON 을 먼저 보내야 합니다: " + eventKey));
        if (!event.canAttachVideo()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE, "이미 파기됐거나 사용자가 무시한 이벤트라 영상을 받지 않습니다: " + eventKey);
        }

        String key = "clips/" + event.getId() + ".mp4";
        clipStorage.put(key, request.getInputStream(), length);
        event.attachVideo(key);
        return toResponse(eventRepository.save(event));
    }

    /** AI 의 device_id 는 장치 이름(예: safewatch-dev-001)이라 서버의 장치 번호로 바꾼다. */
    private UUID deviceIdOf(String deviceCode) {
        return deviceRepository.findByCode(deviceCode)
                .map(Device::getId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "등록되지 않은 장치입니다: " + deviceCode));
    }

    private EventResponse toResponse(Event event) {
        return EventResponse.from(event, clipStorage.playUrl(event.getVideoKey()));
    }

    private Event findOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다: " + id));
    }
}
