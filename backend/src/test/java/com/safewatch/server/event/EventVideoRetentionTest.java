package com.safewatch.server.event;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 영상 보관·파기 규칙 (노션 9,10주차). DB 없이 엔티티만으로 확인한다. */
class EventVideoRetentionTest {

    private Event newEvent() {
        return new Event(UUID.randomUUID(), UUID.randomUUID(), OffsetDateTime.now(),
                "drunk_suspect", 88, 37.0, 127.0, "12가 3456", "https://x/clip.mp4", "https://x/thumb.jpg");
    }

    @Test
    void 무시하면_영상이_즉시_파기된다() {
        Event e = newEvent();
        e.changeStatus(Event.STATUS_REVIEWED);
        assertNull(e.getVideoUrl());
        assertNull(e.getThumbUrl());
        assertNotNull(e.getVideoDeletedAt());
        assertNull(e.getVideoExpiresAt());
    }

    @Test
    void 신고하면_영상이_남고_2주_뒤_파기_예정이다() {
        Event e = newEvent();
        e.changeStatus(Event.STATUS_REPORTED);
        assertNotNull(e.getVideoUrl());
        assertNull(e.getVideoDeletedAt());
        assertEquals(e.getReceivedAt().plusDays(14), e.getVideoExpiresAt());
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
