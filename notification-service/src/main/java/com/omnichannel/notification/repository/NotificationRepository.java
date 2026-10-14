package com.omnichannel.notification.repository;

import com.omnichannel.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findTop50ByOrderByCreatedAtDesc();
    List<NotificationLog> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);
}
