package com.lankapay.analytics.service;

import com.lankapay.analytics.web.dto.ActivityItemDto;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side generator that constructs compliant PDF-1.4 binary documents
 * with "SIMULATED" watermarking, masked identifiers, and formatted statement tables.
 */
@Component
public class StatementPdfGenerator {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Colombo"));

    public byte[] generateStatementPdf(
            String customerName,
            String customerPhoneMasked,
            String period,
            long totalSpentMinor,
            long totalReceivedMinor,
            List<ActivityItemDto> items
    ) {
        try {
            ByteArrayOutputStream streamContent = new ByteArrayOutputStream();

            StringBuilder sb = new StringBuilder();

            // ── Watermark Background ──
            sb.append("q\n");
            sb.append("0.90 0.90 0.90 rg\n"); // Light gray
            sb.append("BT\n");
            sb.append("/F2 42 Tf\n");
            sb.append("0.7071 0.7071 -0.7071 0.7071 80 250 Tm\n");
            sb.append("(").append(escapePdf("SIMULATED - FOR TESTING PURPOSES ONLY")).append(") Tj\n");
            sb.append("ET\n");
            sb.append("Q\n");

            // ── Header Box ──
            sb.append("q\n");
            sb.append("0.2 0.4 0.8 rg\n"); // Blue banner
            sb.append("50 720 512 50 re f\n");
            sb.append("Q\n");

            sb.append("BT\n");
            sb.append("1 1 1 rg\n"); // White text
            sb.append("/F2 20 Tf\n");
            sb.append("70 738 Td\n");
            sb.append("(").append(escapePdf("LANKAPAY ID - ACCOUNT STATEMENT")).append(") Tj\n");
            sb.append("ET\n");

            // ── Customer & Statement Details ──
            sb.append("BT\n");
            sb.append("0 0 0 rg\n"); // Black text
            sb.append("/F2 11 Tf\n");
            sb.append("50 690 Td\n");
            sb.append("(").append(escapePdf("Account Holder: " + customerName)).append(") Tj\n");
            sb.append("0 -15 Td\n");
            sb.append("/F1 10 Tf\n");
            sb.append("(").append(escapePdf("Mobile: " + customerPhoneMasked)).append(") Tj\n");
            sb.append("0 -15 Td\n");
            sb.append("(").append(escapePdf("Statement Period: " + period)).append(") Tj\n");
            sb.append("0 -15 Td\n");
            sb.append("(").append(escapePdf("Status: SIMULATED ENVIRONMENT")).append(") Tj\n");
            sb.append("ET\n");

            // ── Summary Box ──
            sb.append("q\n");
            sb.append("0.95 0.95 0.97 rg\n");
            sb.append("350 630 212 75 re f\n");
            sb.append("0.7 0.7 0.7 RG 1 w\n");
            sb.append("350 630 212 75 re S\n");
            sb.append("Q\n");

            sb.append("BT\n");
            sb.append("0 0 0 rg\n");
            sb.append("/F2 11 Tf\n");
            sb.append("365 685 Td\n");
            sb.append("(").append(escapePdf("Summary for " + period)).append(") Tj\n");
            sb.append("0 -18 Td\n");
            sb.append("/F1 10 Tf\n");
            sb.append("(").append(escapePdf(String.format("Total Spent: LKR %.2f", totalSpentMinor / 100.0))).append(") Tj\n");
            sb.append("0 -16 Td\n");
            sb.append("(").append(escapePdf(String.format("Total Received: LKR %.2f", totalReceivedMinor / 100.0))).append(") Tj\n");
            sb.append("ET\n");

            // ── Table Header ──
            int tableTopY = 590;
            sb.append("q\n");
            sb.append("0.25 0.3 0.4 rg\n");
            sb.append("50 ").append(tableTopY).append(" 512 22 re f\n");
            sb.append("Q\n");

            sb.append("BT\n");
            sb.append("1 1 1 rg\n");
            sb.append("/F2 9 Tf\n");
            sb.append("60 ").append(tableTopY + 7).append(" Td\n");
            sb.append("(").append(escapePdf("DATE")).append(") Tj\n");
            sb.append("110 0 Td\n");
            sb.append("(").append(escapePdf("DESCRIPTION")).append(") Tj\n");
            sb.append("160 0 Td\n");
            sb.append("(").append(escapePdf("CATEGORY")).append(") Tj\n");
            sb.append("90 0 Td\n");
            sb.append("(").append(escapePdf("REF / MASK")).append(") Tj\n");
            sb.append("90 0 Td\n");
            sb.append("(").append(escapePdf("AMOUNT (LKR)")).append(") Tj\n");
            sb.append("ET\n");

            // ── Table Rows ──
            int y = tableTopY - 18;
            int rowCount = 0;
            for (ActivityItemDto item : items) {
                if (rowCount++ >= 28) break; // Limit single page layout

                boolean isCredit = "CREDIT".equalsIgnoreCase(item.direction());
                String dateStr = item.createdAt() != null ? DATE_FMT.format(item.createdAt()) : "N/A";
                String desc = truncate(item.title(), 24);
                String cat = truncate(item.category(), 14);
                String refMask = truncate(item.cardLast4() != null ? "Card ••" + item.cardLast4() : item.reference(), 16);
                String sign = isCredit ? "+" : "-";
                String amtStr = sign + String.format("%.2f", item.amountMinor() / 100.0);

                // Alternating row background
                if (rowCount % 2 == 0) {
                    sb.append("q\n0.97 0.97 0.98 rg\n50 ").append(y - 4).append(" 512 16 re f\nQ\n");
                }

                sb.append("BT\n");
                sb.append("0.1 0.1 0.1 rg\n");
                sb.append("/F1 8 Tf\n");
                sb.append("60 ").append(y).append(" Td\n");
                sb.append("(").append(escapePdf(dateStr)).append(") Tj\n");
                sb.append("110 0 Td\n");
                sb.append("(").append(escapePdf(desc)).append(") Tj\n");
                sb.append("160 0 Td\n");
                sb.append("(").append(escapePdf(cat)).append(") Tj\n");
                sb.append("90 0 Td\n");
                sb.append("(").append(escapePdf(refMask)).append(") Tj\n");
                sb.append("90 0 Td\n");
                if (isCredit) {
                    sb.append("0 0.5 0 rg\n"); // Green
                }
                sb.append("(").append(escapePdf(amtStr)).append(") Tj\n");
                sb.append("ET\n");

                y -= 17;
            }

            // ── Footer ──
            sb.append("BT\n");
            sb.append("0.5 0.5 0.5 rg\n");
            sb.append("/F1 8 Tf\n");
            sb.append("50 30 Td\n");
            sb.append("(").append(escapePdf("Simulated LankaPay Statement • Generated electronically • Validated for sandbox testing only")).append(") Tj\n");
            sb.append("ET\n");

            byte[] contentBytes = sb.toString().getBytes(StandardCharsets.ISO_8859_1);

            return assemblePdf(contentBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to construct statement PDF", e);
        }
    }

    private byte[] assemblePdf(byte[] contentBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();

        // 1. Header
        out.write("%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1));

        // 2. Object 1: Catalog
        offsets.add(out.size());
        out.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

        // 3. Object 2: Pages
        offsets.add(out.size());
        out.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

        // 4. Object 3: Page
        offsets.add(out.size());
        out.write("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n"
                .getBytes(StandardCharsets.ISO_8859_1));

        // 5. Object 4: Font F1 (Helvetica)
        offsets.add(out.size());
        out.write("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

        // 6. Object 5: Font F2 (Helvetica-Bold)
        offsets.add(out.size());
        out.write("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

        // 7. Object 6: Stream Contents
        offsets.add(out.size());
        String streamHeader = "6 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n";
        out.write(streamHeader.getBytes(StandardCharsets.ISO_8859_1));
        out.write(contentBytes);
        out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

        // 8. Cross-reference Table
        int xrefOffset = out.size();
        out.write("xref\n0 7\n0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
        for (int offset : offsets) {
            String entry = String.format("%010d 00000 n \n", offset);
            out.write(entry.getBytes(StandardCharsets.ISO_8859_1));
        }

        // 9. Trailer & EOF
        String trailer = "trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n";
        out.write(trailer.getBytes(StandardCharsets.ISO_8859_1));

        return out.toByteArray();
    }

    private String escapePdf(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }
}
