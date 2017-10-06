package graphql.schema;


import graphql.Internal;
import graphql.PublicApi;
import graphql.language.ScalarTypeDefinition;

import java.util.HashMap;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

/**
 * A scalar type is a leaf node in the graphql tree of types.  This class allows you to define new scalar types.
 *
 * <blockquote>
 * GraphQL provides a number of built‐in scalars, but type systems can add additional scalars with semantic meaning,
 * for example, a GraphQL system could define a scalar called Time which, while serialized as a string, promises to
 * conform to ISO‐8601. When querying a field of type Time, you can then rely on the ability to parse the result with an ISO‐8601 parser and use a client‐specific primitive for time.
 *
 * From the spec : http://facebook.github.io/graphql/#sec-Scalars
 * </blockquote>
 *
 * graphql-java ships with a set of predefined scalar types via {@link graphql.Scalars}
 *
 * @see graphql.Scalars
 */
public class GraphQLScalarType implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLMetadataSupport {

    private final String name;
    private final String description;
    private final Coercing coercing;
    private final ScalarTypeDefinition definition;
    private final Map<String, Object> metadata;

    @Internal
    public GraphQLScalarType(String name, String description, Coercing coercing) {
        this(name, description, coercing, null, null);
    }

    @Internal
    public GraphQLScalarType(String name, String description, Coercing coercing, ScalarTypeDefinition definition) {
        this(name, description, coercing, definition, null);
    }

    @Internal
    public GraphQLScalarType(String name, String description, Coercing coercing, ScalarTypeDefinition definition, Map<String, Object> metadata) {
        assertValidName(name);
        assertNotNull(coercing, "coercing can't be null");
        this.name = name;
        this.description = description;
        this.coercing = coercing;
        this.definition = definition;
        this.metadata = metadata == null ? emptyMap() : unmodifiableMap(metadata);
    }

    public String getName() {
        return name;
    }


    public String getDescription() {
        return description;
    }


    public Coercing getCoercing() {
        return coercing;
    }

    public ScalarTypeDefinition getDefinition() {
        return definition;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "GraphQLScalarType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", coercing=" + coercing +
                '}';
    }


    public static Builder newScalar() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {
        private String name;
        private String description;
        private Coercing coercing;
        private ScalarTypeDefinition definition;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder coercing(Coercing coercing) {
            this.coercing = coercing;
            return this;
        }

        public Builder definition(ScalarTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new HashMap<>() : metadata;
            return this;
        }

        public Builder metadata(String key, Object metadataValue) {
            this.metadata.put(key, metadataValue);
            return this;
        }

        public GraphQLScalarType build() {
            return new GraphQLScalarType(name, description, coercing, definition, metadata);
        }
    }

}
