package graphql.language;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;

@PublicApi
@NullMarked
public class DirectiveExtensionDefinition extends DirectiveDefinition implements SDLExtensionDefinition {

    @Internal
    protected DirectiveExtensionDefinition(String name,
                                           List<Directive> directives,
                                           @Nullable SourceLocation sourceLocation,
                                           List<Comment> comments,
                                           IgnoredChars ignoredChars,
                                           Map<String, String> additionalData) {
        super(name, false, null, emptyList(), directives, emptyList(), sourceLocation, comments, ignoredChars, additionalData);
    }

    @Override
    public DirectiveExtensionDefinition deepCopy() {
        return new DirectiveExtensionDefinition(
                getName(),
                assertNotNull(deepCopy(getDirectives())),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public DirectiveExtensionDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transformExtension(builder -> builder.directives(newChildren.getChildren(CHILD_DIRECTIVES)));
    }

    public static Builder newDirectiveExtensionDefinition() {
        return new Builder();
    }

    public DirectiveExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public String toString() {
        return "DirectiveExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", directives=" + getDirectives() +
                '}';
    }

    @NullUnmarked
    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private String name;
        private ImmutableList<Directive> directives = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(DirectiveExtensionDefinition existing) {
            sourceLocation = existing.getSourceLocation();
            comments = ImmutableList.copyOf(existing.getComments());
            name = existing.getName();
            directives = ImmutableList.copyOf(existing.getDirectives());
            ignoredChars = existing.getIgnoredChars();
            additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder directives(List<Directive> directives) {
            this.directives = ImmutableList.copyOf(directives);
            return this;
        }

        @Override
        public Builder directive(Directive directive) {
            directives = ImmutableKit.addToList(directives, directive);
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = assertNotNull(additionalData);
            return this;
        }

        public Builder additionalData(String key, String value) {
            additionalData.put(key, value);
            return this;
        }

        public DirectiveExtensionDefinition build() {
            return new DirectiveExtensionDefinition(name, directives, sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}
