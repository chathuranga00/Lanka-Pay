package com.lankapay.support.domain;

import java.util.List;

public record FaqItem(
        String id,
        String title,
        String question,
        List<String> keywords,
        String answer
) {}
