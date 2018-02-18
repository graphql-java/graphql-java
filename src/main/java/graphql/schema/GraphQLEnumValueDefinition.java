package graphql.schema;


import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static java.util.Collections.emptyList;

/**
 * A graphql enumeration type has a limited set of values and this defines one of those unique values
 *
 * See http://graphql.org/learn/schema/#enumeration-types for more details
 *
 * @see graphql.schema.GraphQLEnumType
 */
@PublicApi
public class GraphQLEnumValueDefinition {

    private final String name;
    private final String description;
    private final Object value;
    private final String deprecationReason;
    private final List<GraphQLDirective> directives;

    @Internal
    public GraphQLEnumValueDefinition(String name, String description, Object value) {
        this(name, description, value, null, emptyList());
    }

    @Internal
    public GraphQLEnumValueDefinition(String name, String description, Object value, String deprecationReason) {
        this(name, description, value, deprecationReason, emptyList());
    }

    @Internal
    public GraphQLEnumValueDefinition(String name, String description, Object value, String deprecationReason, List<GraphQLDirective> directives) {
        assertValidName(name);
        assertNotNull(directives, "directives cannot be null");

        this.name = name;
        this.description = description;
        this.value = value;
        this.deprecationReason = deprecationReason;
        this.directives = directives;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Object getValue() {
        return value;
    }

    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    public String getDeprecationReason() {
        return deprecationReason;
    }

    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
    }

    public Map<String, GraphQLDirective> getDirectivesByName() {
        return DirectivesUtil.directivesByName(directives);
    }

    public GraphQLDirective getDirective(String directiveName) {
        return getDirectivesByName().get(directiveName);
    }

    public static Builder newEnumValueDefinition() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {
        private String name;
        private String description;
        private Object value;
        private String deprecationReason;
        private List<GraphQLDirective> directives = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder deprecationReason(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        public Builder withDirectives(GraphQLDirective... directives) {
            Collections.addAll(this.directives, directives);
            return this;
        }

        public GraphQLEnumValueDefinition build() {
            return new GraphQLEnumValueDefinition(name, description, value, deprecationReason, directives);
        }
    }
}
