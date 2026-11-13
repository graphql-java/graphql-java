package graphql.schema;


import com.google.common.collect.ImmutableList;
import graphql.DirectivesUtil;
import graphql.PublicApi;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveExtensionDefinition;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.introspection.Introspection.DirectiveLocation;
import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;
import static graphql.util.FpKit.getByName;

/**
 * A directive can be used to modify the behavior of a graphql field or type.
 * <p>
 * See <a href="https://graphql.org/learn/queries/#directives">https://graphql.org/learn/queries/#directives</a> for more details on the concept.
 * <p>
 * A directive has a definition, that is what arguments it takes, and it can also be applied
 * to other schema elements.  Originally graphql-java re-used the {@link GraphQLDirective} and {@link GraphQLArgument}
 * classes to do both purposes.  This was a modelling mistake.  New {@link GraphQLAppliedDirective} and {@link GraphQLAppliedDirectiveArgument}
 * classes have been introduced to better model when a directive is applied to a schema element,
 * as opposed to its schema definition itself.
 */
@PublicApi
@NullMarked
public class GraphQLDirective implements GraphQLNamedSchemaElement, GraphQLDirectiveContainer {

    private final String name;
    private final boolean repeatable;
    private final @Nullable String description;
    private final EnumSet<DirectiveLocation> locations;
    private final ImmutableList<GraphQLArgument> arguments;
    private final @Nullable DirectiveDefinition definition;
    private final ImmutableList<DirectiveExtensionDefinition> extensionDefinitions;
    private final DirectivesUtil.DirectivesHolder directivesHolder;
    private final @Nullable String deprecationReason;


    public static final String CHILD_ARGUMENTS = "arguments";

