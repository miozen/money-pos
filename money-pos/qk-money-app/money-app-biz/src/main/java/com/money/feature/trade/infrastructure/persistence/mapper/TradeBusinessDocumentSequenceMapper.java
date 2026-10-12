package com.money.feature.trade.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Atomic per-prefix, per-day sequence; LAST_INSERT_ID is scoped to the transaction connection. */
public interface TradeBusinessDocumentSequenceMapper {
    @Insert("INSERT INTO trade_business_document_sequence (document_prefix, business_date, sequence_value) " +
            "VALUES (#{prefix}, #{businessDate}, LAST_INSERT_ID(1)) " +
            "ON DUPLICATE KEY UPDATE sequence_value = LAST_INSERT_ID(sequence_value + 1)")
    int increment(@Param("prefix") String prefix, @Param("businessDate") String businessDate);

    @Select("SELECT LAST_INSERT_ID()")
    Long currentSequence();
}
