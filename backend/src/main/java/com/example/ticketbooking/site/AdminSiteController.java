package com.example.ticketbooking.site;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/site")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSiteController {
    public record GuideRequest(@NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 100000) String html, @NotNull @PositiveOrZero Long revision) {}
    public record LogoRequest(@NotBlank @Size(max = 2796204) String imageBase64,
            @NotNull @PositiveOrZero Long revision) {}
    private final SiteService service;

    public AdminSiteController(SiteService service) { this.service = service; }

    @PutMapping("/guide")
    public SiteSettings guide(@Valid @RequestBody GuideRequest request) {
        return service.saveGuide(request.title(), request.html(), request.revision());
    }

    @PutMapping("/logo")
    public SiteSettings logo(@Valid @RequestBody LogoRequest request) {
        return service.saveLogo(request.imageBase64(), request.revision());
    }

    @DeleteMapping("/logo")
    public SiteSettings reset(@RequestParam @PositiveOrZero long revision) {
        return service.resetLogo(revision);
    }
}
