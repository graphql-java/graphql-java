package graphql.validation.rules;


import graphql.Internal;
import graphql.execution.TypeFromAST;
import graphql.execution.ValuesResolver;
import graphql.language.OperationDefinition;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
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
        GraphQLInputType locationType = getValidationContext().getInputType();
        Optional<InputValueWithState> locationDefault = Optional.ofNullable(getValidationContext().getDefaultValue());
        if (locationType == null) {
            // we must have an unknown variable say to not have a known type
            return;
        }
        Value<?> locationDefaultValue = null;
        if (locationDefault.isPresent() && locationDefault.get().isLiteral()) {
            locationDefaultValue = (Value<?>) locationDefault.get().getValue();
        } else if (locationDefault.isPresent() && locationDefault.get().isSet()) {
            locationDefaultValue = ValuesResolver.valueToLiteral(locationDefault.get(), locationType, getValidationContext().getGraphQLContext(), getValidationContext().getI18n().getLocale());
        }
        boolean variableDefMatches = variablesTypesMatcher.doesVariableTypesMatch(variableType, variableDefinition.getDefaultValue(), locationType, locationDefaultValue);
        if (!variableDefMatches) {
            GraphQLType effectiveType = variablesTypesMatcher.effectiveType(variableType, variableDefinition.getDefaultValue());
            String message = i18n(VariableTypeMismatch, "VariableTypesMatchRule.unexpectedType",
                    variableDefinition.getName(),
                    GraphQLTypeUtil.simplePrint(effectiveType),
                    GraphQLTypeUtil.simplePrint(locationType));
            addError(VariableTypeMismatch, variableReference.getSourceLocation(), message);
        }
    }
}
