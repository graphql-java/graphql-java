package graphql.schema.idl;

import graphql.Internal;
import graphql.language.NamedNode;
import graphql.language.NodeParentTree;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphqlElementParentTree;

import java.util.Map;

@Internal
public class SchemaDirectiveWiringEnvironmentImpl<T extends GraphQLDirectiveContainer> implements SchemaDirectiveWiringEnvironment<T> {

    private final T element;
    private final GraphQLDirective directive;
    private final NodeParentTree<NamedNode> nodeParentTree;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final Map<String, Object> context;
    private final GraphQLCodeRegistry.Builder codeRegistry;
    private final GraphqlElementParentTree elementParentTree;
    private final GraphQLFieldsContainer fieldsContainer;
    private final GraphQLFieldDefinition fieldDefinition;

    public SchemaDirectiveWiringEnvironmentImpl(T element, GraphQLDirective directive, SchemaGeneratorDirectiveHelper.Parameters parameters) {
        this.element = element;
        this.typeDefinitionRegistry = parameters.getTypeRegistry();
        this.directive = directive;
        this.context = parameters.getContext();
        this.codeRegistry = parameters.getCodeRegistry();
        this.nodeParentTree = parameters.getNodeParentTree();
        this.elementParentTree = parameters.getElementParentTree();
        this.fieldsContainer = parameters.getFieldsContainer();
        this.fieldDefinition = parameters.getFieldsDefinition();
    }

    @Override
    public T getElement() {
        return element;
    }

    @Override
    public GraphQLDirective getDirective() {
        return directive;
    }

    @Override
    public NodeParentTree<NamedNode> getNodeParentTree() {
        return nodeParentTree;
    }

    @Override
    public TypeDefinitionRegistry getRegistry() {
        return typeDefinitionRegistry;
    }

    @Override
    public Map<String, Object> getBuildContext() {
        return context;
    }

    @Override
    public GraphQLCodeRegistry.Builder getCodeRegistry() {
        return codeRegistry;
    }

    @Override
    public GraphQLFieldsContainer getFieldsContainer() {
        return fieldsContainer;
    }

    @Override
    public GraphqlElementParentTree getElementParentTree() {
        return elementParentTree;
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }
}
