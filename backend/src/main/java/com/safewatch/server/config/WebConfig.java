package com.safewatch.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 모든 REST 엔드포인트에 /api 접두사를 자동으로 붙인다.
 *
 * 컨트롤러에는 접두사 없이 자원 경로만 쓴다(예: @RequestMapping("/events")).
 * 실제 주소는 /api/events 가 된다. 새 컨트롤러를 추가할 때 접두사를 빠뜨릴 수 없으므로
 * /api/events 와 /devices 처럼 규칙이 갈라지는 일을 원천적으로 막는다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forAnnotation(RestController.class));
    }
}
