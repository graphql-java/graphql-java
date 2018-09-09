package graphql.schema

import graphql.Scalars
import graphql.TypeResolutionEnvironment
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

class TypeTraverserTest extends Specification {


    def "reachable scalar type"() {

        when:

        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, Scalars.GraphQLString)

        then:

        visitor.getStack() == ["scalar: String", "fallback: String"]


    }

    def "reachable string argument type"() {
        when:
        def visitor = new GraphQLTestingVisitor()

        new TypeTraverser().depthFirst(visitor, GraphQLArgument.newArgument()
                .name("Test")
                .type(Scalars.GraphQLString)
                .build())
        then:
        visitor.getStack() == ["argument: Test", "fallback: Test", "scalar: String", "fallback: String"]
    }

    def "reachable number argument type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLArgument.newArgument()
                .name("Test")
                .type(Scalars.GraphQLInt)
                .build())
        then:
        visitor.getStack() == ["argument: Test", "fallback: Test", "scalar: Int", "fallback: Int"]

    }

    def "reachable enum type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLEnumType
                .newEnum()
                .name("foo")
                .value("bar")
                .value(GraphQLEnumValueDefinition.newEnumValueDefinition().name("abc").value(123).build())
                .build())
        then:
        visitor.getStack() == ["enum: foo", "fallback: foo",
                               "enum value: bar", "fallback: bar",
                               "enum value: abc", "fallback: abc"]

    }

    def "reachable field definition type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLFieldDefinition.newFieldDefinition()
                .name("foo")
                .type(Scalars.GraphQLString)
                .build())
        then:
        visitor.getStack() == ["field: foo", "fallback: foo", "scalar: String", "fallback: String"]

    }

    def "reachable input object field type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLInputObjectField.newInputObjectField()
                .name("bar")
                .type(Scalars.GraphQLString)
                .build())
        then:
        visitor.getStack() == ["input object field: bar", "fallback: bar", "scalar: String", "fallback: String"]
    }

    def "reachable input object type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLInputObjectType.newInputObject()
                .name("foo")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("bar")
                .type(Scalars.GraphQLString)
                .build())
                .build())
        then:
        visitor.getStack() == ["input object: foo", "fallback: foo",
                               "input object field: bar", "fallback: bar",
                               "scalar: String", "fallback: String"]
    }


    def "reachable interface type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLInterfaceType.newInterface()
                .name("foo")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                .name("bar")
                .type(Scalars.GraphQLString)
                .build())
                .typeResolver(NOOP_RESOLVER)
                .build())
        then:
        visitor.getStack() == ["interface: foo", "fallback: foo",
                               "field: bar", "fallback: bar",
                               "scalar: String", "fallback: String"]
    }

    def "reachable list type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLList.list(Scalars.GraphQLString))
        then:
        visitor.getStack() == ["list: String", "fallback: null",
                               "scalar: String", "fallback: String"]
    }


    def "reachable nonNull type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLNonNull.nonNull(Scalars.GraphQLString))
        then:
        visitor.getStack() == ["nonNull: String", "fallback: null",
                               "scalar: String", "fallback: String"]
    }

    def "reachable object type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLObjectType.newObject()
                .name("myObject")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                .name("foo")
                .type(Scalars.GraphQLString)
                .build())
                .withInterface(GraphQLInterfaceType.newInterface()
                .name("bar")
                .typeResolver(NOOP_RESOLVER)
                .build())
                .build())
        then:
        visitor.getStack() == ["object: myObject", "fallback: myObject",
                               "field: foo", "fallback: foo",
                               "scalar: String", "fallback: String",
                               "interface: bar", "fallback: bar"]
    }


    def "reachable reference type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLTypeReference.typeRef("something"))
        then:
        visitor.getStack() == ["reference: something", "fallback: something"]
    }

    def "reachable union type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLUnionType.newUnionType()
                .name("foo")
                .possibleType(GraphQLObjectType.newObject().name("dummy").build())
                .possibleType(GraphQLTypeReference.typeRef("dummyRef"))
                .typeResolver(NOOP_RESOLVER)
                .build())
        then:
        visitor.getStack() == ["union: foo", "fallback: foo",
                               "object: dummy", "fallback: dummy",
                               "reference: dummyRef", "fallback: dummyRef"]
    }


    def NOOP_RESOLVER = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            return null
        }
    }


    class GraphQLTestingVisitor extends GraphQLTypeVisitorStub {

        def stack = []

        @Override
        TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLType> context) {
            stack.add("argument: ${node.getName()}")
            return super.visitGraphQLArgument(node, context)
        }

        @Override
        TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLType> context) {
            stack.add("scalar: ${node.getName()}")
            return super.visitGraphQLScalarType(node, context)
        }

        @Override
        protected TraversalControl visitGraphQLType(GraphQLType node, TraverserContext<GraphQLType> context) {
            stack.add("fallback: ${node.getName()}")
            return super.visitGraphQLType(node, context)
        }

        @Override
        TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLType> context) {
            stack.add("enum: ${node.getName()}")
            return super.visitGraphQLEnumType(node, context)
        }

        @Override
        TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLType> context) {
            stack.add("enum value: ${node.getName()}")
            return super.visitGraphQLEnumValueDefinition(node, context)
        }

        @Override
        TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLType> context) {
            stack.add("field: ${node.getName()}")
            return super.visitGraphQLFieldDefinition(node, context)
        }

        @Override
        TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLType> context) {
            stack.add("input object field: ${node.getName()}")
            return super.visitGraphQLInputObjectField(node, context)
        }

        @Override
        TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLType> context) {
            stack.add("input object: ${node.getName()}")
            return super.visitGraphQLInputObjectType(node, context)
        }

        @Override
        TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLType> context) {
            stack.add("interface: ${node.getName()}")
            return super.visitGraphQLInterfaceType(node, context)
        }

        @Override
        TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLType> context) {
            stack.add("list: ${node.getWrappedType().getName()}")
            return super.visitGraphQLList(node, context)
        }

        @Override
        TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLType> context) {
            stack.add("nonNull: ${node.getWrappedType().getName()}")
            return super.visitGraphQLNonNull(node, context)
        }

        @Override
        TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLType> context) {
            stack.add("object: ${node.getName()}")
            return super.visitGraphQLObjectType(node, context)
        }

        @Override
        TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLType> context) {
            stack.add("reference: ${node.getName()}")
            return super.visitGraphQLTypeReference(node, context)
        }

        @Override
        TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLType> context) {
            stack.add("union: ${node.getName()}")
            return super.visitGraphQLUnionType(node, context)
        }

        def getStack() {
            return stack
        }
    }


}
