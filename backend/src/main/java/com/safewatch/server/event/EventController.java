package com.safewatch.server.event;

import com.safewatch.server.device.Device;
import com.safewatch.server.device.DeviceRepository;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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

    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;

    public EventController(EventRepository eventRepository, DeviceRepository deviceRepository) {
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
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

        return EventResponse.from(events);
    }

    /** 상세 조회. */
    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable UUID id) {
        return EventResponse.from(findOrThrow(id));
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
        event.changeStatus(request.status());
        return EventResponse.from(eventRepository.save(event));
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
        // AI 의 device_id 는 장치 이름(예: safewatch-dev-001)이라 서버의 장치 번호로 바꾼다
        UUID deviceId = deviceRepository.findByCode(request.deviceId())
                .map(Device::getId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "등록되지 않은 장치입니다: " + request.deviceId()));

        Optional<Event> existing = eventRepository.findByDeviceIdAndEventKey(deviceId, request.eventId());
        if (existing.isPresent()) {
            return ResponseEntity.ok(EventResponse.from(existing.get()));
        }

        Event saved = eventRepository.save(request.toEntity(deviceId));

        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(saved.getId()).toUri())
                .body(EventResponse.from(saved));
    }

    private Event findOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다: " + id));
    }
}
