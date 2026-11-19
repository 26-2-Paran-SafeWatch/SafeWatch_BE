package com.safewatch.server.event;

import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 영상 보관·파기 규칙 (노션 9,10주차). DB 없이 엔티티만으로 확인한다. */
class EventVideoRetentionTest {

    private Event newEvent() {
        Event e = new Event(UUID.randomUUID(), "evt_20261102_143022_001", OffsetDateTime.now(),
                88, "high", List.of("lane_departure", "weaving"), Map.of(), Map.of(),
                37.0, 127.0, 60.0, 90.0, Map.of(), Map.of());
        e.attachVideo("clips/test.mp4");
        ReflectionTestUtils.setField(e, "thumbUrl", "https://x/thumb.jpg");
        return e;
    }

    @Test
    void 무시하면_영상이_즉시_파기된다() {
        Event e = newEvent();
        assertEquals("clips/test.mp4", e.changeStatus(Event.STATUS_REVIEWED));   // S3 에서 지울 이름
        assertNull(e.getVideoKey());
        assertNull(e.getThumbUrl());
        assertNotNull(e.getVideoDeletedAt());
        assertNull(e.getVideoExpiresAt());
    }

    @Test
    void 신고하면_영상이_남고_2주_뒤_파기_예정이다() {
        Event e = newEvent();
        e.changeStatus(Event.STATUS_REPORTED);
        assertNotNull(e.getVideoKey());
        assertNull(e.getVideoDeletedAt());
        assertEquals(e.getReceivedAt().plusDays(14), e.getVideoExpiresAt());
    }

    @Test
    void 파기했거나_무시한_이벤트는_영상을_받지_않는다() {
        Event purged = newEvent();
        purged.purgeVideo();
        assertFalse(purged.canAttachVideo());
        assertThrows(IllegalStateException.class, () -> purged.attachVideo("clips/late.mp4"));

        Event old = newEvent();   // 서버가 받은 지 15일 지남 → 받자마자 지울 영상
        ReflectionTestUtils.setField(old, "receivedAt", OffsetDateTime.now().minusDays(15));
        assertFalse(old.canAttachVideo());
    }

    @Test
    void 두_번_파기해도_처음_파기_시각이_유지된다() {
        Event e = newEvent();
        e.purgeVideo();
        OffsetDateTime first = e.getVideoDeletedAt();
        e.purgeVideo();
        assertEquals(first, e.getVideoDeletedAt());
    }
}
