package com.example.ticketbooking.site;

import java.time.Instant;

public record SiteSettings(String guideTitle, String guideHtml, boolean customLogo,
        long revision, Instant updatedAt) {}
