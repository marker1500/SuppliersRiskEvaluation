package by.bsuir.coursework.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "shipments")
public class ShipmentEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private ContractEntity contract;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDate plannedDate;

    @Column
    private LocalDate actualDate;

    public ShipmentEntity() {
    }

    public ShipmentEntity(ContractEntity contract, String status, LocalDate plannedDate, LocalDate actualDate) {
        this.contract = contract;
        this.status = status;
        this.plannedDate = plannedDate;
        this.actualDate = actualDate;
    }

    public ContractEntity getContract() {
        return contract;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public LocalDate getActualDate() {
        return actualDate;
    }
}
