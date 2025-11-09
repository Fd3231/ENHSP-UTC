package models;

public class Flow {

    private String sourceLink;
    private String targetLink;
    private Float value;

    public Flow(String sourceLink, String targetLink, Float value) {
        this.sourceLink = sourceLink;
        this.targetLink = targetLink;
        this.value = value;
    }

    public String getSourceLink() {
        return sourceLink;
    }

    public String getTargetLink() {
        return targetLink;
    }

    public Float getValue() {
        return value;
    }

}