    private GraphQLDirective(String name,
                             @Nullable String description,
                             boolean repeatable,
                             EnumSet<DirectiveLocation> locations,
                             List<GraphQLArgument> arguments,
                             List<GraphQLDirective> directives,
                             List<GraphQLAppliedDirective> appliedDirectives,
                             @Nullable DirectiveDefinition definition,
                             List<DirectiveExtensionDefinition> extensionDefinitions,
                             @Nullable String deprecationReason) {
        assertValidName(name);
        assertNotNull(arguments, "arguments can't be null");
        assertNotEmpty(locations, "locations can't be empty");
        this.name = name;
        this.description = description;
        this.repeatable = repeatable;
        this.locations = locations;
        this.arguments = ImmutableList.copyOf(arguments);
        this.definition = definition;
        this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
        this.directivesHolder = DirectivesUtil.DirectivesHolder.create(directives, appliedDirectives);
        this.deprecationReason = deprecationReason;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public boolean isNonRepeatable() {
        return !repeatable;
    }

    public List<GraphQLArgument> getArguments() {
        return arguments;
    }

    public @Nullable GraphQLArgument getArgument(String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

    public EnumSet<DirectiveLocation> validLocations() {
        return EnumSet.copyOf(locations);
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable DirectiveDefinition getDefinition() {
        return definition;
    }

    public List<DirectiveExtensionDefinition> getExtensionDefinitions() {
        return extensionDefinitions;
    }

    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    public @Nullable String getDeprecationReason() {
        return deprecationReason;
    }

    @Override
    public List<GraphQLDirective> getDirectives() {
        return directivesHolder.getDirectives();
    }

    @Override
    public Map<String, GraphQLDirective> getDirectivesByName() {
        return directivesHolder.getDirectivesByName();
    }

    @Override
    public Map<String, List<GraphQLDirective>> getAllDirectivesByName() {
        return directivesHolder.getAllDirectivesByName();
    }

    @Override
    public @Nullable GraphQLDirective getDirective(String directiveName) {
        return directivesHolder.getDirective(directiveName);
    }

    @Override
    public List<GraphQLAppliedDirective> getAppliedDirectives() {
        return directivesHolder.getAppliedDirectives();
    }

    @Override
    public Map<String, List<GraphQLAppliedDirective>> getAllAppliedDirectivesByName() {
        return directivesHolder.getAllAppliedDirectivesByName();
    }

    @Override
    public @Nullable GraphQLAppliedDirective getAppliedDirective(String directiveName) {
        return directivesHolder.getAppliedDirective(directiveName);
    }

    @Override
    public String toString() {
        return "GraphQLDirective{" +
                "name='" + name + '\'' +
                ", repeatable='" + repeatable + '\'' +
                ", arguments=" + arguments +
                ", locations=" + locations +
                '}';
    }

    /**
     * This helps you transform the current GraphQLDirective into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public GraphQLDirective transform(Consumer<Builder> builderConsumer) {
        Builder builder = newDirective(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newDirective(this).build();
    }


    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLDirective(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>(arguments);
        children.addAll(directivesHolder.getDirectives());
        children.addAll(directivesHolder.getAppliedDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return newSchemaElementChildrenContainer()
                .children(CHILD_ARGUMENTS, arguments)
                .children(CHILD_DIRECTIVES, directivesHolder.getDirectives())
                .children(CHILD_APPLIED_DIRECTIVES, directivesHolder.getAppliedDirectives())
                .build();
    }

    @Override
    public GraphQLDirective withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceArguments(newChildren.getChildren(CHILD_ARGUMENTS))
                        .replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replaceAppliedDirectives(newChildren.getChildren(CHILD_APPLIED_DIRECTIVES))
        );
    }

    /**
     * This method can be used to turn a directive that was being use as an applied directive into one.
     * @return an {@link GraphQLAppliedDirective}
     */
    public GraphQLAppliedDirective toAppliedDirective() {
        GraphQLAppliedDirective.Builder builder = GraphQLAppliedDirective.newDirective();
        builder.name(this.name);
        for (GraphQLArgument argument : arguments) {
            builder.argument(argument.toAppliedArgument());
        }
        return builder.build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }


    public static Builder newDirective() {
        return new Builder();
    }

    public static Builder newDirective(GraphQLDirective existing) {
        return new Builder(existing);
    }

    @NullUnmarked
    public static class Builder extends GraphqlDirectivesContainerTypeBuilder<Builder, Builder> {

        private EnumSet<DirectiveLocation> locations = EnumSet.noneOf(DirectiveLocation.class);
        private final Map<String, GraphQLArgument> arguments = new LinkedHashMap<>();
        private DirectiveDefinition definition;
        private List<DirectiveExtensionDefinition> extensionDefinitions = emptyList();
        private String deprecationReason;
        private boolean repeatable = false;

        public Builder() {
        }

        public Builder(GraphQLDirective existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.repeatable = existing.isRepeatable();
            this.locations = existing.validLocations();
            this.arguments.putAll(getByName(existing.getArguments(), GraphQLArgument::getName));
            this.definition = existing.getDefinition();
            this.extensionDefinitions = existing.getExtensionDefinitions();
            this.deprecationReason = existing.getDeprecationReason();
            copyExistingDirectives(existing);
        }

        public Builder repeatable(boolean repeatable) {
            this.repeatable = repeatable;
            return this;
        }

        public Builder validLocations(DirectiveLocation... validLocations) {
            Collections.addAll(locations, validLocations);
            return this;
        }

        public Builder validLocation(DirectiveLocation validLocation) {
            locations.add(validLocation);
            return this;
        }

        public Builder clearValidLocations() {
            locations = EnumSet.noneOf(DirectiveLocation.class);
            return this;
        }

        public Builder argument(GraphQLArgument argument) {
            assertNotNull(argument, "argument must not be null");
            arguments.put(argument.getName(), argument);
            return this;
        }

        public Builder replaceArguments(List<GraphQLArgument> arguments) {
            assertNotNull(arguments, "arguments must not be null");
            this.arguments.clear();
            for (GraphQLArgument argument : arguments) {
                this.arguments.put(argument.getName(), argument);
            }
            return this;
        }

        /**
         * Take an argument builder in a function definition and apply. Can be used in a jdk8 lambda
         * e.g.:
         * <pre>
         *     {@code
         *      argument(a -> a.name("argumentName"))
         *     }
         * </pre>
         *
         * @param builderFunction a supplier for the builder impl
         *
         * @return this
         */
        public Builder argument(UnaryOperator<GraphQLArgument.Builder> builderFunction) {
            GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
            builder = builderFunction.apply(builder);
            return argument(builder);
        }

        /**
         * Same effect as the argument(GraphQLArgument). Builder.build() is called
         * from within
         *
         * @param builder an un-built/incomplete GraphQLArgument
         *
         * @return this
         */
        public Builder argument(GraphQLArgument.Builder builder) {
            return argument(builder.build());
        }

        /**
         * This is used to clear all the arguments in the builder so far.
         *
         * @return the builder
         */
        public Builder clearArguments() {
            arguments.clear();
            return this;
        }


        public Builder definition(DirectiveDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<DirectiveExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }

        public Builder deprecate(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        // -- the following are repeated to avoid a binary incompatibility problem --

        @Override
        public Builder replaceDirectives(List<GraphQLDirective> directives) {
            return super.replaceDirectives(directives);
        }

        @Override
        public Builder withDirectives(GraphQLDirective... directives) {
            return super.withDirectives(directives);
        }

        @Override
        public Builder withDirective(GraphQLDirective directive) {
            return super.withDirective(directive);
        }

        @Override
        public Builder withDirective(GraphQLDirective.Builder builder) {
            return super.withDirective(builder);
        }

        @Override
        public Builder replaceAppliedDirectives(List<GraphQLAppliedDirective> directives) {
            return super.replaceAppliedDirectives(directives);
        }

        @Override
        public Builder withAppliedDirectives(GraphQLAppliedDirective... directives) {
            return super.withAppliedDirectives(directives);
        }

        @Override
        public Builder withAppliedDirective(GraphQLAppliedDirective directive) {
            return super.withAppliedDirective(directive);
        }

        @Override
        public Builder withAppliedDirective(GraphQLAppliedDirective.Builder builder) {
            return super.withAppliedDirective(builder);
        }

        @Override
        public Builder clearDirectives() {
            return super.clearDirectives();
        }

        @Override
        public Builder name(String name) {
            return super.name(name);
        }

        @Override
        public Builder description(String description) {
            return super.description(description);
        }

        public GraphQLDirective build() {
            return new GraphQLDirective(
                    name,
                    description,
                    repeatable,
                    locations,
                    sort(arguments, GraphQLDirective.class, GraphQLArgument.class),
                    sort(directives, GraphQLDirective.class, GraphQLDirective.class),
                    sort(appliedDirectives, GraphQLDirective.class, GraphQLAppliedDirective.class),
                    definition,
                    extensionDefinitions,
                    deprecationReason);
        }


    }
}
