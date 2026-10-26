import { siteLogoUrl } from "../lib/api";
import { useSite } from "./SiteContext";

export default function BrandLogo({ footer = false, light = false }) {
  const { settings } = useSite();
  const fallback =
    "/images/vexesieure-logo" + (footer && !light ? "" : "-white") + ".svg";
  const src = settings?.customLogo ? siteLogoUrl(settings.revision) : fallback;
  return (
    <img
      key={src}
      className={footer ? "footer-logo" : "brand-logo"}
      src={src}
      alt="Vexesieure"
      width="290"
      height="64"
      onError={(event) => {
        if (settings?.customLogo) event.currentTarget.src = fallback;
      }}
    />
  );
}
