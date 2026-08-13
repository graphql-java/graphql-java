package graphql.validation;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.schema.GraphQLSchema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Internal
@NullMarked
public class ValidationContext {

    private final GraphQLSchema schema;
    private final Document document;

    private final Map<String, FragmentDefinition> fragmentDefinitionMap = new LinkedHashMap<>();
    private final I18n i18n;
    private final GraphQLContext graphQLContext;
    private final QueryComplexityLimits queryComplexityLimits;

    public ValidationContext(GraphQLSchema schema, Document document, I18n i18n) {
        this(schema, document, i18n, null);
    }

    public ValidationContext(GraphQLSchema schema, Document document, I18n i18n, @Nullable QueryComplexityLimits limits) {
        this.schema = schema;
        this.document = document;
        this.i18n = i18n;
        this.graphQLContext = GraphQLContext.newContext().of(Locale.class, i18n.getLocale()).build();
        this.queryComplexityLimits = limits != null ? limits : QueryComplexityLimits.getDefaultLimits();
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

    public GraphQLSchema getSchema() {
        return schema;
    }

    public Document getDocument() {
        return document;
    }

    public @Nullable FragmentDefinition getFragment(String name) {
        return fragmentDefinitionMap.get(name);
    }

    public I18n getI18n() {
        return i18n;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    public QueryComplexityLimits getQueryComplexityLimits() {
        return queryComplexityLimits;
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

}
