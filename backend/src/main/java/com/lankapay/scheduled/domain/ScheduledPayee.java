package com.lankapay.scheduled.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduledPayee(
        String identifier,
        String name,
        String billerId,
        String customerReference,
        String note
) {
    public static ScheduledPayee forTransfer(String identifier, String name, String note) {
        return new ScheduledPayee(identifier, name, null, null, note);
    }

    public static ScheduledPayee forBill(String billerId, String customerReference, String billerName, String note) {
        return new ScheduledPayee(customerReference, billerName, billerId, customerReference, note);
    }
}
