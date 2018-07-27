package graphql.schema;


import graphql.Directives;
import graphql.PublicApi;
import graphql.schema.validation.InvalidSchemaException;
import graphql.schema.validation.SchemaValidationError;
import graphql.schema.validation.SchemaValidator;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;
import static java.lang.String.format;
import static java.util.Arrays.asList;

/**
 * The schema represents the combined type system of the graphql engine.  This is how the engine knows
 * what graphql queries represent what data.
 *
 * See http://graphql.org/learn/schema/#type-language for more details
 */
@PublicApi
public class GraphQLSchema {


    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final GraphQLObjectType subscriptionType;
    private final Map<String, GraphQLType> typeMap;
    private final Set<GraphQLType> additionalTypes;
    private final Set<GraphQLDirective> directives;
    private final GraphqlFieldVisibility fieldVisibility;
    private final Map<String, List<GraphQLObjectType>> byInterface;


    public GraphQLSchema(GraphQLObjectType queryType) {
        this(queryType, null, Collections.emptySet());
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, Set<GraphQLType> additionalTypes) {
        this(queryType, mutationType, null, additionalTypes);
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, GraphQLObjectType subscriptionType, Set<GraphQLType> additionalTypes) {
        this(queryType, mutationType, subscriptionType, additionalTypes, Collections.emptySet(), DEFAULT_FIELD_VISIBILITY);
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, GraphQLObjectType subscriptionType, Set<GraphQLType> additionalTypes, Set<GraphQLDirective> directives, GraphqlFieldVisibility fieldVisibility) {
        assertNotNull(additionalTypes, "additionalTypes can't be null");
        assertNotNull(queryType, "queryType can't be null");
        assertNotNull(directives, "directives can't be null");
        assertNotNull(fieldVisibility, "fieldVisibility can't be null");

        SchemaUtil schemaUtil = new SchemaUtil();

        this.queryType = queryType;
        this.mutationType = mutationType;
        this.subscriptionType = subscriptionType;
        this.fieldVisibility = fieldVisibility;
        this.additionalTypes = additionalTypes;
        this.directives = new LinkedHashSet<>(
                asList(Directives.IncludeDirective, Directives.SkipDirective, Directives.DeferDirective)
        );
        this.directives.addAll(directives);
        this.typeMap = schemaUtil.allTypes(this, additionalTypes);
        this.byInterface = schemaUtil.groupImplementations(this);
    }

    public Set<GraphQLType> getAdditionalTypes() {
        return additionalTypes;
    }

    public GraphQLType getType(String typeName) {
        return typeMap.get(typeName);
    }

    /**
     * Called to return a named {@link graphql.schema.GraphQLObjectType} from the schema
     *
     * @param typeName the name of the type
     *
     * @return a graphql object type or null if there is one
     *
     * @throws graphql.GraphQLException if the type is NOT a object type
     */
    public GraphQLObjectType getObjectType(String typeName) {
        GraphQLType graphQLType = typeMap.get(typeName);
        if (graphQLType != null) {
            assertTrue(graphQLType instanceof GraphQLObjectType,
                    format("You have asked for named object type '%s' but its not an object type but rather a '%s'", typeName, graphQLType.getClass().getName()));
        }
        return (GraphQLObjectType) graphQLType;
    }

    public Map<String, GraphQLType> getTypeMap() {
        return Collections.unmodifiableMap(typeMap);
    }

    public List<GraphQLType> getAllTypesAsList() {
        return new ArrayList<>(typeMap.values());
    }

    /**
     * This will return the list of {@link graphql.schema.GraphQLObjectType} types that implement the given
     * interface type.
     *
     * @param type interface type to obtain implementations of.
     *
     * @return list of types implementing provided interface
     */
    public List<GraphQLObjectType> getImplementations(GraphQLInterfaceType type) {
        List<GraphQLObjectType> implementations = byInterface.get(type.getName());
        return (implementations == null)
                ? Collections.emptyList()
                : Collections.unmodifiableList(implementations);
    }

