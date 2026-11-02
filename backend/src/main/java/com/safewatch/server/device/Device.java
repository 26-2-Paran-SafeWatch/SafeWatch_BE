package com.safewatch.server.device;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 차량에 달린 SafeWatch 장치(라즈베리파이).
 *
 * 지금은 이벤트가 들어올 때 "어느 장치인가"를 찾는 데만 쓴다. 장치 등록 API(/devices)는 아직 없어서
 * 장치는 DB 에 직접 넣는다(seed.sql).
 */
@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Device {

    @Id
    private UUID id;

    private String name;

    /** 장치가 스스로 부르는 이름. AI 이벤트 JSON 의 device_id 가 이 값이다 (예: safewatch-dev-001). */
    private String code;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "registered_at")
    private OffsetDateTime registeredAt;
}
