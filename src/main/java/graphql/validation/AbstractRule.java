package graphql.validation;


import graphql.language.*;

import java.util.List;

/**
 * <p>AbstractRule class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class AbstractRule {

    private final ValidationContext validationContext;
    private final ValidationErrorCollector validationErrorCollector;


    private boolean visitFragmentSpreads;

    private ValidationUtil validationUtil = new ValidationUtil();

    /**
     * <p>Constructor for AbstractRule.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public AbstractRule(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        this.validationContext = validationContext;
        this.validationErrorCollector = validationErrorCollector;
    }

    /**
     * <p>isVisitFragmentSpreads.</p>
     *
     * @return a boolean.
     */
    public boolean isVisitFragmentSpreads() {
        return visitFragmentSpreads;
    }

    /**
     * <p>Setter for the field <code>visitFragmentSpreads</code>.</p>
     *
     * @param visitFragmentSpreads a boolean.
     */
    public void setVisitFragmentSpreads(boolean visitFragmentSpreads) {
        this.visitFragmentSpreads = visitFragmentSpreads;
    }


    /**
     * <p>Getter for the field <code>validationUtil</code>.</p>
     *
     * @return a {@link graphql.validation.ValidationUtil} object.
     */
    public ValidationUtil getValidationUtil() {
        return validationUtil;
    }

    /**
     * <p>Setter for the field <code>validationUtil</code>.</p>
     *
     * @param validationUtil a {@link graphql.validation.ValidationUtil} object.
     */
    public void setValidationUtil(ValidationUtil validationUtil) {
        this.validationUtil = validationUtil;
    }

    /**
     * <p>addError.</p>
     *
     * @param error a {@link graphql.validation.ValidationError} object.
     */
    public void addError(ValidationError error) {
        validationErrorCollector.addError(error);
    }

    /**
     * <p>getErrors.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<ValidationError> getErrors() {
        return validationErrorCollector.getErrors();
    }


    /**
     * <p>Getter for the field <code>validationContext</code>.</p>
     *
     * @return a {@link graphql.validation.ValidationContext} object.
     */
    public ValidationContext getValidationContext() {
        return validationContext;
    }

    /**
     * <p>checkArgument.</p>
     *
     * @param argument a {@link graphql.language.Argument} object.
     */
    public void checkArgument(Argument argument) {

    }

    /**
     * <p>checkTypeName.</p>
     *
     * @param typeName a {@link graphql.language.TypeName} object.
     */
    public void checkTypeName(TypeName typeName) {

    }

    /**
     * <p>checkVariableDefinition.</p>
     *
     * @param variableDefinition a {@link graphql.language.VariableDefinition} object.
     */
    public void checkVariableDefinition(VariableDefinition variableDefinition) {

    }

    /**
     * <p>checkField.</p>
     *
     * @param field a {@link graphql.language.Field} object.
     */
    public void checkField(Field field) {

    }

    /**
     * <p>checkInlineFragment.</p>
     *
     * @param inlineFragment a {@link graphql.language.InlineFragment} object.
     */
    public void checkInlineFragment(InlineFragment inlineFragment) {

    }

    /**
     * <p>checkDirective.</p>
     *
     * @param directive a {@link graphql.language.Directive} object.
     * @param ancestors a {@link java.util.List} object.
     */
    public void checkDirective(Directive directive, List<Node> ancestors) {

    }

    /**
     * <p>checkFragmentSpread.</p>
     *
     * @param fragmentSpread a {@link graphql.language.FragmentSpread} object.
     */
    public void checkFragmentSpread(FragmentSpread fragmentSpread) {

    }

    /**
     * <p>checkFragmentDefinition.</p>
     *
     * @param fragmentDefinition a {@link graphql.language.FragmentDefinition} object.
     */
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {

    }

    /**
     * <p>checkOperationDefinition.</p>
     *
     * @param operationDefinition a {@link graphql.language.OperationDefinition} object.
     */
    public void checkOperationDefinition(OperationDefinition operationDefinition) {

    }

    /**
     * <p>leaveOperationDefinition.</p>
     *
     * @param operationDefinition a {@link graphql.language.OperationDefinition} object.
     */
    public void leaveOperationDefinition(OperationDefinition operationDefinition) {

    }

    /**
     * <p>checkSelectionSet.</p>
     *
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public void checkSelectionSet(SelectionSet selectionSet) {

    }

    /**
     * <p>leaveSelectionSet.</p>
     *
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public void leaveSelectionSet(SelectionSet selectionSet) {

    }

    /**
     * <p>checkVariable.</p>
     *
     * @param variableReference a {@link graphql.language.VariableReference} object.
     */
    public void checkVariable(VariableReference variableReference) {

    }

    /**
     * <p>documentFinished.</p>
     *
     * @param document a {@link graphql.language.Document} object.
     */
    public void documentFinished(Document document) {

    }


}
