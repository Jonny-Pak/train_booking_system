package com.example.ticketbooking.site;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/** Only editor formatting is allowed. No scripts, links, images or arbitrary CSS. */
public final class GuideHtml {
    private GuideHtml() {}

    public static String clean(String html) {
        Safelist allowed = new Safelist().addTags("p", "br", "h2", "h3", "strong", "b", "em", "i",
                "u", "s", "strike", "ul", "ol", "li", "blockquote", "span", "hr")
                .addAttributes("span", "style").addAttributes("p", "style")
                .addAttributes("h2", "style").addAttributes("h3", "style");
        Document document = new Cleaner(allowed).clean(Jsoup.parseBodyFragment(html));
        for (Element element : document.select("[style]")) {
            StringBuilder safe = new StringBuilder();
            for (String declaration : element.attr("style").split(";")) {
                String normalized = declaration.trim().toLowerCase();
                if (normalized.matches("color:\\s*(#[0-9a-f]{6}|rgb\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*\\))")
                        || normalized.matches("font-size:\\s*(14|16|18|20|24|28|32)px")
                        || normalized.matches("text-align:\\s*(left|center|right|justify)")) {
                    safe.append(normalized).append(';');
                }
            }
            element.removeAttr("style");
            if (!safe.isEmpty()) element.attr("style", safe.toString());
        }
        document.outputSettings().prettyPrint(false);
        return document.body().html();
    }
}
