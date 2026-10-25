package com.example.ticketbooking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ticketbooking.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthIntegrationTest.AdminProbe.class)
@Transactional
class AuthIntegrationTest {
    private static final String EMAIL = "auth-test@example.invalid";
    private static final String PASSWORD = "Auth-test-password-21!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtSettings settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void registerNormalizesEmailAndHashesPasswordWithoutLeakingIt() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("  AUTH-TEST@EXAMPLE.INVALID  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Test Customer"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        var user = users.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$2a$12$").isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailWithDifferentCaseReturnsConflict() throws Exception {
        createCustomer();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("  AUTH-TEST@EXAMPLE.INVALID ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        assertThat(users.findAll()).filteredOn(user -> EMAIL.equals(user.getEmail())).hasSize(1);
    }

    @Test
    void clientCannotChooseAdminRoleOrSubmitOtherUnknownFields() throws Exception {
        var body = mapper.readTree(registerBody(EMAIL));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("role", "ADMIN");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertThat(users.findByEmail(EMAIL)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "                                                                         ",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢漢"})
    void invalidOrOver72BytePasswordReturnsValidationError(String password) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("fullName", "Customer",
                                "email", EMAIL, "password", password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void invalidEmailAndBlankNameReturnFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("fullName", " ",
                                "email", "invalid-email", "password", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());
    }

    @Test
    void passwordAtExactly72Utf8BytesCanRegisterAndLogin() throws Exception {
        String password = "漢".repeat(24);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("fullName", "Customer",
                                "email", EMAIL, "password", password))))
                .andExpect(status().isCreated());
        assertThat(login(EMAIL, password).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void loginIssuesBearerTokenThatCanReadCurrentUserAcrossRequests() throws Exception {
        long id = createCustomer();
        MvcResult result = login(" AUTH-TEST@EXAMPLE.INVALID ", PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(response.get("user").has("passwordHash")).isFalse();
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        String token = response.get("accessToken").asText();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.email").value(EMAIL))
                    .andExpect(jsonPath("$.role").value("CUSTOMER"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }
    }

    @Test
    void incorrectPasswordAndUnknownEmailHaveIdenticalCredentialErrors() throws Exception {
        createCustomer();
        MvcResult wrongPassword = login(EMAIL, "Incorrect-password!");
        MvcResult unknownEmail = login("missing@example.invalid", PASSWORD);
        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        JsonNode first = mapper.readTree(wrongPassword.getResponse().getContentAsString());
        JsonNode second = mapper.readTree(unknownEmail.getResponse().getContentAsString());
        assertThat(first.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(first.get("message")).isEqualTo(second.get("message"));
    }

    @Test
    void meRequiresBearerHeaderAndDoesNotAcceptTokenInCookieOrQuery() throws Exception {
        createCustomer();
        String token = accessToken();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", token)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").param("access_token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerGets403AndAdminCanAccessAdminProtectedHandler() throws Exception {
        long id = createCustomer();
        String token = accessToken();
        mvc.perform(get("/api/v1/admin/p2-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", id);
        entityManager.clear();
        mvc.perform(get("/api/v1/admin/p2-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        jdbc.update("UPDATE users SET role = 'CUSTOMER' WHERE id = ?", id);
        entityManager.clear();
        mvc.perform(get("/api/v1/admin/p2-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenRoleClaimCannotOverrideDatabaseRole() throws Exception {
        long id = createCustomer();
        String token = signedToken(id, "valid"); // helper deliberately signs role=ADMIN
        mvc.perform(get("/api/v1/admin/p2-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "tampered", "wrong-signature", "wrong-issuer", "wrong-audience",
            "missing-exp", "missing-iat", "future-iat", "future-nbf", "unsigned", "malformed",
            "invalid-sub", "unknown-user"})
    void invalidTokensReturn401WithoutInternalDetails(String kind) throws Exception {
        long id = createCustomer();
        String token = signedToken(id, kind);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void deletedUserCannotUsePreviouslyIssuedToken() throws Exception {
        long id = createCustomer();
        String token = accessToken();
        jdbc.update("DELETE FROM users WHERE id = ?", id);
        entityManager.clear();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRegistrationCreatesExactlyOneAccount() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.invalid";
        String body = registerBody(email);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> registration = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
                return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(registration);
            var second = executor.submit(registration);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
                    .isEqualTo(1);
        } finally {
            start.countDown();
            jdbc.update("DELETE FROM users WHERE email = ?", email);
        }
    }

    private long createCustomer() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(EMAIL))).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String registerBody(String email) throws Exception {
        return mapper.writeValueAsString(Map.of("fullName", " Test Customer ", "email", email,
                "password", PASSWORD, "phone", "0900000000"));
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
    }

    private String accessToken() throws Exception {
        MvcResult result = login(EMAIL, PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String signedToken(long userId, String kind) {
        if (kind.equals("malformed")) return "not-a-jwt";
        if (kind.equals("unsigned")) return new PlainJWT(new JWTClaimsSet.Builder()
                .subject(Long.toString(userId)).build()).serialize();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(kind.equals("invalid-sub") ? "invalid" : kind.equals("unknown-user")
                        ? Long.toString(Long.MAX_VALUE) : Long.toString(userId))
                .issuer(kind.equals("wrong-issuer") ? "other" : settings.issuer())
                .audience(List.of(kind.equals("wrong-audience") ? "other" : settings.audience()))
                .claim("role", "ADMIN");
        if (!kind.equals("missing-iat")) claims.issuedAt(kind.equals("future-iat") ? now.plusSeconds(60) : now.minusSeconds(120));
        if (!kind.equals("missing-exp")) claims.expiresAt(kind.equals("expired") ? now.minusSeconds(1) : now.plusSeconds(300));
        if (kind.equals("future-nbf")) claims.notBefore(now.plusSeconds(60));
        JwtEncoder signer = kind.equals("wrong-signature") ? new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(new byte[32], "HmacSHA256"))) : encoder;
        String token = signer.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
        if (kind.equals("tampered")) {
            int index = token.lastIndexOf('.') + 1;
            token = token.substring(0, index) + (token.charAt(index) == 'A' ? 'B' : 'A') + token.substring(index + 1);
        }
        return token;
    }

    @RestController
    static class AdminProbe {
        @GetMapping("/api/v1/admin/p2-probe")
        @PreAuthorize("hasRole('ADMIN')")
        public Map<String, Boolean> probe() { return Map.of("allowed", true); }
    }
}
