package graphql.normalized.nf;

import graphql.Assert;
import graphql.ExperimentalApi;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@ExperimentalApi
public class NormalizedDocument {

    private final List<NormalizedOperationWithAssumedSkipIncludeVariables> normalizedOperations;

    public NormalizedDocument(List<NormalizedOperationWithAssumedSkipIncludeVariables> normalizedOperations) {
        this.normalizedOperations = normalizedOperations;
    }

    public List<NormalizedOperationWithAssumedSkipIncludeVariables> getNormalizedOperations() {
        return normalizedOperations;
    }

    public NormalizedOperation getSingleNormalizedOperation() {
        Assert.assertTrue(normalizedOperations.size() == 1, "Expecting a single normalized operation");
        return normalizedOperations.get(0).getNormalizedOperation();
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

