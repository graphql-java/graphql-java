package graphql.validation.rules.experimental.overlappingfieldsmerge;

import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;

import java.util.Objects;

/**
 * Inspired by Simon Adameit's implementation in Sangria https://github.com/sangria-graphql-org/sangria/pull/12
 */
interface TypeShape {

    static TypeShape apply(GraphQLOutputType outputType) {
        if (outputType != null) {
            return new Known(outputType);
        }
        return Unknown.INSTANCE;
    }

    /**
     * Unknown types are ignored by the validation
     */
    class Unknown implements TypeShape {

        public static final Unknown INSTANCE = new Unknown();
    }

    class Known implements TypeShape {

        private Shape typeShape;

        public Known(GraphQLOutputType outputType) {
            this.typeShape = Shape.apply(outputType);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Known) {
                return typeShape.equals(((Known) o).typeShape);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return typeShape.hashCode();
        }
    }

    class Shape {

        static Shape apply(GraphQLOutputType type) {
            if (type instanceof GraphQLNonNull) {
                return new NonNullShape(Shape.apply((GraphQLOutputType) ((GraphQLNonNull) type).getWrappedType()));
            }
            if (type instanceof GraphQLList) {
                return new ListShape(Shape.apply((GraphQLOutputType) ((GraphQLList) type).getWrappedType()));
            }
            if (type instanceof GraphQLCompositeType) {
                return CompositeShape.INSTANCE;
            }
            if (type instanceof GraphQLNamedOutputType) {
                return new LeafShape(((GraphQLNamedOutputType) type).getName());
            }
            throw new IllegalStateException("No mapping for type " + type.getChildren());
        }
    }

    class ListShape extends Shape {

        Shape type;

        public ListShape(Shape type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ListShape)) {
                return false;
            }
            ListShape that = (ListShape) o;
            return type.equals(that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type);
        }
    }

    class NonNullShape extends Shape {

        Known.Shape type;

        public NonNullShape(Shape type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NonNullShape)) {
                return false;
            }
            NonNullShape that = (NonNullShape) o;
            return type.equals(that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type);
        }
    }

    class LeafShape extends Shape {

        String name;

        public LeafShape(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof LeafShape)) {
                return false;
            }
            LeafShape leafShape = (LeafShape) o;
            return name.equals(leafShape.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    /**
     * Composite types do not need to match, as we require that the individual selected
     * fields match recursively.
     */
    class CompositeShape extends Shape {

        private static final CompositeShape INSTANCE = new CompositeShape();
    }

}
