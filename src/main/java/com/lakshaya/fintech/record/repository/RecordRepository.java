package com.lakshaya.fintech.record.repository;

import com.lakshaya.fintech.record.entity.FinancialRecord;
import com.lakshaya.fintech.record.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecordRepository extends JpaRepository<FinancialRecord, Long> {

    // RULE 4: deleted records always invisible on direct fetch
    Optional<FinancialRecord> findByIdAndIsDeletedFalse(Long id);

    // FIX 1: type is TransactionType enum NOT String — String param causes runtime crash in JPQL
    // is_deleted = false always applied — RULE 4
    @Query("""
        SELECT r FROM FinancialRecord r
        WHERE r.isDeleted = false
          AND (:userId IS NULL OR r.userId = :userId)
          AND (:type IS NULL OR r.type = :type)
          AND (:category IS NULL OR r.category = :category)
          AND (:startDate IS NULL OR r.date >= :startDate)
          AND (:endDate IS NULL OR r.date <= :endDate)
    """)
    Page<FinancialRecord> findAllByFilters(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    // FIX 1: TransactionType enum param — not String
    @Query("""
        SELECT COALESCE(SUM(r.amount), 0)
        FROM FinancialRecord r
        WHERE r.isDeleted = false
          AND r.type = :type
          AND (:userId IS NULL OR r.userId = :userId)
    """)
    BigDecimal findTotalByType(
            @Param("userId") Long userId,
            @Param("type") TransactionType type
    );

    // DB-level GROUP BY category
    @Query("""
        SELECT r.category AS category, SUM(r.amount) AS total
        FROM FinancialRecord r
        WHERE r.isDeleted = false
          AND (:userId IS NULL OR r.userId = :userId)
        GROUP BY r.category
    """)
    List<Object[]> findCategoryBreakdown(@Param("userId") Long userId);

    // FIX 2: nativeQuery = true — DATE_FORMAT() is MySQL-only, crashes on H2
    // MODE=MySQL in application-dev.properties makes this work in dev too
    @Query(value = """
        SELECT DATE_FORMAT(r.date, '%Y-%m') AS month,
               SUM(CASE WHEN r.type = 'INCOME' THEN r.amount ELSE 0 END) AS income,
               SUM(CASE WHEN r.type = 'EXPENSE' THEN r.amount ELSE 0 END) AS expense
        FROM financial_records r
        WHERE r.is_deleted = false
          AND (:userId IS NULL OR r.user_id = :userId)
        GROUP BY DATE_FORMAT(r.date, '%Y-%m')
        ORDER BY month ASC
    """, nativeQuery = true)
    List<Object[]> findMonthlyTrend(@Param("userId") Long userId);
}