package graphql.validation;


import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.schema.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>ValidationContext class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ValidationContext {

    private final GraphQLSchema schema;
    private final Document document;

    private TraversalContext traversalContext;
    private final Map<String, FragmentDefinition> fragmentDefinitionMap = new LinkedHashMap<String, FragmentDefinition>();


    /**
     * <p>Constructor for ValidationContext.</p>
     *
     * @param schema a {@link graphql.schema.GraphQLSchema} object.
     * @param document a {@link graphql.language.Document} object.
     */
    public ValidationContext(GraphQLSchema schema, Document document) {
        this.schema = schema;
        this.document = document;
        this.traversalContext = new TraversalContext(schema);
        buildFragmentMap();
    }

    private void buildFragmentMap() {
        for (Definition definition : document.getDefinitions()) {
            if (!(definition instanceof FragmentDefinition)) continue;
            FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
            fragmentDefinitionMap.put(fragmentDefinition.getName(), fragmentDefinition);
        }
    }

    /**
     * <p>Getter for the field <code>traversalContext</code>.</p>
     *
     * @return a {@link graphql.validation.TraversalContext} object.
     */
    public TraversalContext getTraversalContext() {
        return traversalContext;
    }

    /**
     * <p>Getter for the field <code>schema</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLSchema} object.
     */
    public GraphQLSchema getSchema() {
        return schema;
    }

    /**
     * <p>Getter for the field <code>document</code>.</p>
     *
     * @return a {@link graphql.language.Document} object.
     */
    public Document getDocument() {
        return document;
    }

    /**
     * <p>getFragment.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @return a {@link graphql.language.FragmentDefinition} object.
     */
    public FragmentDefinition getFragment(String name) {
        return fragmentDefinitionMap.get(name);
    }

    /**
     * <p>getParentType.</p>
     *
     * @return a {@link graphql.schema.GraphQLCompositeType} object.
     */
    public GraphQLCompositeType getParentType() {
        return traversalContext.getParentType();
    }

    /**
     * <p>getInputType.</p>
     *
     * @return a {@link graphql.schema.GraphQLInputType} object.
     */
    public GraphQLInputType getInputType() {
        return traversalContext.getInputType();
    }

    /**
     * <p>getFieldDef.</p>
     *
     * @return a {@link graphql.schema.GraphQLFieldDefinition} object.
     */
    public GraphQLFieldDefinition getFieldDef() {
        return traversalContext.getFieldDef();
    }

    /**
     * <p>getDirective.</p>
     *
     * @return a {@link graphql.schema.GraphQLDirective} object.
     */
    public GraphQLDirective getDirective() {
        return traversalContext.getDirective();
    }

    /**
     * <p>getArgument.</p>
     *
     * @return a {@link graphql.schema.GraphQLArgument} object.
     */
    public GraphQLArgument getArgument() {
        return traversalContext.getArgument();
    }

    /**
     * <p>getOutputType.</p>
     *
     * @return a {@link graphql.schema.GraphQLOutputType} object.
     */
    public GraphQLOutputType getOutputType() {
        return traversalContext.getOutputType();
    }


}
