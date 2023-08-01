package graphql.validation.rules;


import graphql.Internal;
import graphql.execution.TypeFromAST;
import graphql.execution.ValuesResolver;
import graphql.language.OperationDefinition;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.InputValueWithState;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static graphql.validation.ValidationErrorType.VariableTypeMismatch;

@Internal
public class VariableTypesMatch extends AbstractRule {

    final VariablesTypesMatcher variablesTypesMatcher;

    private Map<String, VariableDefinition> variableDefinitionMap;

    public VariableTypesMatch(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        this(validationContext, validationErrorCollector, new VariablesTypesMatcher());
    }

    VariableTypesMatch(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector, VariablesTypesMatcher variablesTypesMatcher) {
        super(validationContext, validationErrorCollector);
        setVisitFragmentSpreads(true);
        this.variablesTypesMatcher = variablesTypesMatcher;
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        variableDefinitionMap = new LinkedHashMap<>();
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        variableDefinitionMap.put(variableDefinition.getName(), variableDefinition);
    }

    @Override
    public void checkVariable(VariableReference variableReference) {
        VariableDefinition variableDefinition = variableDefinitionMap.get(variableReference.getName());
        if (variableDefinition == null) {
            return;
        }
        GraphQLType variableType = TypeFromAST.getTypeFromAST(getValidationContext().getSchema(), variableDefinition.getType());
        if (variableType == null) {
            return;
        }
        GraphQLInputType expectedType = getValidationContext().getInputType();
        if (expectedType == null) {
            // we must have an unknown variable say to not have a known type
            return;
        }
        GraphQLArgument argument = getValidationContext().getArgument();
        Optional<InputValueWithState> schemaDefault = Optional.ofNullable(argument).map(v -> v.getArgumentDefaultValue());
        Value<?> schemaDefaultValue = null;
        if (schemaDefault.isPresent() && schemaDefault.get().isLiteral()) {
            schemaDefaultValue = (Value<?>) schemaDefault.get().getValue();
        } else if (schemaDefault.isPresent() && schemaDefault.get().isSet()) {
            schemaDefaultValue = ValuesResolver.valueToLiteral(schemaDefault.get(), argument.getType(), getValidationContext().getGraphQLContext(), getValidationContext().getI18n().getLocale());
        }
        if (!variablesTypesMatcher.doesVariableTypesMatch(variableType, variableDefinition.getDefaultValue(), expectedType) &&
                !variablesTypesMatcher.doesVariableTypesMatch(variableType, schemaDefaultValue, expectedType)) {
            GraphQLType effectiveType = variablesTypesMatcher.effectiveType(variableType, variableDefinition.getDefaultValue());
            String message = i18n(VariableTypeMismatch, "VariableTypesMatchRule.unexpectedType",
                    GraphQLTypeUtil.simplePrint(effectiveType),
                    GraphQLTypeUtil.simplePrint(expectedType));
            addError(VariableTypeMismatch, variableReference.getSourceLocation(), message);
        }
    }
}
