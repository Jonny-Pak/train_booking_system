import DOMPurify from "dompurify";
import { useMemo } from "react";

export default function GuideContent({ html, navigation = false }) {
  const document = useMemo(() => {
    const safe = DOMPurify.sanitize(html, {
      ALLOWED_TAGS: [
        "p",
        "br",
        "h2",
        "h3",
        "strong",
        "b",
        "em",
        "i",
        "u",
        "s",
        "strike",
        "ul",
        "ol",
        "li",
        "blockquote",
        "span",
        "hr",
      ],
      ALLOWED_ATTR: ["style"],
    });
    const body = new DOMParser().parseFromString(safe, "text/html").body;
    const headings = Array.from(body.querySelectorAll("h2"));
    headings.forEach((heading, index) => {
      heading.id = "guide-section-" + index;
    });
    return {
      html: body.innerHTML,
      headings: headings.map((heading) => ({
        id: heading.id,
        title: heading.textContent,
      })),
    };
  }, [html]);
  return (
    <div className={navigation ? "guide-layout" : ""}>
      {navigation && (
        <aside className="guide-toc">
          <h2>Trong hướng dẫn này</h2>
          <nav aria-label="Mục lục hướng dẫn">
            {document.headings.map((heading) => (
              <a key={heading.id} href={"#" + heading.id}>
                {heading.title}
              </a>
            ))}
          </nav>
        </aside>
      )}
      <article
        className="guide-content"
        dangerouslySetInnerHTML={{ __html: document.html }}
      />
    </div>
  );
}
