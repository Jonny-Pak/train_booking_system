import { Link } from "react-router-dom";
import { ArrowRight, BookOpen, ShieldCheck } from "lucide-react";
import { useSite } from "../site/SiteContext";
import GuideContent from "../site/GuideContent";
import { ErrorNotice, Loading } from "../components/Feedback";

export default function BookingGuidePage() {
  const { settings, error, refresh } = useSite();
  return (
    <section className="booking-guide-page">
      <header className="guide-heading">
        <span className="guide-eyebrow">
          <BookOpen size={18} /> CẨM NANG HÀNH TRÌNH
        </span>
        <h1>{settings?.guideTitle || "Hướng dẫn đặt vé tàu xe"}</h1>
        <p>
          Tìm chuyến, chọn chỗ, thanh toán và nhận vé điện tử — từng bước rõ
          ràng để bạn an tâm lên đường.
        </p>
        <Link className="button" to="/">
          Tìm chuyến ngay <ArrowRight size={17} />
        </Link>
      </header>
      <ErrorNotice error={error} retry={refresh} />
      {!settings && !error && <Loading text="Đang tải hướng dẫn…" />}
      {settings && (
        <>
          <p className="guide-updated">
            <ShieldCheck size={17} /> Hướng dẫn từ hệ thống · Cập nhật{" "}
            {new Date(settings.updatedAt).toLocaleDateString("vi-VN")}
          </p>
          <GuideContent html={settings.guideHtml} navigation />
        </>
      )}
    </section>
  );
}
