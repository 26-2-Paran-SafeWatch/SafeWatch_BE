package com.safewatch.server.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 설정.
 *
 * Spring Security 는 넣기만 해도 기본값으로 "모든 요청을 막고 로그인 창을 띄운다".
 * 아무 설정 없이 두면 지금 잘 도는 이벤트 API 가 전부 401 이 된다.
 * 이 파일은 그 기본값을 우리 상황에 맞게 다시 정하는 곳이다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 비밀번호 해시 도구.
     *
     * BCrypt 는 일부러 느리게 만든 해시다. 느린 게 장점인 이유는, 비밀번호를 훔친 공격자가
     * 후보를 하나씩 대입해보는 속도도 같이 느려지기 때문이다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 어떤 주소에서 온 브라우저 요청을 받아줄지 정한다.
     *
     * 지금은 개발용이라 내 컴퓨터(localhost)와 파일을 직접 연 경우(null)만 연다.
     * 배포할 때는 실제 도메인만 남기고 좁혀야 한다. 아무나 열어두면
     * 남의 사이트가 우리 API 를 자기 화면에서 그대로 부를 수 있다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "null"                      // 브라우저로 html 파일을 직접 열었을 때
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 다른 주소에서 온 브라우저 요청을 허용한다(CORS).
                //
                // 브라우저는 화면이 떠 있는 주소와 API 주소가 다르면 요청을 막는다.
                // frontend/index.html 은 서버가 아니라 밖에서 열리므로 주소가 다르다.
                // 이 설정이 없으면 화면에서 아무것도 못 부른다.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF 는 브라우저 폼 전송을 노린 공격을 막는 장치다.
                // 우리는 폼이 아니라 앱·장치가 토큰으로 호출하는 REST API 라 해당이 없다.
                .csrf(csrf -> csrf.disable())

                // 서버가 로그인 상태를 기억하지 않는다(무상태).
                // 나중에 토큰(JWT)만으로 판단할 것이므로 세션을 만들 이유가 없다.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 기본으로 딸려오는 로그인 화면과 브라우저 인증창을 끈다.
                // 켜두면 401 대신 로그인 페이지가 돌아와 앱이 응답을 해석할 수 없다.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        // 오류 응답을 만들려고 서버가 스스로 보내는 내부 요청은 막지 않는다.
                        //
                        // 404·409 같은 응답은 컨트롤러가 직접 만들지 않는다. 예외를 던지면 톰캣이 받아서
                        // /error 로 한 번 더 요청을 보내고 거기서 본문이 만들어진다.
                        // 이 줄이 없으면 그 내부 요청이 아래 anyRequest().authenticated() 에 막혀
                        // 원래 만들려던 404 대신 본문 없는 403 이 나간다.
                        //
                        // /error 주소 자체를 여는 방법도 있지만 그건 바깥에서도 부를 수 있게 된다.
                        // 내부 요청(DispatcherType.ERROR)만 집어서 여는 쪽이 정확하다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // 회원가입·로그인은 당연히 열려 있어야 한다. 토큰을 받기 전에 부르는 곳이다.
                        .requestMatchers("/api/auth", "/api/auth/**").permitAll()

                        // 이벤트 API 는 지금은 열어둔다.
                        // 등록은 라즈베리파이가 부르는데 장치 인증 방식이 아직 정해지지 않았고,
                        // 조회는 앱이 부르지만 로그인 기능이 아직 없다.
                        // 장치 키 인증과 로그인이 붙으면 이 줄을 좁힌다.
                        .requestMatchers("/api/events", "/api/events/**").permitAll()

                        // 위에 적지 않은 나머지는 전부 인증이 필요하다.
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
