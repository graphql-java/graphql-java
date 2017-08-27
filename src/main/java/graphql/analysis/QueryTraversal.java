package graphql.analysis;

import graphql.Internal;
import graphql.execution.ConditionalNodes;
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

@Internal
public class QueryTraversal {


    private OperationDefinition operationDefinition;
    private GraphQLSchema schema;
    private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
    private Map<String, Object> variables;

    private ConditionalNodes conditionalNodes = new ConditionalNodes();


    public QueryTraversal(OperationDefinition operationDefinition,
                          GraphQLSchema schema,
                          Map<String, FragmentDefinition> fragmentsByName,
                          Map<String, Object> variables) {
        this.operationDefinition = operationDefinition;
        this.schema = schema;
        this.fragmentsByName = fragmentsByName;
        this.variables = variables;
    }

    public void visitPostOrder(QueryVisitor visitor) {
        visitImpl(visitor, operationDefinition.getSelectionSet(), schema.getQueryType(), null, false);
    }

    public void visitPreOrder(QueryVisitor visitor) {
        visitImpl(visitor, operationDefinition.getSelectionSet(), schema.getQueryType(), null, true);
    }

    public Object reduce(QueryReducer queryReducer, Object initialValue) {
        // compiler hack to make acc final and mutable :-)
        final Object[] acc = {initialValue};
        visitPostOrder((env) -> acc[0] = queryReducer.reduceField(env, acc[0]));
        return acc[0];
    }


    private void visitImpl(QueryVisitor visitor, SelectionSet selectionSet, GraphQLCompositeType type, VisitPath path, boolean preOrder) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) type;
                GraphQLFieldDefinition fieldDefinition = fieldsContainer.getFieldDefinition(((Field) selection).getName());
                visitField(visitor, (Field) selection, fieldDefinition, type, path, preOrder);
            } else if (selection instanceof InlineFragment) {
                visitInlineFragment(visitor, (InlineFragment) selection, type, path, preOrder);
            } else if (selection instanceof FragmentSpread) {
                visitFragmentSpread(visitor, (FragmentSpread) selection, path, preOrder);
            }
        }
    }

    private void visitFragmentSpread(QueryVisitor visitor, FragmentSpread fragmentSpread, VisitPath path, boolean preOrder) {
        if (!conditionalNodes.shouldInclude(this.variables, fragmentSpread.getDirectives())) {
            return;
        }
        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());

        if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives())) {
            return;
        }
        GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
        visitImpl(visitor, fragmentDefinition.getSelectionSet(), typeCondition, path, preOrder);
    }


    private void visitInlineFragment(QueryVisitor visitor, InlineFragment inlineFragment, GraphQLCompositeType parentType, VisitPath path, boolean preOrder) {
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
        visitImpl(visitor, inlineFragment.getSelectionSet(), fragmentCondition, path, preOrder);
    }

    private void visitField(QueryVisitor visitor, Field field, GraphQLFieldDefinition fieldDefinition, GraphQLCompositeType parentType, VisitPath parentPath, boolean preOrder) {
        if (!conditionalNodes.shouldInclude(variables, field.getDirectives())) {
            return;
        }
        if (preOrder) {
            visitor.visitField(new QueryVisitorEnvironment(field, fieldDefinition, parentType, parentPath));
        }
        if (fieldDefinition.getType() instanceof GraphQLCompositeType) {
            VisitPath newPath = new VisitPath(field, fieldDefinition, parentType, parentPath);
            visitImpl(visitor, field.getSelectionSet(), (GraphQLCompositeType) fieldDefinition.getType(), newPath, preOrder);
        }
        if (!preOrder) {
            visitor.visitField(new QueryVisitorEnvironment(field, fieldDefinition, parentType, parentPath));
        }

    }


}
