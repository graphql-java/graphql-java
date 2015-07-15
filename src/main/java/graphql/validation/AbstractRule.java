package graphql.validation;


import graphql.language.*;

import java.util.List;

public class AbstractRule {

    private final ValidationContext validationContext;
    private final ErrorCollector errorCollector;

    private ValidationUtil validationUtil = new ValidationUtil();

    public AbstractRule(ValidationContext validationContext, ErrorCollector errorCollector) {
        this.validationContext = validationContext;
        this.errorCollector = errorCollector;
    }


    public ValidationUtil getValidationUtil() {
        return validationUtil;
    }

    public void setValidationUtil(ValidationUtil validationUtil) {
        this.validationUtil = validationUtil;
    }

    public void addError(ValidationError error){
        errorCollector.addError(error);
    }

    public List<ValidationError> getErrors(){
        return errorCollector.getErrors();
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
