package com.devhub.service;

public class OtpEmailDeliveryException extends RuntimeException {
    public OtpEmailDeliveryException() {
        super("OTP email delivery failed through the email provider");
    }
}
