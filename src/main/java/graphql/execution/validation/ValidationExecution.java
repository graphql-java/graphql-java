package graphql.execution.validation;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.execution.validation.ValidationResult.CONTINUE_RESULT;
import static graphql.execution.validation.ValidationResult.Instruction;
import static graphql.execution.validation.ValidationResult.Instruction.CONTINUE_FETCHING;
import static graphql.execution.validation.ValidationResult.Instruction.RETURN_NULL;
import static graphql.execution.validation.ValidationResult.newResult;

@Internal
public class ValidationExecution {

    public ValidationResult validateField(DataFetchingEnvironment environment) {

        boolean seenReturnNull = false;
        GraphQLCodeRegistry codeRegistry = environment.getGraphQLSchema().getCodeRegistry();
        if (!codeRegistry.hasAnyValidationRules()) {
            return CONTINUE_RESULT;
        }

        List<GraphQLError> allErrors = new ArrayList<>();
        Map<Object, Object> context = new LinkedHashMap<>();

        GraphQLFieldDefinition fieldDefinition = environment.getFieldDefinition();
        GraphQLFieldsContainer parentType = (GraphQLFieldsContainer) environment.getParentType();
        List<ValidationRule> perFieldRules = codeRegistry.getFieldValidationRules(parentType, fieldDefinition);

        for (GraphQLArgument fieldArg : fieldDefinition.getArguments()) {
            List<ValidationRule> perArgRules = codeRegistry.getInputTypeValidationRules(fieldArg.getType());

            Instruction instruction = runRules(context, fieldArg, perArgRules, environment, allErrors);
            if (instruction == RETURN_NULL) {
                seenReturnNull = true;
            }
        }

        Instruction instruction = runRules(context, null, perFieldRules, environment, allErrors);
        if (instruction == RETURN_NULL) {
            seenReturnNull = true;
        }

        return newResult()
                .withErrors(allErrors)
                .instruction(seenReturnNull ? RETURN_NULL : CONTINUE_FETCHING).build();
    }

    private Instruction runRules(Map<Object, Object> context, GraphQLArgument fieldArg, List<ValidationRule> rulesToRun, DataFetchingEnvironment environment, List<GraphQLError> allErrors) {
        if (rulesToRun.isEmpty()) {
            return Instruction.CONTINUE_FETCHING;
        }
        boolean seenReturnNull = false;

        ValidationRuleEnvironment validationRuleEnvironment = ValidationRuleEnvironmentImpl.newRuleEnvironment()
                .dataFetchingEnvironment(environment)
                .validatedArgument(fieldArg)
                .context(context)
                .build();

        for (ValidationRule validationRule : rulesToRun) {
            ValidationResult result = validationRule.validate(validationRuleEnvironment);
            allErrors.addAll(result.getErrors());
            if (result.getInstruction() == RETURN_NULL) {
                seenReturnNull = true;
            }
        }

        return seenReturnNull ? Instruction.RETURN_NULL : Instruction.CONTINUE_FETCHING;
    }

}
