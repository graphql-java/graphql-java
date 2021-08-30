package graphql.execution.instrumentation.parameters;


import graphql.PublicApi;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.MergedField;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.SelectionSet;

import java.util.Map;
import java.util.Set;

@PublicApi
public class InstrumentationCollectFieldsParameters {
    private final FieldCollectorParameters fieldCollectorParameters;
    private final SelectionSet selectionSet;
    private final Set<String> visitedFragments;
    private final Map<String, MergedField> fields;
    private final InstrumentationState instrumentationState;

    public InstrumentationCollectFieldsParameters(FieldCollectorParameters fieldCollectorParameters, SelectionSet selectionSet,
                                                  Set<String> visitedFragments, Map<String, MergedField> fields, InstrumentationState instrumentationState) {
        this.fieldCollectorParameters = fieldCollectorParameters;
        this.selectionSet = selectionSet;
        this.visitedFragments = visitedFragments;
        this.fields = fields;
        this.instrumentationState = instrumentationState;
    }

    public FieldCollectorParameters getFieldCollectorParameters() {
        return fieldCollectorParameters;
    }

    public SelectionSet getSelectionSet() {
        return selectionSet;
    }

    public Set<String> getVisitedFragments() {
        return visitedFragments;
    }

    public Map<String, MergedField> getFields() {
        return fields;
    }

    public InstrumentationState getInstrumentationState() {
        return instrumentationState;
    }

    public InstrumentationCollectFieldsParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationCollectFieldsParameters(
                this.fieldCollectorParameters, this.selectionSet, this.visitedFragments, this.fields, instrumentationState);
    }
}
