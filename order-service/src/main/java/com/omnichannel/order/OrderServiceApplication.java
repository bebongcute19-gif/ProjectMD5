package com.omnichannel.order;

import com.omnichannel.order.entity.Inventory;
import com.omnichannel.order.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedInventory(InventoryRepository inventoryRepository, com.omnichannel.order.lock.RedisLockService redisLockService) {
        return args -> {
            long[][] invDefs = {
                {1L, 10}, {2L, 50}, {3L, 25}, {4L, 100}, {5L, 40},
                {6L, 35}, {7L, 60}, {8L, 80}, {9L, 30}, {10L, 75},
                {11L, 15}, {12L, 45}, {13L, 20}, {14L, 50}
            };
            for (long[] def : invDefs) {
                if (inventoryRepository.findByProductId(def[0]).isEmpty()) {
                    inventoryRepository.save(new Inventory(def[0], (int) def[1]));
                }
            }

            // Always warm up Redis cache on application boot
            inventoryRepository.findAll().forEach(inv -> {
                redisLockService.setStockRedis(inv.getProductId(), inv.getAvailableStock());
            });
            System.out.println(">>> [OrderService] Initialized Inventory & Synced to Redis cache (14 products)!");
        };
    }
}
