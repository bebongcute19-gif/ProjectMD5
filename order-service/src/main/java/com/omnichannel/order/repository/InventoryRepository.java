package com.omnichannel.order.repository;

import com.omnichannel.order.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    /**
     * Dual protection: Pessimistic Write Lock (SELECT ... FOR UPDATE)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findWithPessimisticLockByProductId(@Param("productId") Long productId);

    /**
     * Ultra-High Concurrency Atomic Conditional Update:
     * Decrements availableStock and increments reservedStock in a single atomic SQL statement
     * Returns 1 if successful, 0 if out of stock (availableStock < qty) without holding row locks!
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock - :qty, i.reservedStock = i.reservedStock + :qty, i.version = i.version + 1, i.updatedAt = CURRENT_TIMESTAMP WHERE i.productId = :productId AND i.availableStock >= :qty")
    int deductStockConditional(@Param("productId") Long productId, @Param("qty") int qty);

    /**
     * Atomic Compensation: Restores available stock and decrements reserved stock
     * Completely avoids optimistic locking @Version collisions during high-concurrency rollbacks!
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock + :qty, i.reservedStock = CASE WHEN i.reservedStock >= :qty THEN i.reservedStock - :qty ELSE 0 END, i.version = i.version + 1, i.updatedAt = CURRENT_TIMESTAMP WHERE i.productId = :productId")
    int restoreStockAtomic(@Param("productId") Long productId, @Param("qty") int qty);

    /**
     * Atomic Confirmation: Permanently settles reserved stock on payment success
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.reservedStock = CASE WHEN i.reservedStock >= :qty THEN i.reservedStock - :qty ELSE 0 END, i.version = i.version + 1, i.updatedAt = CURRENT_TIMESTAMP WHERE i.productId = :productId")
    int releaseReservedStockAtomic(@Param("productId") Long productId, @Param("qty") int qty);

    /**
     * Atomic Restoration for Confirmed Orders:
     * Only restores availableStock without modifying reservedStock (which was already settled on confirmation).
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock + :qty, i.version = i.version + 1, i.updatedAt = CURRENT_TIMESTAMP WHERE i.productId = :productId")
    int restoreAvailableStockOnly(@Param("productId") Long productId, @Param("qty") int qty);
}
