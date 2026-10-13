package com.money.feature.trade.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Atomic per-prefix, per-day sequence; callers read the row in their existing write transaction. */
public interface TradeBusinessDocumentSequenceMapper {
    @Insert("INSERT INTO trade_business_document_sequence (document_prefix, business_date, sequence_value) " +
            "VALUES (#{prefix}, #{businessDate}, 1) " +
            "ON DUPLICATE KEY UPDATE sequence_value = sequence_value + 1")
    int increment(@Param("prefix") String prefix, @Param("businessDate") String businessDate);

    @Select("SELECT sequence_value FROM trade_business_document_sequence " +
            "WHERE document_prefix = #{prefix} AND business_date = #{businessDate} FOR UPDATE")
    Long currentSequence(@Param("prefix") String prefix, @Param("businessDate") String businessDate);
}
