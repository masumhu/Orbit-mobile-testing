package com.devhub.ticket;

import java.time.Instant;

public record TicketChange(String field, String oldValue, String newValue, String changedByUserId, Instant changedAt) { }
