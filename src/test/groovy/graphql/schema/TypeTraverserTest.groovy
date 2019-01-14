package graphql.schema

import graphql.Scalars
import graphql.TypeResolutionEnvironment
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLTypeReference.typeRef

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

        new TypeTraverser().depthFirst(visitor, newArgument()
                .name("Test")
                .type(Scalars.GraphQLString)
                .build())
        then:
        visitor.getStack() == ["argument: Test", "fallback: Test", "scalar: String", "fallback: String"]
    }

    def "reachable number argument type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, newArgument()
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
                               "enum value: abc", "fallback: abc",
                               "enum value: bar", "fallback: bar"]

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
        new TypeTraverser().depthFirst(visitor, typeRef("something"))
        then:
        visitor.getStack() == ["reference: something", "fallback: something"]
    }

    def "reachable union type"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        new TypeTraverser().depthFirst(visitor, GraphQLUnionType.newUnionType()
                .name("foo")
                .possibleType(GraphQLObjectType.newObject().name("dummy").build())
                .possibleType(typeRef("dummyRef"))
                .typeResolver(NOOP_RESOLVER)
                .build())
        then:
        visitor.getStack() == ["union: foo", "fallback: foo",
                               "object: dummy", "fallback: dummy",
                               "reference: dummyRef", "fallback: dummyRef"]
    }

    def "reachable scalar directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def coercing = new Coercing() {
            private static final String TEST_ONLY = "For testing only";

            @Override
            Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                throw new UnsupportedOperationException(TEST_ONLY);
            }

            @Override
            Object parseValue(Object input) throws CoercingParseValueException {
                throw new UnsupportedOperationException(TEST_ONLY);
            }

            @Override
            Object parseLiteral(Object input) throws CoercingParseLiteralException {
                throw new UnsupportedOperationException(TEST_ONLY);
            }
        }
        def scalarType = GraphQLScalarType.newScalar()
                .name("foo")
                .coercing(coercing)
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, scalarType)
        then:
        visitor.getStack() == ["scalar: foo", "fallback: foo", "directive: bar", "fallback: bar"]
    }

    def "reachable object directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def objectType = GraphQLObjectType.newObject()
                .name("foo")
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, objectType)
        then:
        visitor.getStack() == ["object: foo", "fallback: foo", "directive: bar", "fallback: bar"]
    }

    def "reachable field definition directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
                .name("foo")
                .type(Scalars.GraphQLString)
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, fieldDefinition)
        then:
        visitor.getStack() == ["field: foo", "fallback: foo", "scalar: String", "fallback: String", "directive: bar", "fallback: bar"]
    }

    def "reachable argument directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def argument = newArgument()
                .name("foo")
                .type(Scalars.GraphQLString)
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, argument)
        then:
        visitor.getStack() == ["argument: foo", "fallback: foo", "scalar: String", "fallback: String", "directive: bar", "fallback: bar"]
    }

    def "reachable interface directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def interfaceType = GraphQLInterfaceType.newInterface()
                .name("foo")
                .typeResolver(NOOP_RESOLVER)
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, interfaceType)
        then:
        visitor.getStack() == ["interface: foo", "fallback: foo", "directive: bar", "fallback: bar"]
    }

    def "reachable union directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def unionType = GraphQLUnionType.newUnionType()
                .name("foo")
                .possibleType(GraphQLObjectType.newObject().name("dummy").build())
                .typeResolver(NOOP_RESOLVER)
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, unionType)
        then:
        visitor.getStack() == ["union: foo", "fallback: foo", "object: dummy", "fallback: dummy", "directive: bar", "fallback: bar"]
    }

    def "reachable enum directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def enumType = GraphQLEnumType.newEnum()
                .name("foo")
                .value("dummy")
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, enumType)
        then:
        visitor.getStack() == ["enum: foo", "fallback: foo", "enum value: dummy", "fallback: dummy", "directive: bar", "fallback: bar"]
    }

    def "reachable enum value directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def enumValue = GraphQLEnumValueDefinition.newEnumValueDefinition()
                .name("foo")
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, enumValue)
        then:
        visitor.getStack() == ["enum value: foo", "fallback: foo", "directive: bar", "fallback: bar"]
    }

    def "reachable input object directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("foo")
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, inputObjectType)
        then:
        visitor.getStack() == ["input object: foo", "fallback: foo", "directive: bar", "fallback: bar"]
    }

    def "reachable input field definition directive"() {
        when:
        def visitor = new GraphQLTestingVisitor()
        def inputField = GraphQLInputObjectField.newInputObjectField()
                .name("foo")
                .type(Scalars.GraphQLString)
                .withDirective(GraphQLDirective.newDirective()
                .name("bar"))
                .build()
        new TypeTraverser().depthFirst(visitor, inputField)
        then:
        visitor.getStack() == ["input object field: foo", "fallback: foo", "scalar: String", "fallback: String", "directive: bar", "fallback: bar"]
    }

    def "back references is are called when a type reference node is visited more than once"() {
        when:
        def visitor = new GraphQLTestingVisitor()

        def typeRef = typeRef("String")

        new TypeTraverser().depthFirst(visitor, [
                newArgument()
                        .name("Test1")
                        .type(typeRef)
                        .build(),
                newArgument()
                        .name("Test2")
                        .type(typeRef)
                        .build()
        ])
        then:
        visitor.getStack() == ["argument: Test1", "fallback: Test1", "reference: String", "fallback: String",
                               "argument: Test2", "fallback: Test2", "backRef: String"
        ]

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
        TraversalControl visitGraphQLDirective(GraphQLDirective node, TraverserContext<GraphQLType> context) {
            stack.add("directive: ${node.getName()}")
            return super.visitGraphQLDirective(node, context)
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

        @Override
        TraversalControl visitBackRef(TraverserContext<GraphQLType> context) {
            stack.add("backRef: ${context.thisNode().getName()}")
            return TraversalControl.CONTINUE
        }

        def getStack() {
            return stack
        }
    }


}
