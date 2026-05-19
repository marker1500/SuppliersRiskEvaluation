package by.bsuir.coursework.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "contracts")
public class ContractEntity extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String contractNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierEntity supplier;

    @Column(nullable = false)
    private LocalDate dueDate;

    /** Сумма контракта (денежная), для справки. */
    @Column(nullable = false)
    private double amount;

    /** Объём заказа в единицах товара — важен для расчёта серьёзности риска. */
    @Column(nullable = false, name = "quantity_units")
    private long quantityUnits;

    public ContractEntity() {
    }

    public ContractEntity(String contractNumber, SupplierEntity supplier, LocalDate dueDate, double amount, long quantityUnits) {
        this.contractNumber = contractNumber;
        this.supplier = supplier;
        this.dueDate = dueDate;
        this.amount = amount;
        this.quantityUnits = quantityUnits;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public SupplierEntity getSupplier() {
        return supplier;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public double getAmount() {
        return amount;
    }

    public long getQuantityUnits() {
        return quantityUnits;
    }
}
