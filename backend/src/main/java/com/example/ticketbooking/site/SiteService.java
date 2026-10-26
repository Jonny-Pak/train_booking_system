package com.example.ticketbooking.site;

import com.example.ticketbooking.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteService {
    private final JdbcTemplate jdbc;

    public SiteService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public SiteSettings get() {
        return jdbc.queryForObject("SELECT guide_title, guide_html, logo_png IS NOT NULL AS custom_logo, revision, updated_at FROM site_settings WHERE id = 1",
                (rs, row) -> new SiteSettings(rs.getString("guide_title"), GuideHtml.clean(rs.getString("guide_html")),
                        rs.getBoolean("custom_logo"), rs.getLong("revision"), rs.getTimestamp("updated_at").toInstant()));
    }

    public byte[] logo() {
        byte[] bytes = jdbc.queryForObject("SELECT logo_png FROM site_settings WHERE id = 1", byte[].class);
        if (bytes == null) throw new ApiException(HttpStatus.NOT_FOUND, "LOGO_NOT_FOUND", "Default logo is in use");
        return bytes;
    }

    @Transactional
    public SiteSettings saveGuide(String title, String html, long revision) {
        String clean = GuideHtml.clean(html);
        if (clean.length() > 100000 || Jsoup.parseBodyFragment(clean).text().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GUIDE_EMPTY", "Guide must contain text");
        }
        checkUpdated(jdbc.update("UPDATE site_settings SET guide_title = ?, guide_html = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP WHERE id = 1 AND revision = ?",
                title.trim(), clean, revision));
        return get();
    }

    @Transactional
    public SiteSettings saveLogo(String base64, long revision) {
        byte[] image = png(base64);
        checkUpdated(jdbc.update("UPDATE site_settings SET logo_png = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP WHERE id = 1 AND revision = ?",
                image, revision));
        return get();
    }

    @Transactional
    public SiteSettings resetLogo(long revision) {
        checkUpdated(jdbc.update("UPDATE site_settings SET logo_png = NULL, revision = revision + 1, updated_at = CURRENT_TIMESTAMP WHERE id = 1 AND revision = ?", revision));
        return get();
    }

    private void checkUpdated(int count) {
        if (count != 1) throw new ApiException(HttpStatus.CONFLICT, "SITE_CHANGED", "Settings changed. Reload before editing");
    }

    private byte[] png(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length > 2 * 1024 * 1024) throw invalidLogo();
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidLogo();
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw invalidLogo();
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 16 || height < 16 || width > 4096 || height > 4096) throw invalidLogo();
                    BufferedImage image = reader.read(0);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", output);
                    if (output.size() > 4 * 1024 * 1024) throw invalidLogo();
                    return output.toByteArray();
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidLogo();
        }
    }

    private ApiException invalidLogo() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOGO", "Use PNG/JPEG up to 2 MB, dimensions 16–4096 px");
    }
}
