package graphql.validation

import graphql.Directives
import graphql.language.*
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import spock.lang.Specification

import static graphql.Directives.IncludeDirective
import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.*
import static graphql.language.OperationDefinition.Operation.QUERY

class TraversalContextTest extends Specification {

    TraversalContext traversalContext = new TraversalContext(starWarsSchema)

    def "operation definition"() {
        given:
        SelectionSet selectionSet = new SelectionSet([])
        OperationDefinition operationDefinition = new OperationDefinition(queryType.getName(), QUERY, selectionSet)

        when:
        traversalContext.enter(operationDefinition,[])

        then:
        traversalContext.getOutputType() == queryType

        when:
        traversalContext.leave(operationDefinition,[])

        then:
        traversalContext.getOutputType() == null
    }

    def "SelectionSet saves current output type as parent"() {
        given:
        SelectionSet selectionSet = new SelectionSet()
        traversalContext.outputTypeStack.add(new GraphQLNonNull(droidType))

        when:
        traversalContext.enter(selectionSet,[])

        then:
        traversalContext.getParentType() == droidType

        when:
        traversalContext.leave(selectionSet,[])

        then:
        traversalContext.getParentType() == null
    }

    def "field saves output type and fieldDefinition"() {
        given:
        def parentType = droidType
        traversalContext.parentTypeStack.add(parentType)
        Field field = new Field("id")

        when:
        traversalContext.enter(field,[])

        then:
        traversalContext.getOutputType() == droidType.getFieldDefinition("id").getType()
        traversalContext.getFieldDef() == droidType.getFieldDefinition("id")

        when:
        traversalContext.leave(field,[])

        then:
        traversalContext.getOutputType() == null
        traversalContext.getFieldDef() == null

    }

    def "directives are saved"() {
        given:
        Directive directive = new Directive("skip")

        when:
        traversalContext.enter(directive,[])

        then:
        traversalContext.getDirective() == Directives.SkipDirective

        when:
        traversalContext.leave(directive,[])

        then:
        traversalContext.getDirective() == null
    }

    def "inlineFragment type condition saved as output type"() {
        given:
        InlineFragment inlineFragment = new InlineFragment(new TypeName(droidType.getName()))

        when:
        traversalContext.enter(inlineFragment,[])

        then:
        traversalContext.getOutputType() == droidType

        when:
        traversalContext.leave(inlineFragment,[])

        then:
        traversalContext.getOutputType() == null
    }

    def "fragmentDefinition type condition saved as output type"() {
        given:
        FragmentDefinition fragmentDefinition = new FragmentDefinition("fragment", new TypeName(droidType.getName()))

        when:
        traversalContext.enter(fragmentDefinition,[])

        then:
        traversalContext.getOutputType() == droidType

        when:
        traversalContext.leave(fragmentDefinition,[])

        then:
        traversalContext.getOutputType() == null
    }

    def "variableDefinition saved as input type"() {
        given:
        VariableDefinition variableDefinition = new VariableDefinition("var", new TypeName("String"))

        when:
        traversalContext.enter(variableDefinition,[])

        then:
        traversalContext.getInputType() == GraphQLString

        when:
        traversalContext.leave(variableDefinition,[])

        then:
        traversalContext.getInputType() == null
    }

    def "field argument saves argument and input type"() {
        given:
        Argument argument = new Argument("id", new StringValue("string"))
        traversalContext.fieldDefStack.add(queryType.getFieldDefinition("droid"))

        when:
        traversalContext.enter(argument,[])

        then:
        traversalContext.getArgument() == queryType.getFieldDefinition("droid").getArgument("id")
        traversalContext.getInputType() == queryType.getFieldDefinition("droid").getArgument("id").getType()

        when:
        traversalContext.leave(argument,[])

        then:
        traversalContext.getArgument() == null
        traversalContext.getInputType() == null

    }

    def "directive argument saves argument and input type"() {
        given:
        Argument argument = new Argument("if", new BooleanValue(true))
        traversalContext.directive = IncludeDirective

        when:
        traversalContext.enter(argument,[])

        then:
        traversalContext.getArgument() == IncludeDirective.getArgument("if")
        traversalContext.getInputType() == IncludeDirective.getArgument("if").getType()

        when:
        traversalContext.leave(argument,[])

        then:
        traversalContext.getArgument() == null
        traversalContext.getInputType() == null
    }

    def "array value saves input type"() {
        given:
        GraphQLNonNull graphQLList = new GraphQLNonNull(new GraphQLList(GraphQLString));
        traversalContext.inputTypeStack.add(graphQLList);
        ArrayValue arrayValue = new ArrayValue([new StringValue("string")])

        when:
        traversalContext.enter(arrayValue,[])

        then:
        traversalContext.getInputType() == GraphQLString

        when:
        traversalContext.leave(arrayValue,[])

        then:
        traversalContext.getInputType() == graphQLList
    }

    def "object field saves input type"() {
        given:
        def inputObjectField = GraphQLInputObjectField.newInputObjectField().name("field").type(GraphQLString)
        GraphQLInputObjectType inputObjectType = GraphQLInputObjectType.newInputObject().name("inputObjectType").field(inputObjectField).build()
        traversalContext.inputTypeStack.add(inputObjectType);
        ObjectField objectField = new ObjectField("field", new StringValue("value"))

        when:
        traversalContext.enter(objectField,[])

        then:
        traversalContext.getInputType() == GraphQLString

        when:
        traversalContext.leave(objectField,[])

        then:
        traversalContext.getInputType() == inputObjectType
    }
}
