package com.devhub.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OtpEmailSender {
    private static final Logger log = LoggerFactory.getLogger(OtpEmailSender.class);
    private static final String BREVO_SEND_EMAIL_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public OtpEmailSender(
            RestClient.Builder restClientBuilder,
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${mail.from:}") String fromEmail,
            @Value("${mail.from-name:Orbit}") String fromName) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    public void send(String email, String otp) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("BREVO_API_KEY is not configured");
        }
        if (fromEmail.isBlank()) {
            throw new IllegalStateException("MAIL_FROM must be set to a verified Brevo sender email");
        }

        Map<String, Object> request = Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("email", email)),
                "subject", "Your Orbit verification code",
                "textContent", "Your Orbit registration code is " + otp
                        + ". It expires in 10 minutes. If you did not request it, ignore this email.");

        try {
            restClient.post()
                    .uri(BREVO_SEND_EMAIL_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", apiKey)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            log.error("Brevo rejected the OTP email request with HTTP status {}", exception.getStatusCode().value());
            throw new OtpEmailDeliveryException();
        } catch (ResourceAccessException exception) {
            log.error("Could not connect to Brevo's email API ({})", exception.getClass().getSimpleName());
            throw new OtpEmailDeliveryException();
        }
    }
}
