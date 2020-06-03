package ru.hse.erokhina.representation;

public class Parameters {
    private RepresentationType representationType;
    private Integer n;
    private Boolean useContext;

    public Parameters(RepresentationType representationType, Integer n, Boolean useContext) {
        this.representationType = representationType;
        this.n = n;
        this.useContext = useContext;
    }

    public RepresentationType getRepresentationType() {
        return representationType;
    }

    public Integer getN() {
        return n;
    }

    public Boolean getUseContext() {
        return useContext;
    }

    public enum RepresentationType {SHORT_AS_NGRAM, CONCAT};
}
