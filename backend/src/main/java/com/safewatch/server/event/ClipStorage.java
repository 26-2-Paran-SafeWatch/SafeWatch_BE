package com.safewatch.server.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.InputStream;
import java.time.Duration;

/**
 * 영상 클립 보관함(S3). 버킷은 비공개라서 앱은 파일을 직접 못 열고,
 * 서버가 만들어 준 1시간짜리 임시 주소로만 재생한다 — 영상에 번호판·얼굴이 찍혀 있어서.
 *
 * 접근 권한은 코드에 넣지 않는다. AWS 기본 방식대로 찾는다:
 * EC2 에서는 서버에 붙인 IAM 역할(SafeWatchServerRole, 이 버킷의 올리기·읽기·지우기만),
 * 로컬에서는 AWS_PROFILE 환경변수의 키.
 */
@Component
public class ClipStorage {

    private static final Logger log = LoggerFactory.getLogger(ClipStorage.class);

    /** 앱이 받는 재생 주소의 유효 시간. 화면을 다시 열면 새로 받는다. */
    private static final Duration PLAY_URL_TTL = Duration.ofHours(1);

    private final String bucket;
    private final S3Client s3;
    private final S3Presigner presigner;

    public ClipStorage(@Value("${safewatch.clips.bucket}") String bucket,
                       @Value("${safewatch.clips.region}") String region) {
        this.bucket = bucket;
        this.s3 = S3Client.builder().region(Region.of(region)).build();
        this.presigner = S3Presigner.builder().region(Region.of(region)).build();
    }

    /** 요청 본문을 메모리에 다 담지 않고 S3 로 흘려보낸다. 실패하면 예외 → 장치는 5xx 를 받고 나중에 다시 보낸다. */
    public void put(String key, InputStream body, long length) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("video/mp4").build(),
                RequestBody.fromInputStream(body, length));
    }

    /**
     * 파기. 실패해도 예외를 던지지 않는다 — DB 에서는 이미 지웠고,
     * 버킷 자체 규칙(15일 뒤 자동 삭제)이 남은 파일을 결국 지운다.
     */
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            log.warn("S3 영상 삭제 실패 (15일 자동 삭제 규칙이 처리): {} — {}", key, e.getMessage());
        }
    }

    /**
     * 앱이 재생할 임시 주소. 영상이 없으면 null.
     * 서명만 하고 네트워크는 쓰지 않아서 목록 전체에 붙여도 가볍다.
     */
    public String playUrl(String key) {
        if (key == null) {
            return null;
        }
        try {
            return presigner.presignGetObject(b -> b
                    .signatureDuration(PLAY_URL_TTL)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build()))
                    .url().toString();
        } catch (RuntimeException e) {
            // 로컬에서 AWS 키 없이 띄운 경우 등. 영상만 안 보이고 나머지 화면은 살린다.
            log.warn("재생 주소를 만들 수 없음: {} — {}", key, e.getMessage());
            return null;
        }
    }
}
