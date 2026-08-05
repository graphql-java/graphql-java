package graphql.schema.universe

import graphql.AssertException
import graphql.Scalars
import graphql.TestUtil
import graphql.language.StringValue
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphqlTypeComparatorRegistry
import graphql.schema.InputValueWithState
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

import java.util.EnumSet

import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT

class SUTest extends Specification {

    def "three derived schemas reuse vertices and unchanged adjacency"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def string = universe.newScalarType("String")
        def foo = universe.newField("foo")
        def foo2 = universe.newField("foo2")

        def schema1 = universe.newSchema("S1")
                .queryType(query)
                .addField(query, foo)
                .setFieldType(foo, string)
                .build()
        def baseVertexCount = universe.vertexCount

        when:
        def schema2 = schema1.transform("S2", builder -> builder
                .removeField(query, foo)
                .addField(query, foo2)
                .setFieldType(foo2, string))
        def schema3 = schema1.transform("S3", builder -> builder
                .addField(query, foo2)
                .setFieldType(foo2, string))

        then:
        schema1.queryType.is(query)
        schema2.queryType.is(query)
        schema3.queryType.is(query)

        schema1.getFields(query)*.name == ["foo"]
        schema2.getFields(query)*.name == ["foo2"]
        schema3.getFields(query)*.name == ["foo", "foo2"]

        schema1.getField(query, "foo").is(foo)
        schema1.getField(query, "foo2") == null
        schema2.getField(query, "foo") == null
        schema2.getField(query, "foo2").is(foo2)

        schema1.getType(foo).is(string)
        schema2.getType(foo2).is(string)
        schema3.getType(foo).is(string)
        schema3.getType(foo2).is(string)

        !schema1.root.is(schema2.root)
        !schema1.root.is(schema3.root)
        schema1.sharesOutgoingEdgesWith(schema2, foo)
        schema1.sharesOutgoingEdgesWith(schema3, foo)
        !schema1.sharesOutgoingEdgesWith(schema2, query)

