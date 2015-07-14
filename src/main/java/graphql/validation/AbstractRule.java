package graphql.validation;


import graphql.language.*;

public class AbstractRule {

    private ValidationContext validationContext;

    private ValidationUtil validationUtil = new ValidationUtil();

    public ValidationUtil getValidationUtil() {
        return validationUtil;
    }

    public void setValidationUtil(ValidationUtil validationUtil) {
        this.validationUtil = validationUtil;
    }

    public void setValidationContext(ValidationContext validationContext) {
        this.validationContext = validationContext;
    }

    public ValidationContext getValidationContext() {
        return validationContext;
    }

    public void checkArgument(Argument argument) {

    }

    public void checkTypeName(TypeName typeName) {

    }

    public void checkVariableDefinition(VariableDefinition variableDefinition) {

    }

    public void checkField(Field field) {

    }

    public void checkInlineFragment(InlineFragment inlineFragment) {

    }

    public void checkDirective(Directive directive) {

    }

    public void checkFragmentSpread(FragmentSpread fragmentSpread) {

    }

    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {

    }

    public void checkOperationDefinition(OperationDefinition operationDefinition) {

    }

    public void checkSelectionSet(SelectionSet selectionSet) {

    }

    public void checkVariable(VariableReference variableReference) {

    }


}
