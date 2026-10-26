package com.example.ticketbooking.auth;

import com.example.ticketbooking.common.ApiException;
import java.net.URI;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class VerificationMailer {
    private final JavaMailSender sender;
    private final String from;
    private final String frontendUrl;

    public VerificationMailer(JavaMailSender sender,
            @Value("${app.mail.from}") String from,
            @Value("${app.email-verification.frontend-url}") String frontendUrl) {
        URI uri = URI.create(frontendUrl);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Invalid email verification frontend URL");
        }
        this.sender = sender;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void send(String email, String token, Instant expiresAt) {
        // Fragment keeps the secret out of HTTP access logs and Referer headers.
        String link = frontendUrl + "/verify-email#token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Xác nhận email tài khoản Lên Đường");
        message.setText("Chào bạn,\n\nVui lòng mở liên kết và nhấn Xác nhận email:\n" + link
                + "\n\nLiên kết chỉ dùng một lần, hết hạn lúc " + expiresAt
                + " (UTC).\nNếu bạn không đăng ký, hãy bỏ qua email này.\n\nLên Đường");
        try {
            sender.send(message);
        } catch (MailException exception) {
            // Do not log the SMTP message, token or credentials.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_SEND_FAILED",
                    "Unable to send verification email. Please try again later");
        }
    }
}
