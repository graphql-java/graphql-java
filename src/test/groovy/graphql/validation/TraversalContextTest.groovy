package graphql.validation

import graphql.Directives
import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.Directive
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.InlineFragment
import graphql.language.ObjectField
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLNonNull
import spock.lang.Specification

import static graphql.Directives.IncludeDirective
import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.droidType
import static graphql.StarWarsSchema.queryType
import static graphql.StarWarsSchema.starWarsSchema
import static graphql.language.OperationDefinition.Operation.QUERY
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class TraversalContextTest extends Specification {

    TraversalContext traversalContext = new TraversalContext(starWarsSchema)

    def "operation definition"() {
        given:
        SelectionSet selectionSet = new SelectionSet([])
        OperationDefinition operationDefinition = OperationDefinition.newOperationDefinition().name(queryType.getName()).operation(QUERY).selectionSet(selectionSet).build()

        when:
        traversalContext.enter(operationDefinition, [])

        then:
        traversalContext.getOutputType() == queryType

        when:
        traversalContext.leave(operationDefinition, [])

        then:
        traversalContext.getOutputType() == null
    }

    def "SelectionSet saves current output type as parent"() {
        given:
        SelectionSet selectionSet = SelectionSet.newSelectionSet().build()
        traversalContext.outputTypeStack.add(nonNull(droidType))

        when:
        traversalContext.enter(selectionSet, [])

        then:
        traversalContext.getParentType() == droidType

        when:
        traversalContext.leave(selectionSet, [])

        then:
        traversalContext.getParentType() == null
    }

    def "field saves output type and fieldDefinition"() {
        given:
        def parentType = droidType
        traversalContext.parentTypeStack.add(parentType)
        Field field = new Field("id")

        when:
        traversalContext.enter(field, [])

        then:
        traversalContext.getOutputType() == droidType.getFieldDefinition("id").getType()
        traversalContext.getFieldDef() == droidType.getFieldDefinition("id")

        when:
        traversalContext.leave(field, [])

        then:
        traversalContext.getOutputType() == null
        traversalContext.getFieldDef() == null

    }

    def "directives are saved"() {
        given:
        Directive directive = new Directive("skip")

        when:
        traversalContext.enter(directive, [])

        then:
        traversalContext.getDirective() == Directives.SkipDirective

        when:
        traversalContext.leave(directive, [])

        then:
        traversalContext.getDirective() == null
    }

    def "inlineFragment type condition saved as output type"() {
        given:
        InlineFragment inlineFragment = new InlineFragment(new TypeName(droidType.getName()))

        when:
        traversalContext.enter(inlineFragment, [])

        then:
        traversalContext.getOutputType() == droidType

        when:
        traversalContext.leave(inlineFragment, [])

        then:
        traversalContext.getOutputType() == null
    }

    def "fragmentDefinition type condition saved as output type"() {
        given:
        FragmentDefinition fragmentDefinition = FragmentDefinition.newFragmentDefinition().name("fragment").typeCondition(new TypeName(droidType.getName())).build()

        when:
        traversalContext.enter(fragmentDefinition, [])

        then:
        traversalContext.getOutputType() == droidType

        when:
        traversalContext.leave(fragmentDefinition, [])

        then:
        traversalContext.getOutputType() == null
    }

    def "variableDefinition saved as input type"() {
        given:
        VariableDefinition variableDefinition = new VariableDefinition("var", new TypeName("String"))

        when:
        traversalContext.enter(variableDefinition, [])

        then:
        traversalContext.getInputType() == GraphQLString

        when:
        traversalContext.leave(variableDefinition, [])

        then:
        traversalContext.getInputType() == null
    }

    def "variableDefinition that is not a GraphQLInputType should result as null"() {
        given: "a GraphQLObjectType instead of a GraphQLInputType"
        VariableDefinition variableDefinition = new VariableDefinition("var", new TypeName("Human"))

        when:
        traversalContext.enter(variableDefinition, [])

        then:
        traversalContext.getInputType() == null

        when:
        traversalContext.leave(variableDefinition, [])

        then:
        traversalContext.getInputType() == null
    }

    def "field argument saves argument and input type"() {
        given:
        Argument argument = new Argument("id", new StringValue("string"))
        traversalContext.fieldDefStack.add(queryType.getFieldDefinition("droid"))

        when:
        traversalContext.enter(argument, [])

        then:
        traversalContext.getArgument() == queryType.getFieldDefinition("droid").getArgument("id")
        traversalContext.getInputType() == queryType.getFieldDefinition("droid").getArgument("id").getType()

        when:
        traversalContext.leave(argument, [])

        then:
        traversalContext.getArgument() == null
        traversalContext.getInputType() == null

    }

    def "directive argument saves argument and input type"() {
        given:
        Argument argument = new Argument("if", new BooleanValue(true))
        traversalContext.directive = IncludeDirective

        when:
        traversalContext.enter(argument, [])

        then:
        traversalContext.getArgument() == IncludeDirective.getArgument("if")
        traversalContext.getInputType() == IncludeDirective.getArgument("if").getType()

        when:
        traversalContext.leave(argument, [])

        then:
        traversalContext.getArgument() == null
        traversalContext.getInputType() == null
    }

    def "array value saves input type"() {
        given:
        GraphQLNonNull graphQLList = nonNull(list(GraphQLString))
        traversalContext.inputTypeStack.add(graphQLList)
        ArrayValue arrayValue = new ArrayValue([new StringValue("string")])

        when:
        traversalContext.enter(arrayValue, [])

        then:
        traversalContext.getInputType() == GraphQLString

        when:
        traversalContext.leave(arrayValue, [])

        then:
        traversalContext.getInputType() == graphQLList
    }

    def "object field saves input type"() {
        given:
        def inputObjectField = GraphQLInputObjectField.newInputObjectField().name("field").type(GraphQLString)
        GraphQLInputObjectType inputObjectType = GraphQLInputObjectType.newInputObject().name("inputObjectType").field(inputObjectField).build()
        traversalContext.inputTypeStack.add(inputObjectType)
        ObjectField objectField = new ObjectField("field", new StringValue("value"))

        when:
        traversalContext.enter(objectField, [])

        then:
        traversalContext.getInputType() == GraphQLString

        when:
        traversalContext.leave(objectField, [])

        then:
        traversalContext.getInputType() == inputObjectType
    }

    def "visit array with schema input type is enum: input type is null after"() {
        given:
        GraphQLEnumType enumType = GraphQLEnumType.newEnum().name("EnumType").value("Val1").value("Val2").build()
        ArrayValue arrayValue = ArrayValue.newArrayValue().build()

        traversalContext.inputTypeStack.add(enumType)

        when:
        traversalContext.enter(arrayValue, [])

        then:
        traversalContext.getInputType() == null
    }
}
