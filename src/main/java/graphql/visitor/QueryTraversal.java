package graphql.visitor;

import graphql.execution.ConditionalNodes;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryTraversal {


    private Document document;
    private OperationDefinition operationDefinition;
    private GraphQLSchema schema;
    private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
    private Map<String, Object> variables;

    private ConditionalNodes conditionalNodes = new ConditionalNodes();

    private QueryVisitor visitor;

    public QueryTraversal(Document document,
                          OperationDefinition operationDefinition,
                          GraphQLSchema schema,
                          Map<String, FragmentDefinition> fragmentsByName,
                          Map<String, Object> variables,
                          QueryVisitor visitor) {
        this.document = document;
        this.operationDefinition = operationDefinition;
        this.schema = schema;
        this.fragmentsByName = fragmentsByName;
        this.variables = variables;
        this.visitor = visitor;
    }

    public void traverse() {
        visit(operationDefinition.getSelectionSet(), new ArrayList<>(), schema.getQueryType());
    }


    private void visit(SelectionSet selectionSet, List<String> visitedFragments, GraphQLCompositeType type) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) type;
                GraphQLFieldDefinition fieldDefinition = fieldsContainer.getFieldDefinition(((Field) selection).getName());
                visitField((Field) selection, visitedFragments, fieldDefinition);
            } else if (selection instanceof InlineFragment) {
                visitInlineFragment(visitedFragments, (InlineFragment) selection, type);
            } else if (selection instanceof FragmentSpread) {
                visitFragmentSpread(visitedFragments, (FragmentSpread) selection);
            }
        }
    }

    private void visitFragmentSpread(List<String> visitedFragments, FragmentSpread fragmentSpread) {
        if (visitedFragments.contains(fragmentSpread.getName())) {
            return;
        }
        if (!conditionalNodes.shouldInclude(this.variables, fragmentSpread.getDirectives())) {
            return;
        }
//        visitedFragments.add(fragmentSpread.getName());
        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());

        if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives())) {
            return;
        }
        GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
        visit(fragmentDefinition.getSelectionSet(), visitedFragments, typeCondition);
    }


    private void visitInlineFragment(List<String> visitedFragments, InlineFragment inlineFragment, GraphQLCompositeType parentType) {
        if (!conditionalNodes.shouldInclude(variables, inlineFragment.getDirectives())) {
            return;
        }
        // inline fragments are allowed not have type conditions, if so the parent type counts
        GraphQLCompositeType fragmentCondition;
        if (inlineFragment.getTypeCondition() != null) {
            TypeName typeCondition = inlineFragment.getTypeCondition();
            fragmentCondition = (GraphQLCompositeType) schema.getType(typeCondition.getName());
        } else {
            fragmentCondition = parentType;
        }
        // for unions we only have other fragments inside
        visit(inlineFragment.getSelectionSet(), visitedFragments, fragmentCondition);
    }

    private void visitField(Field field, List<String> visitedFragments, GraphQLFieldDefinition fieldDefinition) {
        if (!conditionalNodes.shouldInclude(variables, field.getDirectives())) {
            return;
        }
        visitor.visitField(field, fieldDefinition);
        if (fieldDefinition.getType() instanceof GraphQLCompositeType) {
            visit(field.getSelectionSet(), visitedFragments, (GraphQLCompositeType) fieldDefinition.getType());
        }
    }


}
