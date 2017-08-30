package graphql.analysis;

import graphql.Internal;
import graphql.execution.ConditionalNodes;
import graphql.execution.ValuesResolver;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;

@Internal
public class QueryTraversal {


    private OperationDefinition operationDefinition;
    private GraphQLSchema schema;
    private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
    private Map<String, Object> variables;

    private ConditionalNodes conditionalNodes = new ConditionalNodes();

    private ValuesResolver valuesResolver = new ValuesResolver();


    public QueryTraversal(GraphQLSchema schema,
                          Document document,
                          String operation,
                          Map<String, Object> variables) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operation);
        this.operationDefinition = getOperationResult.operationDefinition;
        this.fragmentsByName = getOperationResult.fragmentsByName;
        this.schema = schema;
        this.variables = variables;
    }

    public void visitPostOrder(QueryVisitor visitor) {
        visitImpl(visitor, operationDefinition.getSelectionSet(), getRootType(), null, false);
    }

    public void visitPreOrder(QueryVisitor visitor) {
        visitImpl(visitor, operationDefinition.getSelectionSet(), getRootType(), null, true);
    }

    private GraphQLObjectType getRootType() {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            return assertNotNull(schema.getMutationType());
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            return assertNotNull(schema.getQueryType());
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
            return assertNotNull(schema.getSubscriptionType());
        } else {
            return assertShouldNeverHappen();
        }
    }

    public <T> T reducePostOrder(QueryReducer<T> queryReducer, T initialValue) {
        // compiler hack to make acc final and mutable :-)
        final Object[] acc = {initialValue};
        visitPostOrder((env) -> acc[0] = queryReducer.reduceField(env, (T) acc[0]));
        return (T) acc[0];
    }

    public <T> T reducePreOrder(QueryReducer<T> queryReducer, T initialValue) {
        // compiler hack to make acc final and mutable :-)
        final Object[] acc = {initialValue};
        visitPreOrder((env) -> acc[0] = queryReducer.reduceField(env, (T) acc[0]));
        return (T) acc[0];
    }


    private void visitImpl(QueryVisitor visitor, SelectionSet selectionSet, GraphQLCompositeType type, QueryVisitorEnvironment parent, boolean preOrder) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) type;
                GraphQLFieldDefinition fieldDefinition = fieldsContainer.getFieldDefinition(((Field) selection).getName());
                visitField(visitor, (Field) selection, fieldDefinition, type, parent, preOrder);
            } else if (selection instanceof InlineFragment) {
                visitInlineFragment(visitor, (InlineFragment) selection, type, parent, preOrder);
            } else if (selection instanceof FragmentSpread) {
                visitFragmentSpread(visitor, (FragmentSpread) selection, parent, preOrder);
            }
        }
    }

    private void visitFragmentSpread(QueryVisitor visitor, FragmentSpread fragmentSpread, QueryVisitorEnvironment parent, boolean preOrder) {
        if (!conditionalNodes.shouldInclude(this.variables, fragmentSpread.getDirectives())) {
            return;
        }
        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());

        if (!conditionalNodes.shouldInclude(variables, fragmentDefinition.getDirectives())) {
            return;
        }
        GraphQLCompositeType typeCondition = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
        visitImpl(visitor, fragmentDefinition.getSelectionSet(), typeCondition, parent, preOrder);
    }


    private void visitInlineFragment(QueryVisitor visitor, InlineFragment inlineFragment, GraphQLCompositeType parentType, QueryVisitorEnvironment parent, boolean preOrder) {
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
        visitImpl(visitor, inlineFragment.getSelectionSet(), fragmentCondition, parent, preOrder);
    }

    private void visitField(QueryVisitor visitor, Field field, GraphQLFieldDefinition fieldDefinition, GraphQLCompositeType parentType, QueryVisitorEnvironment parentEnv, boolean preOrder) {
        if (!conditionalNodes.shouldInclude(variables, field.getDirectives())) {
            return;
        }
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDefinition.getArguments(), field.getArguments(), variables);
        if (preOrder) {
            visitor.visitField(new QueryVisitorEnvironment(field, fieldDefinition, parentType, parentEnv, argumentValues));
        }
        if (fieldDefinition.getType() instanceof GraphQLCompositeType) {
            QueryVisitorEnvironment newParentEnvironment = new QueryVisitorEnvironment(field, fieldDefinition, parentType, parentEnv, argumentValues);
            visitImpl(visitor, field.getSelectionSet(), (GraphQLCompositeType) fieldDefinition.getType(), newParentEnvironment, preOrder);
        }
        if (!preOrder) {
            visitor.visitField(new QueryVisitorEnvironment(field, fieldDefinition, parentType, parentEnv, argumentValues));
        }

    }


}