    /**
     * Returns true if a specified concrete type is a possible type of a provided abstract type.
     * If the provided abstract type is:
     *   - an interface, it checks whether the concrete type is one of its implementations.
     *   - a union, it checks whether the concrete type is one of its possible types.
     *
     * @param abstractType abstract type either interface or union
     * @param concreteType concrete type
     * @return true if possible type, false otherwise.
     */
    public boolean isPossibleType(GraphQLType abstractType, GraphQLObjectType concreteType) {
        if (abstractType instanceof GraphQLInterfaceType) {
            return getImplementations((GraphQLInterfaceType) abstractType).stream()
                    .map(GraphQLType::getName)
                    .anyMatch(name -> concreteType.getName().equals(name));
        } else if (abstractType instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) abstractType).getTypes().stream()
                    .map(GraphQLType::getName)
                    .anyMatch(name -> concreteType.getName().equals(name));
        }

        return assertShouldNeverHappen("Unsupported abstract type %s. Abstract types supported are Union and Interface.", abstractType.getName());
    }

    public GraphQLObjectType getQueryType() {
        return queryType;
    }

    public GraphQLObjectType getMutationType() {
        return mutationType;
    }

    public GraphQLObjectType getSubscriptionType() {
        return subscriptionType;
    }

    public GraphqlFieldVisibility getFieldVisibility() {
        return fieldVisibility;
    }

    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
    }

    public GraphQLDirective getDirective(String name) {
        for (GraphQLDirective directive : getDirectives()) {
            if (directive.getName().equals(name)) return directive;
        }
        return null;
    }

    public boolean isSupportingMutations() {
        return mutationType != null;
    }

    public boolean isSupportingSubscriptions() {
        return subscriptionType != null;
    }

    /**
     * This helps you transform the current GraphQLSchema object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new GraphQLSchema object based on calling build on that builder
     */
    public GraphQLSchema transform(Consumer<Builder> builderConsumer) {
        Builder builder = newSchema(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * @return a new schema builder
     */
    public static Builder newSchema() {
        return new Builder();
    }

    /**
     * This allows you to build a schema from an existing schema.  It copies everything from the existing
     * schema and then allows you to replace them.
     *
     * @param existingSchema the existing schema
     *
     * @return a new schema builder
     */
    public static Builder newSchema(GraphQLSchema existingSchema) {
        return new Builder()
                .query(existingSchema.getQueryType())
                .mutation(existingSchema.getMutationType())
                .subscription(existingSchema.getSubscriptionType())
                .fieldVisibility(existingSchema.getFieldVisibility())
                .additionalDirectives(existingSchema.directives)
                .additionalTypes(existingSchema.additionalTypes);
    }

    public static class Builder {
        private GraphQLObjectType queryType;
        private GraphQLObjectType mutationType;
        private GraphQLObjectType subscriptionType;
        private GraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;
        private Set<GraphQLType> additionalTypes = new HashSet<>();
        private Set<GraphQLDirective> additionalDirectives = new HashSet<>();

        public Builder query(GraphQLObjectType.Builder builder) {
            return query(builder.build());
        }

        public Builder query(GraphQLObjectType queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder mutation(GraphQLObjectType.Builder builder) {
            return mutation(builder.build());
        }

        public Builder mutation(GraphQLObjectType mutationType) {
            this.mutationType = mutationType;
            return this;
        }

        public Builder subscription(GraphQLObjectType.Builder builder) {
            return subscription(builder.build());
        }

        public Builder subscription(GraphQLObjectType subscriptionType) {
            this.subscriptionType = subscriptionType;
            return this;
        }

        public Builder fieldVisibility(GraphqlFieldVisibility fieldVisibility) {
            this.fieldVisibility = fieldVisibility;
            return this;
        }

        public Builder additionalTypes(Set<GraphQLType> additionalTypes) {
            this.additionalTypes.addAll(additionalTypes);
            return this;
        }

        public Builder additionalType(GraphQLType additionalType) {
            this.additionalTypes.add(additionalType);
            return this;
        }

        public Builder additionalDirectives(Set<GraphQLDirective> additionalDirectives) {
            this.additionalDirectives.addAll(additionalDirectives);
            return this;
        }

        public Builder additionalDirective(GraphQLDirective additionalDirective) {
            this.additionalDirectives.add(additionalDirective);
            return this;
        }

        public GraphQLSchema build() {
            return build(additionalTypes, additionalDirectives);
        }

        public GraphQLSchema build(Set<GraphQLType> additionalTypes) {
            return build(additionalTypes, Collections.emptySet());
        }

        public GraphQLSchema build(Set<GraphQLType> additionalTypes, Set<GraphQLDirective> additionalDirectives) {
            assertNotNull(additionalTypes, "additionalTypes can't be null");
            assertNotNull(additionalDirectives, "additionalDirectives can't be null");
            GraphQLSchema graphQLSchema = new GraphQLSchema(queryType, mutationType, subscriptionType, additionalTypes, additionalDirectives, fieldVisibility);
            new SchemaUtil().replaceTypeReferences(graphQLSchema);
            Collection<SchemaValidationError> errors = new SchemaValidator().validateSchema(graphQLSchema);
            if (errors.size() > 0) {
                throw new InvalidSchemaException(errors);
            }
            return graphQLSchema;
        }
    }
}
