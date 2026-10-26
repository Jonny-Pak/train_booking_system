package com.example.ticketbooking.payment;

import com.example.ticketbooking.common.ApiException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
public class VnpayGateway {
    public static final String SANDBOX_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private final boolean enabled;
    private final String merchant;
    private final String secret;
    private final String returnUrl;
    private final String paymentEndpoint;

    public VnpayGateway(@Value("${vnpay.enabled:false}") boolean enabled,
            @Value("${vnpay.tmn-code:}") String merchant,
            @Value("${vnpay.hash-secret:}") String secret,
            @Value("${vnpay.return-url:http://localhost:5173/payment/return}") String returnUrl,
            @Value("${vnpay.payment-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String paymentEndpoint) {
        this.enabled = enabled;
        this.merchant = merchant;
        this.secret = secret;
        this.returnUrl = returnUrl;
        this.paymentEndpoint = paymentEndpoint;
        if (!SANDBOX_URL.equals(paymentEndpoint)) {
            throw new IllegalArgumentException("Only the VNPAY Sandbox payment endpoint is supported");
        }
        if (enabled) {
            URI uri = URI.create(returnUrl);
            boolean localHttp = "http".equals(uri.getScheme()) && uri.getHost() != null &&
                    Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            if (!merchant.matches("[A-Za-z0-9]{1,32}") || secret.length() < 16
                    || !(localHttp || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || uri.getQuery() != null) {
                throw new IllegalArgumentException("Invalid VNPAY Sandbox configuration");
            }
        }
    }

    public void requireEnabled() {
        if (!enabled) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "VNPAY_NOT_CONFIGURED", "VNPAY Sandbox is not configured");
    }

    public String paymentUrl(Payment payment, String ipAddress) {
        requireEnabled();
        Map<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", merchant);
        fields.put("vnp_Amount", amount(payment.getAmount()));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", payment.getTxnRef());
        fields.put("vnp_OrderInfo", "Thanh toan booking " + payment.getBookingId());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", returnUrl);
        fields.put("vnp_IpAddr", ipAddress);
        fields.put("vnp_CreateDate", format(payment.getCreatedAt()));
        fields.put("vnp_ExpireDate", format(payment.getExpiresAt()));
        return paymentEndpoint + "?" + canonical(fields) + "&vnp_SecureHash=" + sign(fields);
    }

    public Map<String, String> verifiedFields(MultiValueMap<String, String> query) {
        if (!enabled || query.size() > 40) return null;
        Map<String, String> fields = new TreeMap<>();
        int length = 0;
        for (var entry : query.entrySet()) {
            if (!entry.getKey().startsWith("vnp_") || entry.getValue().size() != 1) return null;
            String value = entry.getValue().getFirst();
            if (value == null) return null;
            length += entry.getKey().length() + value.length();
            if (length > 16000) return null;
            fields.put(entry.getKey(), value);
        }
        String received = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");
        if (received == null || !received.matches("[a-fA-F0-9]{128}")
                || !merchant.equals(fields.get("vnp_TmnCode"))) return null;
        byte[] expected = HexFormat.of().parseHex(sign(fields));
        if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(received))) return null;
        return fields;
    }

    public String sign(Map<String, String> fields) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(fields).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA512 is required", exception);
        }
    }

    private String canonical(Map<String, String> fields) {
        StringJoiner result = new StringJoiner("&");
        for (var entry : new TreeMap<>(fields).entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                result.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        return result.toString();
    }

    public static String amount(BigDecimal amount) {
        // VND must be a positive whole number and fit VNPAY's 12-digit field after x100.
        try {
            long vnd = amount.longValueExact();
            long encoded = Math.multiplyExact(vnd, 100);
            if (vnd <= 0 || encoded > 999999999999L) throw new ArithmeticException();
            return Long.toString(encoded);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_AMOUNT", "Unsupported VND amount");
        }
    }

    public static String format(Instant time) { return DATE.format(time.atZone(ZONE)); }
    public static Instant parseDate(String value) {
        return LocalDateTime.parse(value, DATE).atZone(ZONE).toInstant();
    }
}
