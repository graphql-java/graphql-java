package graphql.execution.validation;

import graphql.GraphQLError;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class ValidationResult {

    /**
     * This constant represents no errors and hence fetching should continue
     */
    public static final ValidationResult CONTINUE_RESULT = new ValidationResult(Instruction.CONTINUE_FETCHING, Collections.emptyList());

    public enum Instruction {
        /**
         * The graphql system should continue fetching values for the field
         */
        CONTINUE_FETCHING,
        /**
         * The graphql system should make the field null and not fetch values for it.  Note this can still fail for non null types.
         */
        RETURN_NULL
    }

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

        public Builder withErrors(GraphQLError... errrors) {
            Collections.addAll(this.errors, errrors);
            return this;
        }

        /**
         * Combines the given validattion result into the current one and IF it returns
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

        public Builder withErrors(List<GraphQLError> errrors) {
            this.errors.addAll(errrors);
            return this;
        }

        /**
         * @return a CONTINUE result if there no errors in the result
         */
        public ValidationResult continueIfNoErrors() {
            return new ValidationResult(hasErrors() ? Instruction.RETURN_NULL : Instruction.CONTINUE_FETCHING, errors);
        }

        public ValidationResult build() {
            return new ValidationResult(instruction, errors);
        }
    }

}
