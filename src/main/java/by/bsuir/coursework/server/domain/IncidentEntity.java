package by.bsuir.coursework.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "incidents")
public class IncidentEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private ShipmentEntity shipment;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean escalated;

    public IncidentEntity() {
    }

    public IncidentEntity(ShipmentEntity shipment, String severity, String description) {
        this.shipment = shipment;
        this.severity = severity;
        this.description = description;
        this.escalated = false;
    }

    public void escalate() {
        this.escalated = true;
    }

    public boolean isEscalated() {
        return escalated;
    }
}
