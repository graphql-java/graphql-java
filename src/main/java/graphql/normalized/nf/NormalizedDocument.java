package graphql.normalized.nf;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class NormalizedDocument {

    private final List<NormalizedOperationWithAssumedSkipIncludeVariables> normalizedOperations;

    public NormalizedDocument(List<NormalizedOperationWithAssumedSkipIncludeVariables> normalizedOperations) {
        this.normalizedOperations = normalizedOperations;
    }

    public List<NormalizedOperationWithAssumedSkipIncludeVariables> getNormalizedOperations() {
        return normalizedOperations;
    }

    public static class NormalizedOperationWithAssumedSkipIncludeVariables {

        Map<String, Boolean> assumedSkipIncludeVariables;
        NormalizedOperation normalizedOperation;

        public NormalizedOperationWithAssumedSkipIncludeVariables(@Nullable Map<String, Boolean> assumedSkipIncludeVariables, NormalizedOperation normalizedOperation) {
            this.assumedSkipIncludeVariables = assumedSkipIncludeVariables;
            this.normalizedOperation = normalizedOperation;
        }

        public Map<String, Boolean> getAssumedSkipIncludeVariables() {
            return assumedSkipIncludeVariables;
        }

        public NormalizedOperation getNormalizedOperation() {
            return normalizedOperation;
        }
    }
}

