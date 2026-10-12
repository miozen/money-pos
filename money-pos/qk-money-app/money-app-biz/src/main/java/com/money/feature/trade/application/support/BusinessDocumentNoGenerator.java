package com.money.feature.trade.application.support;

import com.money.feature.trade.infrastructure.persistence.mapper.TradeBusinessDocumentSequenceMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** TRADE-owned readable business document number generator; callers invoke it inside their existing write transaction. */
@Component
@RequiredArgsConstructor
public class BusinessDocumentNoGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final TradeBusinessDocumentSequenceMapper sequenceMapper;

    public String next(String prefix) {
        if (prefix == null || !prefix.matches("[A-Z]{2,6}")) throw new BaseException("业务单据前缀不合法");
        LocalDateTime now = LocalDateTime.now();
        sequenceMapper.increment(prefix, now.format(DATE));
        Long serial = sequenceMapper.currentSequence();
        if (serial == null || serial < 1 || serial > 9999) throw new BaseException("业务单据日序号异常");
        return prefix + now.format(TIMESTAMP) + String.format("%04d", serial);
    }
}
