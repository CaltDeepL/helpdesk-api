package com.caltdeepl.helpdesk.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.client.RestClient;

import com.caltdeepl.helpdesk.TestcontainersConfiguration;
import com.caltdeepl.helpdesk.auth.service.JwtService;
import com.caltdeepl.helpdesk.common.security.JwtConfig;
import com.caltdeepl.helpdesk.common.security.JwtProperties;
import com.caltdeepl.helpdesk.user.domain.Role;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * Task #2 の完了条件（登録 201・ログイン 200・不正トークン 401）を、実 PostgreSQL と実 HTTP で確かめる。
 *
 * <p>テストごとに一意なメールアドレスを使い、データの後片付けに頼らない。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    @Test
    @DisplayName("登録は 201 を返し、パスワードは返さず、bcrypt のハッシュで保存する（FR-1.1 / FR-1.4）")
    void registerReturns201() {
        String email = uniqueEmail();

        ResponseEntity<String> response = register(email, PASSWORD, "Alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DocumentContext body = JsonPath.parse(response.getBody());
        assertThat(body.read("$.email", String.class)).isEqualTo(email);
        assertThat(body.read("$.displayName", String.class)).isEqualTo("Alice");
        assertThat(body.read("$.role", String.class)).isEqualTo("AGENT");
        assertThat(UUID.fromString(body.read("$.id", String.class))).isNotNull();
        assertThat(response.getBody()).doesNotContain(PASSWORD).doesNotContain("password");

        String storedHash =
                jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(storedHash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("大文字小文字だけが違うメールアドレスの再登録は 409")
    void duplicateEmailReturns409() {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice");

        ResponseEntity<String> response = register(email.toUpperCase(Locale.ROOT), PASSWORD, "Alice");

        assertProblem(response, HttpStatus.CONFLICT, "conflict");
    }

    @Test
    @DisplayName("入力不備の登録は 400 とフィールド単位の errors")
    void invalidRegistrationReturns400() {
        ResponseEntity<String> response = register("not-an-email", "short", "");

        assertProblem(response, HttpStatus.BAD_REQUEST, "validation-failed");
        DocumentContext body = JsonPath.parse(response.getBody());
        assertThat(body.read("$.errors", Map.class)).containsKeys("email", "password", "displayName");
    }

    @Test
    @DisplayName("ログインは 200 と Bearer トークンを返し、そのトークンで /api/me が 200（FR-1.2）")
    void loginReturns200AndTokenWorks() {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice");

        ResponseEntity<String> login = login(email, PASSWORD);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        DocumentContext body = JsonPath.parse(login.getBody());
        assertThat(body.read("$.tokenType", String.class)).isEqualTo("Bearer");
        assertThat(body.read("$.expiresIn", Long.class)).isEqualTo(3600L);
        String token = body.read("$.accessToken", String.class);

        ResponseEntity<String> me = me("Bearer " + token);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        DocumentContext meBody = JsonPath.parse(me.getBody());
        assertThat(meBody.read("$.email", String.class)).isEqualTo(email);
        assertThat(meBody.read("$.role", String.class)).isEqualTo("AGENT");
    }

    @Test
    @DisplayName("パスワード誤りと未登録のメールは、どちらも同じ 401")
    void loginFailuresReturnSame401() {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice");

        ResponseEntity<String> wrongPassword = login(email, "wrong-password");
        ResponseEntity<String> unknownEmail = login(uniqueEmail(), PASSWORD);

        assertProblem(wrongPassword, HttpStatus.UNAUTHORIZED, "invalid-credentials");
        assertProblem(unknownEmail, HttpStatus.UNAUTHORIZED, "invalid-credentials");
        assertThat(JsonPath.parse(wrongPassword.getBody()).read("$.detail", String.class))
                .isEqualTo(JsonPath.parse(unknownEmail.getBody()).read("$.detail", String.class));
    }

    @Test
    @DisplayName("トークンなしの /api/me は 401（FR-1.3）")
    void missingTokenReturns401() {
        ResponseEntity<String> response = me(null);

        assertUnauthorized(response);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"Bearer not-a-jwt", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.invalid-signature", "Basic dXNlcjpwYXNz"})
    @DisplayName("形式・署名が不正なトークン、Bearer 以外の方式は 401（FR-1.3）")
    void malformedTokenReturns401(String authorization) {
        ResponseEntity<String> response = me(authorization);

        assertUnauthorized(response);
    }

    @Test
    @DisplayName("別の鍵で署名したトークンは 401（FR-1.3）")
    void tokenSignedWithAnotherKeyReturns401() {
        JwtProperties otherKey = new JwtProperties("another-integration-test-secret-0123456789abcdef", 60);
        String token = new JwtService(new JwtConfig().jwtEncoder(otherKey), otherKey, Clock.systemUTC())
                .issue(registeredUserId(), Role.AGENT)
                .accessToken();

        assertUnauthorized(me("Bearer " + token));
    }

    @Test
    @DisplayName("有効期限切れのトークンは 401（FR-1.3）")
    void expiredTokenReturns401() {
        Clock twoHoursAgo = Clock.offset(Clock.systemUTC(), Duration.ofHours(-2));
        String token = new JwtService(jwtEncoder, jwtProperties, twoHoursAgo)
                .issue(registeredUserId(), Role.AGENT)
                .accessToken();

        assertUnauthorized(me("Bearer " + token));
    }

    @Test
    @DisplayName("ヘルスチェックは認証なしで 200")
    void healthIsPublic() {
        ResponseEntity<String> response =
                client.get().uri("/actuator/health").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> register(String email, String password, String displayName) {
        return post("/api/auth/register", Map.of("email", email, "password", password, "displayName", displayName));
    }

    private ResponseEntity<String> login(String email, String password) {
        return post("/api/auth/login", Map.of("email", email, "password", password));
    }

    private ResponseEntity<String> post(String path, Map<String, String> body) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> me(String authorization) {
        return client.get()
                .uri("/api/me")
                .headers(headers -> {
                    if (authorization != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .retrieve()
                .toEntity(String.class);
    }

    private UUID registeredUserId() {
        ResponseEntity<String> response = register(uniqueEmail(), PASSWORD, "Alice");
        return UUID.fromString(JsonPath.parse(response.getBody()).read("$.id", String.class));
    }

    private static void assertUnauthorized(ResponseEntity<String> response) {
        assertProblem(response, HttpStatus.UNAUTHORIZED, "unauthorized");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(JsonPath.parse(response.getBody()).read("$.instance", String.class))
                .isEqualTo("/api/me");
    }

    private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String slug) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        DocumentContext body = JsonPath.parse(response.getBody());
        assertThat(body.read("$.type", String.class)).isEqualTo("https://helpdesk.example.com/problems/" + slug);
        assertThat(body.read("$.status", Integer.class)).isEqualTo(status.value());
        assertThat(body.read("$.traceId", String.class)).matches(TRACE_ID_PATTERN);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
