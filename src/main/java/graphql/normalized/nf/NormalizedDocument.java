package graphql.normalized.nf;

import java.util.List;

public class NormalizedDocument {

    private final List<NormalizedOperation> normalizedOperations;

    public NormalizedDocument(List<NormalizedOperation> normalizedOperations) {
        this.normalizedOperations = normalizedOperations;
    }

    public List<NormalizedOperation> getNormalizedOperations() {
        return normalizedOperations;
    }
}

