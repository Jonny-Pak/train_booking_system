package com.example.ticketbooking.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IpnResponse(@JsonProperty("RspCode") String code, @JsonProperty("Message") String message) {
    public static IpnResponse confirmed() { return new IpnResponse("00", "Confirm Success"); }
    public static IpnResponse duplicate() { return new IpnResponse("02", "Order already confirmed"); }
    public static IpnResponse invalid() { return new IpnResponse("97", "Invalid signature or merchant"); }
    public static IpnResponse missing() { return new IpnResponse("01", "Order not found"); }
    public static IpnResponse amount() { return new IpnResponse("04", "Invalid amount"); }
    public static IpnResponse error() { return new IpnResponse("99", "Unable to confirm transaction"); }
}
