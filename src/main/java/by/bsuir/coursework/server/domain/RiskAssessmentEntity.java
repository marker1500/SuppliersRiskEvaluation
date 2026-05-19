package by.bsuir.coursework.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "risk_assessments")
public class RiskAssessmentEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private ContractEntity contract;

    @Column(nullable = false)
    private double riskScore;

    @Column(nullable = false)
    private String riskLevel;

    public RiskAssessmentEntity() {
    }

    public RiskAssessmentEntity(ContractEntity contract, double riskScore, String riskLevel) {
        this.contract = contract;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }
}
