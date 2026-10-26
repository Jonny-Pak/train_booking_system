package com.example.ticketbooking.payment;

import static org.assertj.core.api.Assertions.*;
import com.example.ticketbooking.common.ApiException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VnpayGatewayTest {
    @Test
    void publicReturnRequiresHttpsAndGatewayRemainsSandboxOnly() {
        for (String url : new String[]{"http://merchant.example/payment/return", "https://user:pass@merchant.example/return",
                "https://merchant.example/return?redirect=other", "https://merchant.example/return#fragment"}) {
            assertThatThrownBy(() -> new VnpayGateway(true, "P6TEST01", "test-only-secret-32-characters", url, VnpayGateway.SANDBOX_URL))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new VnpayGateway(true, "P6TEST01", "test-only-secret-32-characters",
                "https://merchant.example/payment/return", "https://pay.vnpay.vn/vpcpay.html"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsAllRequiredFieldsAndSignsThePublicReturnUrl() {
        String returnUrl = "https://callback.example/payment/return";
        VnpayGateway gateway = new VnpayGateway(true, "P6TEST01", "test-only-secret-32-characters", returnUrl, VnpayGateway.SANDBOX_URL);
        var now = java.time.Instant.parse("2030-01-01T00:00:00Z");
        Payment payment = new Payment(42, "unique-attempt", new BigDecimal("150000.00"), now, now.plusSeconds(600));
        String url = gateway.paymentUrl(payment, "127.0.0.1");
        var params = new org.springframework.util.LinkedMultiValueMap<String, String>();
        for (String pair : java.net.URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.add(java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThat(params.toSingleValueMap()).containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_Command", "pay").containsEntry("vnp_TmnCode", "P6TEST01")
                .containsEntry("vnp_Amount", "15000000").containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_TxnRef", "unique-attempt").containsEntry("vnp_OrderInfo", "Thanh toan booking 42")
                .containsEntry("vnp_OrderType", "other").containsEntry("vnp_Locale", "vn")
                .containsEntry("vnp_ReturnUrl", returnUrl).containsEntry("vnp_IpAddr", "127.0.0.1")
                .containsEntry("vnp_CreateDate", "20300101070000").containsEntry("vnp_ExpireDate", "20300101071000");
        assertThat(gateway.verifiedFields(params)).isNotNull();
        params.set("vnp_ReturnUrl", "https://other.example/return");
        assertThat(gateway.verifiedFields(params)).isNull();
    }
    @Test
    void signingMatchesAnIndependentNodeCryptoVector() {
        VnpayGateway gateway = new VnpayGateway(true,"P6TEST01","test-only-vnpay-secret-32-characters","http://localhost:5173/payment/return", VnpayGateway.SANDBOX_URL);
        assertThat(gateway.sign(Map.of("vnp_TmnCode","P6TEST01","vnp_OrderInfo","Thanh toan + test","vnp_Amount","100000")))
                .isEqualTo("96f06ff4d3811095c1d389c2aa696c3ecb2871ed0086a9945ea651712b19377475a3e38ab6d726e7a0d40143720600c52f04130497c81ccb4462b4ded9b4a659");
    }
    @Test
    void amountsMustBeWholePositiveVndAndFitTheProviderField() {
        assertThat(VnpayGateway.amount(new BigDecimal("150000.00"))).isEqualTo("15000000");
        for (String value : new String[]{"0","-1","1.01","10000000000"}) {
            assertThatThrownBy(() -> VnpayGateway.amount(new BigDecimal(value))).isInstanceOf(ApiException.class);
        }
    }
    @Test
    void disabledGatewayFailsClearlyWithoutExposingCredentials() {
        VnpayGateway gateway = new VnpayGateway(false,"","","http://localhost:5173/payment/return", VnpayGateway.SANDBOX_URL);
        assertThatThrownBy(gateway::requireEnabled).isInstanceOf(ApiException.class).hasMessage("VNPAY Sandbox is not configured");
        assertThatThrownBy(() -> new VnpayGateway(true,"P6TEST01","","http://localhost:5173/payment/return", VnpayGateway.SANDBOX_URL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
