package io.github.poupeai.ingestion.service.parser;


import io.github.poupeai.ingestion.domain.model.BankTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OfxParserService {
    private static final Pattern BANK_ID_PATTERN = Pattern.compile("<BANKID>(\\d+)");
    private static final Pattern TRANSACTION_BLOCK_PATTERN = Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.DOTALL);

    private static final Pattern TRNTYPE_PATTERN = Pattern.compile("<TRNTYPE>(\\w+)");
    private static final Pattern TRNAMT_PATTERN = Pattern.compile("<TRNAMT>([-\\d.]+)");
    private static final Pattern FITID_PATTERN = Pattern.compile("<FITID>([^<\n\r]+)");
    private static final Pattern MEMO_PATTERN = Pattern.compile("<MEMO>([^<\n\r]+)");
    private static final Pattern NAME_PATTERN = Pattern.compile("<NAME>([^<\n\r]+)");
    private static final Pattern DTPOSTED_PATTERN = Pattern.compile("<DTPOSTED>(\\d{14})");

    private static final DateTimeFormatter OFX_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public List<BankTransaction> parse(InputStream inputStream) {
        List<BankTransaction> transactions = new ArrayList<>();
        StringBuilder contentBuilder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha ao ler stream do OFX", e);
        }

        String content = contentBuilder.toString();
        String bankCode = extractTagValue(BANK_ID_PATTERN, content);
        if (bankCode == null) bankCode = "UNKNOWN";

        Matcher matcher = TRANSACTION_BLOCK_PATTERN.matcher(content);

        while (matcher.find()) {
            String block = matcher.group(1);

            try {
                String amountStr = extractTagValue(TRNAMT_PATTERN, block);
                if (amountStr == null) continue;

                BigDecimal amount = new BigDecimal(amountStr);

                if (amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                String dateStr = extractTagValue(DTPOSTED_PATTERN, block);
                String type = extractTagValue(TRNTYPE_PATTERN, block);
                String fitId = extractTagValue(FITID_PATTERN, block);

                String name = extractTagValue(NAME_PATTERN, block);
                String memo = extractTagValue(MEMO_PATTERN, block);

                String finalDescription = buildDescription(name, memo);

                transactions.add(BankTransaction.builder()
                        .bankCode(bankCode)
                        .fitId(fitId != null ? fitId.trim() : null)
                        .type(type)
                        .amount(amount)
                        .description(finalDescription)
                        .date(LocalDateTime.parse(dateStr, OFX_DATE_FMT))
                        .build());

            } catch (Exception e) {
                log.error("Falha ao converter transação específica do OFX: {}", block, e);
            }
        }

        return transactions;
    }

    private String extractTagValue(Pattern pattern, String content) {
        Matcher m = pattern.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String buildDescription(String name, String memo) {
        if (name == null) name = "";
        if (memo == null) memo = "";

        name = name.trim();
        memo = memo.trim();

        if (name.isEmpty()) return memo.isEmpty() ? "Sem descrição" : memo;
        if (memo.isEmpty()) return name;

        if (name.equalsIgnoreCase(memo)) return name;

        if (memo.toLowerCase().contains(name.toLowerCase())) return memo;

        return name + " - " + memo;
    }
}
