package com.safewatch.server.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 보관 기간(Event.VIDEO_RETENTION, 2주)이 지난 영상을 자동 파기한다. 신고 여부와 관계없이 전부.
 *
 * 매시 정각 + 서버가 켜질 때 한 번 돈다. 하루 한 번(새벽)이 아닌 이유는,
 * 요금 때문에 EC2 를 자주 끄는데 꺼져 있는 시각에 작업이 잡혀 있으면 계속 건너뛰기 때문이다.
 * 켜질 때 한 번 더 돌리면 꺼져 있던 동안 기간이 지난 것도 바로 정리된다.
 */
@Component
public class VideoRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(VideoRetentionJob.class);

    private final EventRepository eventRepository;

    public VideoRetentionJob(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredVideos() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(Event.VIDEO_RETENTION);
        // ponytail: 대상을 전부 읽어와 하나씩 지운다. 하루 수천 건 넘게 쌓이면 UPDATE 한 번으로 바꾼다.
        List<Event> expired = eventRepository.findByVideoUrlIsNotNullAndReceivedAtBefore(cutoff);
        expired.forEach(Event::purgeVideo);   // 트랜잭션이 끝날 때 바뀐 값이 DB 에 저장된다
        if (!expired.isEmpty()) {
            log.info("보관 기간이 지난 영상 {}건 파기", expired.size());
        }
    }
}
