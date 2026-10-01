package com.safewatch.server.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 잘못된 요청이 그대로 DB 까지 내려가 500 이 되는 것을 막고, 원인을 알려주는 400 으로 바꾼다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 요청 본문 검증 실패(필수값 누락, 허용되지 않는 riskType, 범위를 벗어난 riskScore 등) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다");
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * DB 제약조건 위반. 가장 흔한 경우는 등록되지 않은 deviceId 를 보낸 것(외래키 위반).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "DB 제약조건을 위반했습니다. 등록되지 않은 deviceId 인지 확인하세요.");
        problem.setTitle("Constraint Violation");
        return problem;
    }

    /**
     * 컨트롤러가 직접 던진 오류(없는 이벤트 404, 중복 이메일 409 등).
     *
     * 이걸 여기서 잡지 않으면 예외가 톰캣까지 올라가고, 톰캣이 오류 본문을 만들려고
     * 내부 요청을 한 번 더 보낸다. 그 경로로 만들어진 본문에는 우리가 적은 사유가 빠진다.
     * (Spring Boot 가 기본적으로 오류 메시지를 감추기 때문이다.)
     *
     *   감추는 이유: 메시지에 DB 구조나 내부 경로가 딸려 나가면 공격에 힌트가 된다.
     *   그래서 전체를 여는 대신, 우리가 직접 적은 문구만 이렇게 골라서 내려준다.
     *
     * 여기서 잡으면 응답을 바로 만들어 돌려주므로 사유가 그대로 담긴다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException e) {
        String detail = (e.getReason() != null) ? e.getReason() : "요청을 처리할 수 없습니다";

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getStatusCode(), detail);
        problem.setTitle(HttpStatus.valueOf(e.getStatusCode().value()).getReasonPhrase());
        return problem;
    }
}
