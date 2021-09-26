package graphql.schema;

import graphql.DirectivesUtil;
import graphql.Internal;

import java.util.ArrayList;
import java.util.List;

import static graphql.Assert.assertNotNull;

@Internal
public class GraphqlDirectivesContainerTypeBuilder extends GraphqlTypeBuilder {

    protected final List<GraphQLAppliedDirective> appliedDirectives = new ArrayList<>();
    protected final List<GraphQLDirective> directives = new ArrayList<>();


    public GraphqlDirectivesContainerTypeBuilder replaceAppliedDirectives(List<GraphQLAppliedDirective> directives) {
        assertNotNull(directives, () -> "directive can't be null");
        this.appliedDirectives.clear();
        this.appliedDirectives.addAll(directives);
        return this;
    }

    public GraphqlDirectivesContainerTypeBuilder withAppliedDirectives(GraphQLAppliedDirective... directives) {
        assertNotNull(directives, () -> "directives can't be null");
        this.appliedDirectives.clear();
        for (GraphQLAppliedDirective directive : directives) {
            withAppliedDirective(directive);
        }
        return this;
    }

    public GraphqlDirectivesContainerTypeBuilder withAppliedDirective(GraphQLAppliedDirective directive) {
        assertNotNull(directive, () -> "directive can't be null");
        this.appliedDirectives.add(directive);
        return this;
    }

    public GraphqlDirectivesContainerTypeBuilder withAppliedDirective(GraphQLAppliedDirective.Builder builder) {
        return withAppliedDirectives(builder.build());
    }


    public GraphqlDirectivesContainerTypeBuilder replaceDirectives(List<GraphQLDirective> directives) {
        assertNotNull(directives, () -> "directive can't be null");
        this.directives.clear();
        DirectivesUtil.enforceAddAll(this.directives, directives); // TODO
        return this;
    }

    public GraphqlDirectivesContainerTypeBuilder withDirectives(GraphQLDirective... directives) {
        assertNotNull(directives, () -> "directives can't be null");
        this.directives.clear();
        for (GraphQLDirective directive : directives) {
            withDirective(directive);
        }
        return this;
    }

    public GraphqlDirectivesContainerTypeBuilder withDirective(GraphQLDirective directive) {
        assertNotNull(directive, () -> "directive can't be null");
        this.directives.add(directive);
        return this;
    }

    public GraphqlDirectivesContainerTypeBuilder withDirective(GraphQLDirective.Builder builder) {
        return withDirective(builder.build());
    }

    /**
     * This is used to clear all the directives in the builder so far.
     *
     * @return the builder
     */
    public GraphqlDirectivesContainerTypeBuilder clearDirectives() {
        directives.clear();
        appliedDirectives.clear();
        return this;
    }

    protected void copyExistingDirectives(GraphQLDirectiveContainer directivesContainer) {
        DirectivesUtil.enforceAddAll(this.directives,
                directivesContainer.getDirectives(),
                this.appliedDirectives,
                directivesContainer.getAppliedDirectives());

    }
}
