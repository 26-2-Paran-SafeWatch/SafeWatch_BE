package com.safewatch.server.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 인증 API.
 *
 * 경로에 /api 를 직접 쓰지 않는다. WebConfig 가 자동으로 붙이므로 실제 주소는 /api/auth 다.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /**
     * 계정이 없을 때도 비밀번호를 비교하는 시늉을 하기 위한 가짜 해시.
     * 왜 필요한지는 login() 의 주석 참고.
     */
    private final String dummyHash;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.dummyHash = passwordEncoder.encode("timing-attack-guard");
    }

    /**
     * 회원가입.
     *
     * 비밀번호를 평문으로 저장하지 않는다. passwordEncoder.encode() 로 BCrypt 해시를 만들어
     * 그 결과만 DB 에 넣는다. 해시는 되돌릴 수 없으므로, DB 가 통째로 유출돼도
     * 비밀번호 자체는 바로 드러나지 않는다.
     *
     * 같은 비밀번호라도 해시 결과는 매번 다르다. BCrypt 가 매번 다른 소금값(salt)을 섞기 때문이다.
     * 그래서 두 사람이 같은 비밀번호를 써도 DB 만 보고는 알 수 없다.
     */
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다");
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),   // ← 평문이 해시로 바뀌는 지점
                request.displayName()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserResponse.from(userRepository.save(user)));
    }

    /**
     * 로그인. 성공하면 토큰을 준다.
     *
     * 비밀번호는 저장된 해시와 비교한다. 해시는 되돌릴 수 없으므로 "저장된 값을 풀어서 맞춰보는" 게
     * 아니라, 들어온 평문을 같은 방식으로 해시해서 결과가 같은지 본다. matches() 가 그 일을 한다.
     *
     * 실패 이유를 구분해서 알려주지 않는 이유:
     *   "없는 이메일입니다" 와 "비밀번호가 틀렸습니다" 를 나눠서 주면,
     *   이메일만 바꿔가며 넣어보고 어떤 계정이 존재하는지 알아낼 수 있다.
     *   조금 불친절해도 둘 다 같은 문구를 준다.
     *
     * 계정이 없어도 해시 비교를 한 번 돌리는 이유:
     *   없을 때 곧장 실패를 돌려주면 응답이 눈에 띄게 빨라진다.
     *   그 시간 차이만으로도 계정 존재 여부가 드러난다. 그래서 가짜 해시로 비슷한 시간을 쓴다.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {

        User user = userRepository.findByEmail(request.email()).orElse(null);

        // 구글 로그인으로 가입한 사람은 비밀번호가 없다(passwordHash == null).
        // 그런 계정은 이 경로로 로그인할 수 없다.
        boolean hasPassword = user != null && user.getPasswordHash() != null;
        String hashToCompare = hasPassword ? user.getPasswordHash() : dummyHash;

        boolean passwordOk = passwordEncoder.matches(request.password(), hashToCompare);

        if (!hasPassword || !passwordOk) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다");
        }

        return LoginResponse.of(
                jwtProvider.createToken(user),
                jwtProvider.getExpirySeconds(),
                user);
    }
}
