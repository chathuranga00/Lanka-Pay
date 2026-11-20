package com.lankapay.support.web.dto;

import com.lankapay.support.domain.FaqItem;

public record FaqItemDto(
        String id,
        String title,
        String question,
        String answer
) {
    public static FaqItemDto fromDomain(FaqItem faq) {
        return new FaqItemDto(faq.id(), faq.title(), faq.question(), faq.answer());
    }
}
