package com.hostlify.module.hostel.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "hostel_amenities")
public class HostelAmenity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    // Serialized as "key" to match the web client ({ key, label, enabled })
    @JsonProperty("key")
    @Column(name = "amenity_key", nullable = false, length = 50)
    private String amenityKey;

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** Human-readable label derived from the key, e.g. "power_backup" -> "Power Backup". */
    @JsonProperty("label")
    public String getLabel() {
        if (amenityKey == null || amenityKey.isBlank()) return amenityKey;
        StringBuilder sb = new StringBuilder();
        for (String part : amenityKey.split("[_\\s]+")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.length() <= 2 || part.equalsIgnoreCase("cctv") ? part.toUpperCase()
                    : Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