        universe.vertexCount == baseVertexCount + 2
    }

    def "transforming one branch does not change its parent or sibling"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def string = universe.newScalarType("String")
        def foo = universe.newField("foo")
        def bar = universe.newField("bar")
        def base = universe.newSchema("base")
                .queryType(query)
                .addField(query, foo)
                .setFieldType(foo, string)
                .build()

        when:
        def withoutFoo = base.transform("withoutFoo", builder ->
                builder.removeField(query, foo))
        def withBar = base.transform("withBar", builder -> builder
                .addField(query, bar)
                .setFieldType(bar, string))

        then:
        base.getFields(query)*.name == ["foo"]
        withoutFoo.getFields(query).isEmpty()
        withBar.getFields(query)*.name == ["foo", "bar"]
        withoutFoo.getField(query, "bar") == null
    }

    def "universe registers schemas by name in creation order"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def first = universe.newSchema("first")
                .queryType(query)
                .build()
        def second = first.transform("second", builder -> {})

        expect:
        universe.getSchema("first").is(first)
        universe.getSchema("second").is(second)
        universe.getSchema("missing") == null
        universe.schemas == [first, second]
        universe.schemasByName.keySet().toList() == ["first", "second"]

        when:
        universe.newSchema("first")
                .queryType(query)
                .build()

        then:
        thrown(AssertException)
        universe.schemas == [first, second]
    }

    def "schemas can be removed from the universe registry"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def first = universe.newSchema("first")
                .queryType(query)
                .build()
        def second = first.transform("second", builder -> {})

        when:
        def removed = universe.removeSchema("first")

        then:
        removed.is(first)
        universe.getSchema("first") == null
        universe.schemas == [second]
        universe.schemasByName == [second: second]
        universe.removeSchema("missing") == null

        when:
        first.transform("fromRemoved", builder -> {})

        then:
        thrown(AssertException)

        when:
        def replacement = second.transform("first", builder -> {})

        then:
        universe.schemas == [second, replacement]
        universe.getSchema("first").is(replacement)

        when:
        def removedOldInstance = universe.removeSchema(first)

        then:
        !removedOldInstance
        universe.getSchema("first").is(replacement)

        when:
        def removedReplacement = universe.removeSchema(replacement)

        then:
        removedReplacement
        universe.schemas == [second]
        universe.schemasByName == [second: second]
    }

    def "removing a schema leaves its vertices stored until cleanup"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def field = universe.newField("foo")
        def string = universe.newScalarType("String")
        def schema = universe.newSchema("schema")
                .queryType(query)
                .addField(query, field)
                .setFieldType(field, string)
                .build()
        def root = schema.root
        def vertexCount = universe.vertexCount

        when:
        universe.removeSchema(schema)

        then:
        universe.schemas.isEmpty()
        universe.vertexCount == vertexCount
        universe.getVertex(root.id).is(root)
        universe.getVertex(query.id).is(query)
        universe.getVertex(field.id).is(field)
        universe.getVertex(string.id).is(string)
    }

    def "exact duplicate edges are folded but duplicate child names are rejected"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def firstFoo = universe.newField("foo")
        def secondFoo = universe.newField("foo")

        when:
        def schema = universe.newSchema("duplicates")
                .queryType(query)
                .addField(query, firstFoo)
                .addField(query, firstFoo)
                .build()

        then:
        schema.getFields(query) == [firstFoo]

        when:
        universe.newSchema("invalid")
                .queryType(query)
                .addField(query, firstFoo)
                .addField(query, secondFoo)
                .build()

        then:
        thrown(AssertException)
    }

    def "single valued edges can be replaced explicitly"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def foo = universe.newField("foo")
        def string = universe.newScalarType("String")
        def integer = universe.newScalarType("Int")
        def schema = universe.newSchema("base")
                .queryType(query)
                .addField(query, foo)
                .setFieldType(foo, string)
                .build()

        when:
        def changed = schema.transform("changed", builder ->
                builder.setFieldType(foo, integer))

        then:
        schema.getType(foo).is(string)
        !schema.getType(foo).is(integer)
        !changed.getType(foo).is(string)
        changed.getType(foo).is(integer)
    }

    def "vertices from another universe are rejected"() {
        given:
        def first = new SchemaUniverse()
        def second = new SchemaUniverse()
        def query = first.newObjectType("Query")
        def foreignField = second.newField("foo")

        when:
        first.newSchema("invalid")
                .queryType(query)
                .addField(query, foreignField)

        then:
        thrown(AssertException)
    }

    def "edge endpoints register every named type directly on the schema"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def user = universe.newObjectType("User")
        def field = universe.newField("user")
        def name = universe.newField("name")
        def string = universe.newScalarType("String")

        when:
        def schema = universe.newSchema("schema")
                .queryType(query)
                .addField(query, field)
                .setFieldType(field, user)
                .addField(user, name)
                .setFieldType(name, string)
                .build()

        then:
        schema.types.containsAll([query, string, user])
        schema.getObjectType("__Schema").is(schema.introspectionSchemaType)
        schema.getType("Query").is(query)
        schema.getObjectType("User").is(user)
        schema.getScalarType("String").is(string)
    }

    def "named type registry is structurally shared across schema transforms"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def string = universe.newScalarType("String")
        def integer = universe.newScalarType("Int")
        def base = universe.newSchema("base")
                .queryType(query)
                .addType(string)
                .build()

        when:
        def unchanged = base.transform("unchanged", builder -> builder.addType(string))
        def changed = base.transform("changed", builder -> builder.addType(integer))

        then:
        unchanged.namedTypesByNameId.is(base.namedTypesByNameId)
        !changed.namedTypesByNameId.is(base.namedTypesByNameId)
        changed.getType("Query").is(query)
        changed.getType("String").is(string)
        changed.getType("Int").is(integer)
        base.getType("Int") == null
    }

    def "a schema rejects different named type vertices with the same name"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def duplicate = universe.newScalarType("Query")

        when:
        universe.newSchema("invalid")
                .queryType(query)
                .addType(duplicate)

        then:
        def exception = thrown(AssertException)
        exception.message.contains("different type named 'Query'")
    }

    def "typed builder helpers cover every schema relationship"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def mutation = universe.newObjectType("Mutation")
        def subscription = universe.newObjectType("Subscription")
        def user = universe.newObjectType("User")
        def node = universe.newInterfaceType("Node")
        def resource = universe.newInterfaceType("Resource")
        def searchResult = universe.newUnionType("SearchResult")
        def status = universe.newEnumType("Status")
        def active = universe.newEnumValue("ACTIVE")
        def inactive = universe.newEnumValue("INACTIVE")
        def filter = universe.newInputObjectType("Filter")
        def term = universe.newInputField("term")
        def string = universe.newScalarType("String")
        def integer = universe.newScalarType("Int")
        def search = universe.newField("search")
        def id = universe.newField("id")
        def limit = universe.newArgument("limit")
        def tag = universe.newDirective("tag")
        def label = universe.newArgument("label")
        def list = universe.newListType()
        def nonNull = universe.newNonNullType()
        def firstTag = universe.newAppliedDirective("tag")
        def secondTag = universe.newAppliedDirective("tag")
        def schemaTag = universe.newAppliedDirective("schemaTag")

        def schema = universe.newSchema("complete")
                .setQueryType(query)
                .setMutationType(mutation)
                .setSubscriptionType(subscription)
                .addType(user)
                .addType(node)
                .addType(resource)
                .addType(searchResult)
                .addType(status)
                .addType(filter)
                .addType(string)
                .addType(integer)
                .addDirectiveDefinition(tag)
                .addField(query, search)
                .addField(node, id)
                .setWrappedType(list, string)
                .setWrappedType(nonNull, list)
                .setFieldType(search, nonNull)
                .setFieldType(id, string)
                .addArgument(search, limit)
                .setArgumentType(limit, integer)
                .addArgument(tag, label)
                .setArgumentType(label, string)
                .addInterface(user, node)
                .addInterface(node, resource)
                .addUnionMember(searchResult, user)
                .addEnumValue(status, active)
                .addEnumValue(status, inactive)
                .addInputField(filter, term)
                .setInputFieldType(term, string)
                .addAppliedDirective(user, firstTag)
                .addAppliedDirective(user, secondTag)
                .addSchemaAppliedDirective(schemaTag)
                .build()

        expect:
        schema.queryType.is(query)
        schema.mutationType.is(mutation)
        schema.subscriptionType.is(subscription)
        schema.getField(query, "search").is(search)
        schema.getField(node, "id").is(id)
        schema.getType(search).is(nonNull)
        schema.getWrappedType(nonNull).is(list)
        schema.getWrappedType(list).is(string)
        schema.getArgument(search, "limit").is(limit)
        schema.getArgument(tag, "label").is(label)
        schema.getInterfaces(user) == [node]
        schema.getInterfaces(node) == [resource]
        schema.getUnionMembers(searchResult) == [user]
        schema.getEnumValues(status) == [active, inactive]
        schema.getInputFields(filter) == [term]
        schema.getAppliedDirectives(user) == [firstTag, secondTag]
        schema.schemaAppliedDirectives == [schemaTag]

        when:
        def removed = schema.transform("removed", builder -> builder
                .removeField(query, search)
                .removeField(node, "id")
                .removeArgument(search, limit)
                .removeArgument(tag, "label")
                .removeInterface(user, node)
                .removeInterface(node, "Resource")
                .removeUnionMember(searchResult, user)
                .removeEnumValue(status, "ACTIVE")
                .removeInputField(filter, term)
                .removeAppliedDirective(user, firstTag)
                .removeSchemaAppliedDirective(schemaTag)
                .removeDirectiveDefinition(tag))

        then:
        removed.getFields(query).isEmpty()
        removed.getFields(node).isEmpty()
        removed.getArguments(search).isEmpty()
        removed.getArguments(tag).isEmpty()
        removed.getInterfaces(user).isEmpty()
        removed.getInterfaces(node).isEmpty()
        removed.getUnionMembers(searchResult).isEmpty()
        removed.getEnumValues(status) == [inactive]
        removed.getInputFields(filter).isEmpty()
        removed.getAppliedDirectives(user) == [secondTag]
        removed.schemaAppliedDirectives.isEmpty()
        removed.getDirectiveDefinition("tag") == null
        removed.getScalarType("Int").is(integer)

        when:
        def withoutTags = schema.transform("withoutTags", builder ->
                builder.removeAppliedDirectives(user, "tag"))
        def cleared = schema.transform("cleared", builder -> builder
                .setMutationType(null)
                .setSubscriptionType(null)
                .clearFields(query)
                .clearFields(node)
                .clearArguments(search)
                .clearArguments(tag)
                .clearInterfaces(user)
                .clearInterfaces(node)
                .clearUnionMembers(searchResult)
                .clearEnumValues(status)
                .clearInputFields(filter)
                .clearAppliedDirectives(user)
                .clearSchemaAppliedDirectives()
                .clearDirectiveDefinitions())

        then:
        withoutTags.getAppliedDirectives(user).isEmpty()
        cleared.mutationType == null
        cleared.subscriptionType == null
        cleared.getFields(query).isEmpty()
        cleared.getFields(node).isEmpty()
        cleared.getArguments(search).isEmpty()
        cleared.getArguments(tag).isEmpty()
        cleared.getInterfaces(user).isEmpty()
        cleared.getInterfaces(node).isEmpty()
        cleared.getUnionMembers(searchResult).isEmpty()
        cleared.getEnumValues(status).isEmpty()
        cleared.getInputFields(filter).isEmpty()
        cleared.getAppliedDirectives(user).isEmpty()
        cleared.schemaAppliedDirectives.isEmpty()
        cleared.directiveDefinitions.isEmpty()
        cleared.types == schema.types

        and:
        schema.getFields(query) == [search]
        schema.getAppliedDirectives(user) == [firstTag, secondTag]
    }

    def "typed type setters reject invalid input and output types"() {
        given:
        def universe = new SchemaUniverse()
        def field = universe.newField("field")
        def argument = universe.newArgument("argument")
        def inputField = universe.newInputField("input")
        def objectType = universe.newObjectType("Object")
        def inputObjectType = universe.newInputObjectType("Input")
        def firstNonNull = universe.newNonNullType()
        def secondNonNull = universe.newNonNullType()
        def builder = universe.newSchema("invalid")

        when:
        builder.setFieldType(field, inputObjectType)

        then:
        thrown(AssertException)

        when:
        builder.setArgumentType(argument, objectType)

        then:
        thrown(AssertException)

        when:
        builder.setInputFieldType(inputField, objectType)

        then:
        thrown(AssertException)

        when:
        builder.setWrappedType(firstNonNull, secondNonNull)

        then:
        thrown(AssertException)
    }

    def "an existing GraphQLSchema can seed a universe"() {
        given:
        def role = GraphQLEnumType.newEnum()
                .name("Role")
                .value(GraphQLEnumValueDefinition.newEnumValueDefinition().name("ADMIN").build())
                .build()
        def filter = GraphQLInputObjectType.newInputObject()
                .name("Filter")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("role")
                        .type(role))
                .build()
        def namesType = GraphQLNonNull.nonNull(
                GraphQLList.list(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
        def query = GraphQLObjectType.newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("names")
                        .type(namesType)
                        .argument(GraphQLArgument.newArgument()
                                .name("filter")
                                .type(filter)))
                .build()
        def graphQLSchema = GraphQLSchema.newSchema()
                .query(query)
                .additionalType(role)
                .additionalType(filter)
                .build()
        def universe = new SchemaUniverse()

        when:
        def schema = universe.importSchema("imported", graphQLSchema)
        def universeQuery = schema.queryType
        def names = schema.getField(universeQuery, "names")
        def filterArgument = schema.getArgument(names, "filter")
        def outerNonNull = schema.getType(names)
        def list = schema.getWrappedType(outerNonNull)
        def innerNonNull = schema.getWrappedType(list)
        def string = schema.getWrappedType(innerNonNull)

        then:
        universe.getSchema("imported").is(schema)
        universeQuery instanceof SUObjectType
        names instanceof SUField
        filterArgument instanceof SUArgument
        schema.getType(filterArgument) instanceof SUInputObjectType
        outerNonNull instanceof SUNonNullType
        list instanceof SUListType
        innerNonNull instanceof SUNonNullType
        string instanceof SUScalarType
        string.name == "String"

        and:
        def universeRole = schema.getEnumType("Role")
        universeRole instanceof SUEnumType
        schema.getEnumValue(universeRole, "ADMIN") instanceof SUEnumValue
    }

    def "input value states are intrinsic schema data"() {
        given:
        def universe = new SchemaUniverse()
        def string = universe.newScalarType("String")
        def literal = InputValueWithState.newLiteralValue(StringValue.newStringValue("literal").build())
        def externalNull = InputValueWithState.newExternalValue(null)
        def internal = InputValueWithState.newInternalValue([answer: 42])

        when:
        def literalArgument = universe.newArgument("literal", null, literal, null)
        def nullInputField = universe.newInputField("nullable", null, externalNull, null)
        def internalAppliedArgument =
                universe.newAppliedDirectiveArgument("internal", string, internal, null)
        def unsetArgument = universe.newArgument("unset")

        then:
        literalArgument.argumentDefaultValue.is(literal)
        literalArgument.argumentDefaultValue.isLiteral()
        nullInputField.inputFieldDefaultValue.is(externalNull)
        nullInputField.inputFieldDefaultValue.isExternal()
        nullInputField.inputFieldDefaultValue.value == null
        internalAppliedArgument.argumentValue.is(internal)
        internalAppliedArgument.argumentValue.isInternal()
        internalAppliedArgument.argumentValue.value == [answer: 42]
        unsetArgument.argumentDefaultValue.isNotSet()
    }

    def "changing a default value uses a new vertex and leaves the parent unchanged"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def field = universe.newField("search")
        def integer = universe.newScalarType("Int")
        def originalArgument = universe.newArgument(
                "limit", null, InputValueWithState.newExternalValue(10))
        def changedArgument = universe.newArgument(
                "limit", null, InputValueWithState.newExternalValue(20))
        def base = universe.newSchema("base")
                .queryType(query)
                .addField(query, field)
                .addArgument(field, originalArgument)
                .setArgumentType(originalArgument, integer)
                .build()

        when:
        def changed = base.transform("changed", builder -> builder
                .removeArgument(field, "limit")
                .addArgument(field, changedArgument)
                .setArgumentType(changedArgument, integer))

        then:
        base.getArgument(field, "limit").is(originalArgument)
        changed.getArgument(field, "limit").is(changedArgument)
        originalArgument.argumentDefaultValue.value == 10
        changedArgument.argumentDefaultValue.value == 20
        !base.sharesOutgoingEdgesWith(changed, field)
        base.sharesOutgoingEdgesWith(changed, originalArgument)
    }

    def "import preserves defaults and complete directive metadata"() {
        given:
        def graphQLSchema = TestUtil.schema('''
            directive @tag(label: String = "fallback") repeatable on OBJECT | FIELD_DEFINITION

            input Filter {
                limit: Int = 10
            }

            type Query @tag(label: "query") {
                search(filter: Filter, limit: Int = 5): String @tag(label: null)
            }
        ''')
        def universe = new SchemaUniverse()

        when:
        def schema = universe.importSchema("directives", graphQLSchema)
        def directive = schema.getDirectiveDefinition("tag")
        def directiveArgument = schema.getArgument(directive, "label")
        def filter = schema.getInputObjectType("Filter")
        def inputField = schema.getInputField(filter, "limit")
        def query = schema.queryType
        def search = schema.getField(query, "search")
        def fieldArgument = schema.getArgument(search, "limit")
        def queryTag = schema.getAppliedDirectives(query)
                .find { it.name == "tag" }
        def queryTagArgument = schema.getArgument(queryTag, "label")
        def fieldTag = schema.getAppliedDirectives(search)
                .find { it.name == "tag" }
        def fieldTagArgument = schema.getArgument(fieldTag, "label")

        then:
        directive instanceof SUDirective
        directive.repeatable
        directive.validLocations() == EnumSet.of(OBJECT, FIELD_DEFINITION)
        directive.definition.name == "tag"
        directiveArgument.argumentDefaultValue.isLiteral()
        directiveArgument.argumentDefaultValue.value.value == "fallback"
        directiveArgument.definition.name == "label"

        and:
        inputField.inputFieldDefaultValue.isLiteral()
        inputField.inputFieldDefaultValue.value.value == BigInteger.TEN
        fieldArgument.argumentDefaultValue.isLiteral()
        fieldArgument.argumentDefaultValue.value.value == 5

        and:
        queryTag.definition.name == "tag"
        queryTagArgument.argumentValue.isLiteral()
        queryTagArgument.argumentValue.value.value == "query"
        queryTagArgument.definition.name == "label"
        fieldTagArgument.argumentValue.isLiteral()
        fieldTagArgument.argumentValue.value.class.simpleName == "NullValue"
    }

    def "import normalizes intrinsic deprecation and specifiedBy metadata"() {
        given:
        def date = GraphQLScalarType.newScalar(Scalars.GraphQLString)
                .name("Date")
                .specifiedByUrl("https://example.com/date")
                .build()
        def oldField = GraphQLFieldDefinition.newFieldDefinition()
                .name("old")
                .type(Scalars.GraphQLString)
                .deprecate("Use current")
                .build()
        def query = GraphQLObjectType.newObject()
                .name("Query")
                .field(oldField)
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("date")
                        .type(date))
                .build()
        def graphQLSchema = GraphQLSchema.newSchema()
                .query(query)
                .additionalType(date)
                .build()

        when:
        def schema = new SchemaUniverse().importSchema("normalized", graphQLSchema)
        def old = schema.getField(schema.queryType, "old")
        def deprecated = schema.getAppliedDirectives(old, "deprecated")[0]
        def specifiedBy =
                schema.getAppliedDirectives(schema.getScalarType("Date"), "specifiedBy")[0]

        then:
        schema.getArgument(deprecated, "reason").argumentValue.isLiteral()
        schema.getArgument(deprecated, "reason").argumentValue.value.value == "Use current"
        schema.getArgument(specifiedBy, "url").argumentValue.isLiteral()
        schema.getArgument(specifiedBy, "url").argumentValue.value.value ==
                "https://example.com/date"
    }

    def "changing an applied argument replaces its immutable directive occurrence"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def integer = universe.newScalarType("Int")
        def originalArgument = universe.newAppliedDirectiveArgument(
                "version", integer, InputValueWithState.newExternalValue(1))
        def originalDirective =
                universe.newAppliedDirective("tag", [originalArgument])
        def base = universe.newSchema("base")
                .queryType(query)
                .addType(integer)
                .addAppliedDirective(query, originalDirective)
                .build()
        def baseVertexCount = universe.vertexCount

        when:
        def changedArgument = universe.newAppliedDirectiveArgument(
                "version", integer, InputValueWithState.newExternalValue(2))
        def changedDirective =
                universe.newAppliedDirective("tag", [changedArgument])
        def changed = base.transform("changed", builder -> builder
                .removeAppliedDirective(query, originalDirective)
                .addAppliedDirective(query, changedDirective))

        then:
        base.getAppliedDirectives(query) == [originalDirective]
        changed.getAppliedDirectives(query) == [changedDirective]
        base.getArgument(originalDirective, "version").argumentValue.value == 1
        changed.getArgument(changedDirective, "version").argumentValue.value == 2
        base.getType(originalArgument).is(integer)
        changed.getType(changedArgument).is(integer)

        and:
        universe.vertexCount == baseVertexCount + 2
        changed.storedEdgeCount == base.storedEdgeCount
    }

    def "applied directive payload rejects duplicate names and foreign universes"() {
        given:
        def first = new SchemaUniverse()
        def second = new SchemaUniverse()
        def firstString = first.newScalarType("String")
        def secondString = second.newScalarType("String")
        def firstArgument = first.newAppliedDirectiveArgument("value", firstString)
        def duplicateArgument = first.newAppliedDirectiveArgument("value", firstString)
        def foreignArgument = second.newAppliedDirectiveArgument("value", secondString)

        when:
        first.newAppliedDirective("tag", [firstArgument, duplicateArgument])

        then:
        thrown(AssertException)

        when:
        first.newAppliedDirective("foreign", [foreignArgument])

        then:
        thrown(AssertException)
    }

    def "an imported schema can be exported with the same represented structure"() {
        given:
        def graphQLSchema = TestUtil.schema('''
            directive @tag(label: String = "fallback") repeatable on SCHEMA | OBJECT | FIELD_DEFINITION

            scalar Date

            interface Node {
                id: ID!
            }

            type User implements Node @tag(label: "user") {
                id: ID!
                name: String @tag
            }

            enum Status {
                ACTIVE
                OLD @deprecated(reason: "legacy")
            }

            input Filter {
                limit: Int = 10
                status: Status
            }

            union Result = User

            schema @tag(label: "schema") {
                query: Root
            }

            type Root {
                search(filter: Filter, at: Date): [Result!]!
            }
        ''')
        def universe = new SchemaUniverse()
        def schema = universe.importSchema("source", graphQLSchema)

        when:
        def exported = schema.toGraphQLSchema()

        then:
        canonicalSchema(exported) == canonicalSchema(graphQLSchema)
        ((GraphQLInputObjectType) exported.getType("Filter"))
                .getField("limit")
                .inputFieldDefaultValue
                .isLiteral()
        exported.getSchemaAppliedDirectives("tag")[0]
                .getArgument("label")
                .argumentValue
                .isLiteral()
    }

    def "typed helpers cover the complete schema topology"() {
        given:
        def graphQLSchema = TestUtil.schema('''
            directive @tag(label: String) repeatable on SCHEMA | OBJECT | FIELD_DEFINITION

            schema @tag(label: "schema") {
                query: Query
                mutation: Mutation
                subscription: Subscription
            }

            interface Node {
                id: ID!
            }

            interface Resource implements Node {
                id: ID!
            }

            type User implements Node & Resource @tag(label: "one") @tag(label: "two") {
                id: ID!
                name: String @tag(label: "field")
            }

            union SearchResult = User

            enum Role {
                ADMIN
                USER
            }

            input Filter {
                role: Role
            }

            type Query {
                user(filter: Filter): User
                users: [User!]!
                search: SearchResult
            }

            type Mutation {
                noop: Boolean
            }

            type Subscription {
                changed: Boolean
            }
        ''')
        def universe = new SchemaUniverse()

        when:
        def schema = universe.importSchema("helpers", graphQLSchema)
        def query = schema.queryType
        def user = schema.getObjectType("User")
        def node = schema.getInterfaceType("Node")
        def resource = schema.getInterfaceType("Resource")
        def searchResult = schema.getUnionType("SearchResult")
        def role = schema.getEnumType("Role")
        def filter = schema.getInputObjectType("Filter")
        def tag = schema.getDirectiveDefinition("tag")

        then:
        schema.rootTypes*.name == ["Query", "Mutation", "Subscription"]
        schema.types.containsAll([query, user, node, resource, searchResult, role, filter])
        schema.getType("Query").is(query)
        schema.getType("missing") == null
        schema.getObjectType("Role") == null
        schema.getScalarType("String").name == "String"
        schema.types.contains(user)

        and:
        schema.getFields(user)*.name == ["id", "name"]
        schema.getField(user, "name").name == "name"
        schema.getFields(node)*.name == ["id"]
        schema.getField(node, "id").name == "id"

        and:
        schema.getInterfaces(user)*.name == ["Node", "Resource"]
        schema.getInterface(user, "Node").is(node)
        schema.getInterfaces(resource)*.name == ["Node"]
        schema.getInterface(resource, "Node").is(node)

        and:
        schema.getUnionMembers(searchResult) == [user]
        schema.getUnionMember(searchResult, "User").is(user)
        schema.getEnumValues(role)*.name == ["ADMIN", "USER"]
        schema.getEnumValue(role, "ADMIN").name == "ADMIN"
        schema.getInputFields(filter)*.name == ["role"]
        schema.getInputField(filter, "role").name == "role"
        schema.getType(schema.getInputField(filter, "role")).is(role)

        and:
        def userField = schema.getField(query, "user")
        def filterArgument = schema.getArgument(userField, "filter")
        schema.getArguments(userField) == [filterArgument]
        schema.getType(userField).is(user)
        schema.getType(filterArgument).is(filter)

        and:
        def usersField = schema.getField(query, "users")
        def outerNonNull = schema.getType(usersField) as SUNonNullType
        def list = schema.getWrappedType(outerNonNull) as SUListType
        def innerNonNull = schema.getWrappedType(list) as SUNonNullType
        schema.getWrappedType(innerNonNull).is(user)

        and:
        schema.directiveDefinitions.contains(tag)
        schema.getArguments(tag)*.name == ["label"]
        schema.getArgument(tag, "label").name == "label"
        schema.getType(schema.getArgument(tag, "label")).name == "String"

        and:
        schema.schemaAppliedDirectives.size() == 1
        def schemaTag = schema.schemaAppliedDirectives[0]
        schema.getSchemaAppliedDirectives("tag") == [schemaTag]
        schema.getArgument(schemaTag, "label").argumentValue.value.value == "schema"
        schema.getType(schema.getArgument(schemaTag, "label")).name == "String"

        and:
        def userTags = schema.getAppliedDirectives(user, "tag")
        userTags.size() == 2
        userTags.collect { schema.getArgument(it, "label").argumentValue.value.value } == ["one", "two"]
        schema.getArguments(userTags[0])*.name == ["label"]
        schema.getAppliedDirectives(schema.getField(user, "name"), "tag").size() == 1
    }

    def "large snapshots share every unchanged source adjacency"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def string = universe.newScalarType("String")
        def integer = universe.newScalarType("Int")
        def fields = (0..<1024).collect { universe.newField("field${it}") }
        def builder = universe.newSchema("large").queryType(query)
        fields.each { field ->
            builder.addField(query, field)
            builder.setFieldType(field, string)
        }
        def base = builder.build()

        when:
        def changed = base.transform("changed", transform -> {
            for (int i = 0; i < fields.size(); i += 127) {
                transform.setFieldType(fields[i], integer)
            }
        })

        then:
        base.getFields(query).size() == 1024
        changed.getFields(query).size() == 1024
        base.sharesOutgoingEdgesWith(changed, query)

        and:
        fields.indices.every { index ->
            def changedSource = index % 127 == 0
            base.sharesOutgoingEdgesWith(changed, fields[index]) != changedSource
        }
    }

    private static String canonicalSchema(GraphQLSchema schema) {
        def options = SchemaPrinter.Options.defaultOptions()
                .includeSchemaDefinition(true)
                .includeScalarTypes(true)
                .setComparators(GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY)
        return new SchemaPrinter(options).print(schema)
    }
}
