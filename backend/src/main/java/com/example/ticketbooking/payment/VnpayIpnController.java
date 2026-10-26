package com.example.ticketbooking.payment;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class VnpayIpnController {
    private final PaymentService service;
    public VnpayIpnController(PaymentService service) { this.service = service; }

    @GetMapping("/api/v1/payments/vnpay/ipn")
    public ResponseEntity<IpnResponse> ipn(@RequestParam MultiValueMap<String, String> query) {
        IpnResponse result;
        try {
            // The transactional service commits before this response is acknowledged.
            result = service.confirm(query);
        } catch (RuntimeException exception) {
            // VNPAY must be able to retry on rollback. Never log callback payloads or secrets.
            result = IpnResponse.error();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
