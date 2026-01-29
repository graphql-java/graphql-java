package graphql.validation;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Internal
@NullMarked
public class ValidationContext {

    private final GraphQLSchema schema;
    private final Document document;

    private final TraversalContext traversalContext;
    private final Map<String, FragmentDefinition> fragmentDefinitionMap = new LinkedHashMap<>();
    private final I18n i18n;
    private final GraphQLContext graphQLContext;

    public ValidationContext(GraphQLSchema schema, Document document, I18n i18n) {
        this.schema = schema;
        this.document = document;
        this.traversalContext = new TraversalContext(schema);
        this.i18n = i18n;
        this.graphQLContext = GraphQLContext.newContext().of(Locale.class, i18n.getLocale()).build();
        buildFragmentMap();
    }

    private void buildFragmentMap() {
        for (Definition<?> definition : document.getDefinitions()) {
            if (!(definition instanceof FragmentDefinition)) {
                continue;
            }
            FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
            fragmentDefinitionMap.put(fragmentDefinition.getName(), fragmentDefinition);
        }
    }

    public TraversalContext getTraversalContext() {
        return traversalContext;
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    public Document getDocument() {
        return document;
    }

    public @Nullable FragmentDefinition getFragment(String name) {
        return fragmentDefinitionMap.get(name);
    }

    public @Nullable GraphQLCompositeType getParentType() {
        return traversalContext.getParentType();
    }

    public @Nullable GraphQLInputType getInputType() {
        return traversalContext.getInputType();
    }

    public @Nullable InputValueWithState getDefaultValue() {
        return traversalContext.getDefaultValue();
    }

    public @Nullable GraphQLFieldDefinition getFieldDef() {
        return traversalContext.getFieldDef();
    }

    public @Nullable GraphQLDirective getDirective() {
        return traversalContext.getDirective();
    }

    public @Nullable GraphQLArgument getArgument() {
        return traversalContext.getArgument();
    }

    public @Nullable GraphQLOutputType getOutputType() {
        return traversalContext.getOutputType();
    }

    public @Nullable List<String> getQueryPath() {
        return traversalContext.getQueryPath();
    }

    public I18n getI18n() {
        return i18n;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    /**
     * Creates an I18N message using the key and arguments
     *
     * @param msgKey  the key in the underlying message bundle
     * @param msgArgs the message arguments
     *
     * @return the formatted I18N message
     */
    public String i18n(String msgKey, Object... msgArgs) {
        return i18n.msg(msgKey, msgArgs);
    }

    @Override
    public String toString() {
        return "ValidationContext{" + getQueryPath() + "}";
    }
}
