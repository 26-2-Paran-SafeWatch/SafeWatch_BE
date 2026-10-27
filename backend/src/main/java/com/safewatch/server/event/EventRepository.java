package com.safewatch.server.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /** 앱 목록 화면용. 최신 수신순. */
    List<Event> findAllByOrderByReceivedAtDesc();

    /** 증분 조회용. 앱이 마지막으로 받은 시각 이후 것만 가져간다. */
    List<Event> findByReceivedAtAfterOrderByReceivedAtDesc(OffsetDateTime since);

    /** 자동 파기 대상. 영상이 남아 있고 보관 기간이 지난 것. */
    List<Event> findByVideoUrlIsNotNullAndReceivedAtBefore(OffsetDateTime cutoff);
}
