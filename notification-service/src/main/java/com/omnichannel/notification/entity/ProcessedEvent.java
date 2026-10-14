package com.omnichannel.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_key", nullable = false, unique = true)
    private String eventKey;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent() {
        this.processedAt = LocalDateTime.now();
    }

    public ProcessedEvent(String eventKey) {
        this.eventKey = eventKey;
        this.processedAt = LocalDateTime.now();
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
