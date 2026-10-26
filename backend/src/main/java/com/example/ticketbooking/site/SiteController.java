package com.example.ticketbooking.site;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/site")
public class SiteController {
    private final SiteService service;

    public SiteController(SiteService service) { this.service = service; }

    @GetMapping("/settings")
    public ResponseEntity<SiteSettings> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get());
    }

    @GetMapping(value = "/logo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> logo() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(service.logo());
    }
}
