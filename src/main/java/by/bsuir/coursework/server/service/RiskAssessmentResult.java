package by.bsuir.coursework.server.service;

public record RiskAssessmentResult(double riskScore, String riskLevel, String summaryRu) {

    public static RiskAssessmentResult fromRaw(double rawScore, String summaryRu) {
        double capped = Math.min(100.0, Math.max(0.0, rawScore));
        String level = capped >= 70 ? "HIGH" : (capped >= 40 ? "MEDIUM" : "LOW");
        return new RiskAssessmentResult(capped, level, summaryRu);
    }
}
