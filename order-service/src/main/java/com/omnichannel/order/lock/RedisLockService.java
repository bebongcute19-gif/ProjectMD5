package com.omnichannel.order.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/**
 * Enterprise Distributed Lock & Lua Atomic Inventory Service using Redis.
 * Guarantees ultra-high concurrency throughput and sub-millisecond stock checks.
 */
@Service
public class RedisLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);

    private final StringRedisTemplate redisTemplate;

    // Atomic Lua script for safe lock release (only release if value matches owner)
    private static final String RELEASE_LOCK_LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    // Atomic Lua script for instant inventory pre-deduction:
    // Returns remaining stock (>= 0) on success, -1 if out of stock, -2 if key missing
    private static final String DEDUCT_STOCK_LUA_SCRIPT =
            "local curr = redis.call('get', KEYS[1]) " +
            "if not curr then return -2 end " +
            "local val = tonumber(curr) " +
            "local qty = tonumber(ARGV[1]) " +
            "if val >= qty then " +
            "    return redis.call('decrby', KEYS[1], qty) " +
            "else " +
            "    return -1 " +
            "end";

    // Atomic Lua script for conditional stock restoration:
    // Returns new stock (>= 0) if key exists and incremented, or -1 if key does not exist (prevents corrupted key creation)
    private static final String RESTORE_STOCK_LUA_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 1 then " +
            "    return redis.call('incrby', KEYS[1], ARGV[1]) " +
            "else " +
            "    return -1 " +
            "end";

    private final DefaultRedisScript<Long> releaseScript;
    private final DefaultRedisScript<Long> deductScript;
    private final DefaultRedisScript<Long> restoreScript;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setScriptText(RELEASE_LOCK_LUA_SCRIPT);
        this.releaseScript.setResultType(Long.class);

        this.deductScript = new DefaultRedisScript<>();
        this.deductScript.setScriptText(DEDUCT_STOCK_LUA_SCRIPT);
        this.deductScript.setResultType(Long.class);

        this.restoreScript = new DefaultRedisScript<>();
        this.restoreScript.setScriptText(RESTORE_STOCK_LUA_SCRIPT);
        this.restoreScript.setResultType(Long.class);
    }

    /**
     * Attempts to acquire distributed lock with retries up to waitTimeMs.
     */
    public boolean acquireLock(String lockKey, String lockValue, long expireTimeMs, long waitTimeMs) {
        long startTime = System.currentTimeMillis();
        long retryIntervalMs = 15; // 15ms interval for faster spin

        try {
            while (true) {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, lockValue, Duration.ofMillis(expireTimeMs));

                if (Boolean.TRUE.equals(acquired)) {
                    log.debug(">>> [RedisLock] Lock acquired for key: {}, owner: {}", lockKey, lockValue);
                    return true;
                }

                if (System.currentTimeMillis() - startTime >= waitTimeMs) {
                    log.warn(">>> [RedisLock] Timeout waiting for lock key: {}, waited: {}ms", lockKey, waitTimeMs);
                    return false;
                }

                Thread.sleep(retryIntervalMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(">>> [RedisLock] Interrupted while acquiring lock for key: {}", lockKey, e);
            return false;
        } catch (Exception e) {
            log.error(">>> [RedisLock] Error acquiring lock: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Atomically releases distributed lock only if held by this owner.
     */
    public boolean releaseLock(String lockKey, String lockValue) {
        try {
            Long result = redisTemplate.execute(
                    releaseScript,
                    Collections.singletonList(lockKey),
                    lockValue
            );
            boolean released = result != null && result == 1L;
            if (released) {
                log.debug(">>> [RedisLock] Released lock for key: {}, owner: {}", lockKey, lockValue);
            }
            return released;
        } catch (Exception e) {
            log.error(">>> [RedisLock] Error releasing lock: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ultra-fast atomic stock pre-deduction in Redis via Lua Script.
     * @return remaining stock (>= 0), or -1 if OUT OF STOCK, or -2 if key not found
     */
    public long preDeductStockLua(Long productId, int quantity) {
        String key = "stock:product:" + productId;
        try {
            Long result = redisTemplate.execute(
                    deductScript,
                    Collections.singletonList(key),
                    String.valueOf(quantity)
            );
            return result != null ? result : -2;
        } catch (Exception e) {
            log.warn(">>> [Redis Lua] Error executing Lua deduct: {}. Falling back to DB.", e.getMessage());
            return -2;
        }
    }

    public void setStockRedis(Long productId, int stock) {
        try {
            redisTemplate.opsForValue().set("stock:product:" + productId, String.valueOf(stock));
            log.info(">>> [Redis Cache] Initialized stock in Redis for Product #{}: {}", productId, stock);
        } catch (Exception e) {
            log.warn(">>> [Redis Cache] Failed to set stock for Product #{}: {}", productId, e.getMessage());
        }
    }

    /**
     * Atomically initializes Redis stock ONLY if the key does not exist (SET ... NX).
     * Prevents race conditions where two concurrent cache misses overwrite each other's deductions.
     */
    public boolean setStockRedisIfAbsent(Long productId, int stock) {
        try {
            Boolean set = redisTemplate.opsForValue().setIfAbsent("stock:product:" + productId, String.valueOf(stock));
            boolean success = Boolean.TRUE.equals(set);
            if (success) {
                log.info(">>> [Redis Cache Rehydration] Successfully hydrated Redis stock via SETNX for Product #{}: {}", productId, stock);
            } else {
                log.debug(">>> [Redis Cache Rehydration] Key stock:product:{} already initialized by another thread, skipping overwrite.", productId);
            }
            return success;
        } catch (Exception e) {
            log.warn(">>> [Redis Cache] Failed to setIfAbsent for Product #{}: {}", productId, e.getMessage());
            return false;
        }
    }

    public long restoreStockRedis(Long productId, int quantity) {
        String key = "stock:product:" + productId;
        try {
            Long result = redisTemplate.execute(
                    restoreScript,
                    Collections.singletonList(key),
                    String.valueOf(quantity)
            );
            long val = result != null ? result : -1;
            if (val == -1) {
                log.warn(">>> [Redis Restore] Key {} does not exist in Redis. Skipped INCRBY to prevent cache pollution.", key);
            } else {
                log.debug(">>> [Redis Restore] Conditionally restored {} items for Product #{}, new Redis stock: {}", quantity, productId, val);
            }
            return val;
        } catch (Exception e) {
            log.warn(">>> [Redis Restore] Failed to conditionally increment stock in Redis for Product #{}: {}", productId, e.getMessage());
            return -1;
        }
    }
}
