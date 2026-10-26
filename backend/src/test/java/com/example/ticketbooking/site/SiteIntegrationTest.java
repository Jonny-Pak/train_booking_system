package com.example.ticketbooking.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SiteIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void publicCanReadSeededGuideButCannotEdit() throws Exception {
        mvc.perform(get("/api/v1/site/settings")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.guideHtml").value(org.hamcrest.Matchers.containsString("VNPAY")))
                .andExpect(jsonPath("$.customLogo").value(false));
        String body = guide("<p>Hello</p>", 0);
        mvc.perform(put("/api/v1/admin/site/guide").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/admin/site/guide").with(user("customer").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/site/logo").with(user("customer").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/site/logo?revision=0").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminFormattingIsSanitizedAndStaleSaveDoesNotOverwrite() throws Exception {
        String dirty = "<h2 onclick='alert(1)'>Hướng dẫn</h2><p style='text-align: center; background:url(https://bad.invalid)'><strong>Giữ chỗ</strong><span style='color: #ff0000; font-size: 24px; position:fixed'>Lưu ý</span></p><script>alert(1)</script><img src=x onerror=alert(1)><svg onload=alert(1)></svg>";
        mvc.perform(put("/api/v1/admin/site/guide").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(guide(dirty, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        String saved = mapper.readTree(mvc.perform(get("/api/v1/site/settings")).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("guideHtml").asText();
        assertThat(saved).contains("<strong>Giữ chỗ</strong>", "text-align: center;", "color: #ff0000;", "font-size: 24px;")
                .doesNotContain("onclick", "script", "alert", "<img", "<svg", "background", "position", "bad.invalid");
        mvc.perform(put("/api/v1/admin/site/guide").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(guide("<p>Stale</p>", 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SITE_CHANGED"));
        mvc.perform(get("/api/v1/site/settings")).andExpect(jsonPath("$.guideHtml").value(saved));
    }

    @Test
    void emptyAndOversizedGuideIsRejected() throws Exception {
        for (String html : new String[]{"<p><br></p>", "<script>alert(1)</script>", "a".repeat(100001)}) {
            mvc.perform(put("/api/v1/admin/site/guide").with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content(guide(html, 0))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void imageIsValidatedReencodedServedAndResetWithRevisionProtection() throws Exception {
        String image = image(120, 40);
        mvc.perform(put("/api/v1/admin/site/logo").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("imageBase64", image, "revision", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customLogo").value(true));
        byte[] bytes = mvc.perform(get("/api/v1/site/logo")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(bytes)).getWidth()).isEqualTo(120);
        mvc.perform(delete("/api/v1/admin/site/logo?revision=0").with(user("admin").roles("ADMIN"))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/site/logo?revision=1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customLogo").value(false));
        mvc.perform(get("/api/v1/site/logo")).andExpect(status().isNotFound());
    }

    @Test
    void fakeSvgInvalidBase64OversizedFileAndDimensionsAreRejected() throws Exception {
        for (String input : new String[]{"invalid!", Base64.getEncoder().encodeToString("<svg onload='alert(1)'/>".getBytes()),
                Base64.getEncoder().encodeToString(new byte[2 * 1024 * 1024 + 1]), image(4097, 16)}) {
            mvc.perform(put("/api/v1/admin/site/logo").with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("imageBase64", input, "revision", 0))))
                    .andExpect(status().isBadRequest());
        }
    }

    private String guide(String html, long revision) throws Exception {
        return mapper.writeValueAsString(Map.of("title", "Hướng dẫn kiểm thử", "html", html, "revision", revision));
    }

    private String image(int width, int height) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", stream);
        return Base64.getEncoder().encodeToString(stream.toByteArray());
    }
}
