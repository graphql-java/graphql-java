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

import java.util.LinkedHashMap;
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
        visit(operationDefinition.getSelectionSet(), schema.getQueryType());
    }


    private void visit(SelectionSet selectionSet, GraphQLCompositeType type) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) type;
                GraphQLFieldDefinition fieldDefinition = fieldsContainer.getFieldDefinition(((Field) selection).getName());
                visitField((Field) selection, fieldDefinition);
            } else if (selection instanceof InlineFragment) {
                visitInlineFragment((InlineFragment) selection, type);
            } else if (selection instanceof FragmentSpread) {
                visitFragmentSpread((FragmentSpread) selection);
            }
        }
    }

    private void visitFragmentSpread(FragmentSpread fragmentSpread) {
        if (!conditionalNodes.shouldInclude(this.variables, fragmentSpread.getDirectives())) {
            return;
        }
        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());

        if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives())) {
            return;
        }
        GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
        visit(fragmentDefinition.getSelectionSet(), typeCondition);
    }


    private void visitInlineFragment(InlineFragment inlineFragment, GraphQLCompositeType parentType) {
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
        visit(inlineFragment.getSelectionSet(), fragmentCondition);
    }

    private void visitField(Field field, GraphQLFieldDefinition fieldDefinition) {
        if (!conditionalNodes.shouldInclude(variables, field.getDirectives())) {
            return;
        }
        visitor.visitField(field, fieldDefinition);
        if (fieldDefinition.getType() instanceof GraphQLCompositeType) {
            visit(field.getSelectionSet(), (GraphQLCompositeType) fieldDefinition.getType());
        }
    }


}
