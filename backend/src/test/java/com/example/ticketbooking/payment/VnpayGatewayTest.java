package com.example.ticketbooking.payment;

import static org.assertj.core.api.Assertions.*;
import com.example.ticketbooking.common.ApiException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VnpayGatewayTest {
    @Test
    void signingMatchesAnIndependentNodeCryptoVector() {
        VnpayGateway gateway = new VnpayGateway(true,"P6TEST01","test-only-vnpay-secret-32-characters","http://localhost:5173/payment/return");
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
        VnpayGateway gateway = new VnpayGateway(false,"","","http://localhost:5173/payment/return");
        assertThatThrownBy(gateway::requireEnabled).isInstanceOf(ApiException.class).hasMessage("VNPAY Sandbox is not configured");
        assertThatThrownBy(() -> new VnpayGateway(true,"P6TEST01","","http://localhost:5173/payment/return"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
