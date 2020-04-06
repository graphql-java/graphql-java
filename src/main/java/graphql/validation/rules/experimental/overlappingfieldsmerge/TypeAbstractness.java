package graphql.validation.rules.experimental.overlappingfieldsmerge;

import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLObjectType;

import java.util.Objects;

/**
 * Inspired by Simon Adameit's implementation in Sangria https://github.com/sangria-graphql-org/sangria/pull/12
 */
interface TypeAbstractness {

    static TypeAbstractness apply(GraphQLCompositeType parentType) {
        if (parentType instanceof GraphQLObjectType) {
            return new Concrete(parentType.getName());
        }
        return Abstract.INSTANCE;
    }

    /**
     * For the purpose of grouping types for the validation,
     * we consider abstract types to constitute one group
     */
    class Abstract implements TypeAbstractness {

        public static final Abstract INSTANCE = new Abstract();
    }

    class Concrete implements TypeAbstractness {

        private String name;

        public Concrete(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Concrete)) {
                return false;
            }
            Concrete concrete = (Concrete) o;
            return name.equals(concrete.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
}
