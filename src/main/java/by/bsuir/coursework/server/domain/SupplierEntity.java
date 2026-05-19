package by.bsuir.coursework.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class SupplierEntity extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private double reliabilityScore;

    public SupplierEntity() {
    }

    public SupplierEntity(String name, double reliabilityScore) {
        this.name = name;
        this.reliabilityScore = reliabilityScore;
    }

    public String getName() {
        return name;
    }

    public double getReliabilityScore() {
        return reliabilityScore;
    }
}
