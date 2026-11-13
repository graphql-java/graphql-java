package graphql.performance.page.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrimaryMetric {
    private double score;
    private double scoreError;
    private String scoreUnit;
    private List<Double> scoreConfidence;

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getScoreError() {
        return scoreError;
    }

    public void setScoreError(double scoreError) {
        this.scoreError = scoreError;
    }

    public String getScoreUnit() {
        return scoreUnit;
    }

    public void setScoreUnit(String scoreUnit) {
        this.scoreUnit = scoreUnit;
    }

    public List<Double> getScoreConfidence() {
        return scoreConfidence;
    }

    public void setScoreConfidence(List<Double> scoreConfidence) {
        this.scoreConfidence = scoreConfidence;
    }
}
