package graphql.execution.validation;

import graphql.GraphQLError;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link graphql.execution.validation.ValidationRule} will return a {@link graphql.execution.validation.ValidationResult} as its output
 * which may contain validation errors and instructions on how to proceed with field data fetching.
 */
@PublicApi
public class ValidationResult {

    /**
     * Validation happens before field values are fetched and this enumeration controls how data fetching should
     * proceed in the event of any validation errors
     */
    public enum Instruction {
        /**
         * The graphql system should continue fetching values for the field even if there are validation errors recorded.
         */
        CONTINUE_FETCHING,
        /**
         * The graphql system should not continue fetching values for the field and instead should return a null value.
         */
        RETURN_NULL
    }

    /**
     * This constant represents no errors and hence fetching should continue
     */
    public static final ValidationResult CONTINUE_RESULT = new ValidationResult(Instruction.CONTINUE_FETCHING, Collections.emptyList());

    private final Instruction instruction;
    private final List<GraphQLError> errors;

    private ValidationResult(Instruction instruction, List<GraphQLError> errors) {
        this.instruction = instruction;
        this.errors = errors;
    }

    public Instruction getInstruction() {
        return instruction;
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }

    public static Builder newResult() {
        return new ValidationResult.Builder();
    }

    public static class Builder {
        private Instruction instruction = Instruction.CONTINUE_FETCHING;
        private List<GraphQLError> errors = new ArrayList<>();


        /**
         * @return true if any errors have been put in the result
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public Builder instruction(Instruction instruction) {
            this.instruction = instruction;
            return this;
        }

        public Builder withErrors(GraphQLError... errors) {
            Collections.addAll(this.errors, errors);
            return this;
        }

        public Builder withErrors(List<GraphQLError> errors) {
            this.errors.addAll(errors);
            return this;
        }

        /**
         * Combines the given validation result into the current one and IF it contains
         * the {@link graphql.execution.validation.ValidationResult.Instruction#RETURN_NULL} instruction then
         * this will be transferred as well.
         *
         * @param validationResult the result to combine
         *
         * @return this builder
         */
        public Builder withResult(ValidationResult validationResult) {
            this.errors.addAll(validationResult.getErrors());
            if (validationResult.getInstruction() == Instruction.RETURN_NULL) {
                instruction(Instruction.RETURN_NULL);
            }
            return this;
        }

        /**
         * @return a CONTINUE result if there no errors in the result
         */
        public ValidationResult continueIfNoErrors() {
            return !hasErrors() ? CONTINUE_RESULT : new ValidationResult(Instruction.RETURN_NULL, errors);
        }

        public ValidationResult build() {
            return new ValidationResult(instruction, errors);
        }
    }

}
