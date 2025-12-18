package graphql.schema.transform

import graphql.Scalars
import graphql.TestUtil
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLDirectiveContainer
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.SchemaTraverser
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLTypeReference.typeRef

class FieldVisibilitySchemaTransformationTest extends Specification {

    def visibilitySchemaTransformation = new FieldVisibilitySchemaTransformation({ environment ->
        def directives = (environment.schemaElement as GraphQLDirectiveContainer).appliedDirectives
        return directives.find({ directive -> directive.name == "private" }) == null
    })

    def "can remove a private field"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
    }

    def "can remove a type associated with a private field"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
            secrets: SuperSecretCustomerData
            otherBillingStatus: BillingStatus
        }
        
        type SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }

    def "removes concrete types referenced only by interface"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: SuperSecretCustomerData @private
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            secrets: SuperSecretCustomerData
            otherBillingStatus: BillingStatus
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }

    def "interface and its implementations that have both private and public reference is retained"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: SuperSecretCustomerData @private
            billingStatus2: SuperSecretCustomerData 
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            secrets: SuperSecretCustomerData
            otherBillingStatus: BillingStatus
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("SuperSecretCustomerData") != null
        restrictedSchema.getType("BillingStatus") != null
    }


    def "types with both private and public references are retained"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            public: Foo
            private: Bar @private
        }
        
        type Foo {
            x: X
        }
        
        type Bar {
            x: X
            bar2: Bar2
        }
        
        type Bar2 {
            id: Int
        }
        
        type X {
            x2: X2
        }
        
        type X2 {
            id: Int 
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("Bar") == null
        restrictedSchema.getType("Bar2") == null
        restrictedSchema.getType("X") != null
        restrictedSchema.getType("Foo") != null
        restrictedSchema.getType("X2") != null
    }

    def "union types"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            privateFooOrBar: FooOrBar @private
            privateBar: Bar @private
            privateFoo: Foo @private
            public: Foo
        }
        
        union FooOrBar = Foo | Bar
        
        type Foo {
            id: ID
        }
        
        type Bar {
            id: ID
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("privateFooOrBar") == null
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("privateBar") == null
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("privateFoo") == null
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("public") != null
        restrictedSchema.getType("FooOrBar") == null
        restrictedSchema.getType("Bar") == null
        restrictedSchema.getType("Foo") != null
    }

    def "union type with reference by private interface removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            public: Bar
            private: Baz @private
        }
        
        type Bar {
            id: ID
        }
        
        interface Baz {
            fooOrBar: FooOrBar
        }
        
        type Bing implements Baz {
            fooOrBar: FooOrBar
        }
        union FooOrBar = Foo | Bar
        
        type Foo {
            id: ID
        }
        
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("private") == null
        restrictedSchema.getType("Foo") == null
        restrictedSchema.getType("Bar") != null
        restrictedSchema.getType("Baz") == null
        restrictedSchema.getType("Bing") == null
        restrictedSchema.getType("FooOrBar") == null
    }


    def "leaves concrete types referenced only by interfaces"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: SuperSecretCustomerData 
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") != null
        restrictedSchema.getType("SuperSecretCustomerData") != null
    }

    def "leaves interface types referenced only by concrete types"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") != null
        restrictedSchema.getType("SuperSecretCustomerData") != null
    }


    def "removes interface types implemented by types used in a private field"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            cardLast4: Int
        }
        
        """)


        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }

    def "leaves interface type if has private and public reference"() {

        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
            accountStatus: AccountStatus 
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        type AccountStatus implements SuperSecretCustomerData {
            accountNumber: String
            inGoodStanding: Boolean
        }
        
        interface SuperSecretCustomerData {
            accountNumber: String
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") != null
    }

    def "leaves concrete type if has public and private"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
            publicBillingStatus: BillingStatus
        }
        
        type BillingStatus implements SuperSecretCustomerData {
            accountNumber: String
            cardLast4: Int
        }
        
        interface SuperSecretCustomerData {
            accountNumber: String
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("publicBillingStatus") != null
        restrictedSchema.getType("BillingStatus") != null
    }

    def "removes interface type if only private reference with multiple interfaces"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account implements PublicView {
            name: String
            billingStatus: BillingStatus @private
            country: String
        }
        
        type BillingStatus implements SuperSecretCustomerData & Billable & PublicView {
            accountNumber: String
            cardLast4: Int
            country: String
        }
        
        interface SuperSecretCustomerData {
            accountNumber: String
        }
        
        interface Billable {
            cardLast4: Int
        }
        
        interface PublicView {
            country: String
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
        restrictedSchema.getType("Billable") == null
        restrictedSchema.getType("PublicView") != null
    }

    def "primitive types are retained"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        # only String and Boolean types have other references (in the introspection query)
        type Query {
            bar: String @private
            baz: Boolean @private
            placeholderField: Int
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("String") != null
        restrictedSchema.getType("Boolean") != null
    }

    def "root types with different names are supported"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        schema {
           query: FooQuery
        }

        type FooQuery {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
    }

    def "fields and types are removed from subscriptions and mutations"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        schema {
           query: Query
           mutation: Mutation
           subscription: Subscription
        }
        
        type Query {
            something: String
        }

        type Mutation {
            setFoo(foo: String): Foo @private
            placeholderField: Int
        }
        
        type Subscription {
            barAdded: Bar @private
            placeholderField: Int
        }
        
        type Foo {
            foo: String
        }
        
        type Bar {
            bar: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("Foo") == null
        restrictedSchema.getType("Bar") == null
    }

    def "type with both private and public transitive references is retained"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            private: Foo # calls Baz privately
            public: Bar # calls Baz publicly
        }
        
        type Foo {
            baz: Baz @private
            placeholderField: Int
        }
        
        type Bar {
            baz: Baz
        }
        
        type Baz {
            bing: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("baz") == null
        restrictedSchema.getType("Baz") != null
    }

    def "type with multiple private parent references is removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo
            bar: Bar
        }
        
        type Foo {
            baz: Baz @private
            placeholderField: Int
        }
        
        type Bar {
            baz: Baz @private
            placeholderField: Int
        }
        
        type Baz {
            bing: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("baz") == null
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("baz") == null
        restrictedSchema.getType("Baz") == null
    }

    def "type with multiple private grandparent references is removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo @private
            bar: Bar @private
            placeholderField: Int
        }
        
        type Foo {
            baz: Baz 
        }
        
        type Bar {
            baz: Baz
        }
        
        type Baz {
            bing: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        restrictedSchema.getType("Foo") == null
        restrictedSchema.getType("Bar") == null
        restrictedSchema.getType("Baz") == null
    }

    def "type with circular reference can be traversed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Foo {
            foo2: Foo @private
            bar: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("foo2") == null
        restrictedSchema.getType("Foo") != null
    }

    def "input types can have private fields"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Mutation {
            setFoo(fooInput: FooInput): Foo 
        }
        
        type Foo {
            id: ID
        }
        
        input FooInput {
            foo: BarInput @private
            bar: String
        }
        
        input BarInput {
            id: ID
        }
               
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("FooInput") as GraphQLInputObjectType).getFieldDefinition("foo") == null
        restrictedSchema.getType("FooInput") != null
        restrictedSchema.getType("BarInput") == null
    }

    def "enum types can be removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Mutation {
            setFoo(fooInput: FooInput): Foo 
        }
        
        type Foo {
            id: ID
            fooEnum: FooEnum @private
        }
        
        input FooInput {
            foo: BarEnum @private
            bar: String
        }
        
        enum BarEnum {
            FOO
            BAR
            BAZ
        }
        
        enum FooEnum {
            BING
            BOO
        }
               
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("FooInput") as GraphQLInputObjectType).getFieldDefinition("foo") == null
        restrictedSchema.getType("FooInput") != null
        restrictedSchema.getType("BarEnum") == null
        restrictedSchema.getType("FooEnum") == null
    }

    def "unreferenced types can have fields removed, and the referenced types must be removed as well if they are not used"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Foo {
            id: ID
        }
        
        type Bar {
            baz: String
            bing: Bing @private
        }
        
        type Bing {
            id: ID
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Bar.bing field must have been removed"
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("bing") == null

        and: "since Bing is not used anywhere else, it should be removed"
        restrictedSchema.getType("Bing") == null
    }

    def "unreferenced types can have fields removed, and referenced type must not be removed if used elsewhere in the connected graph"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo 
            zinc: Bing
        }
        
        type Foo {
            id: ID
        }
        
        type Bar {
            baz: String
            bing: Bing @private
        }
        
        type Bing {
            id: ID
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Bar.bing field must have been removed"
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("bing") == null

        and: "since Bing is used in the connected graph, it MUST not be removed"
        restrictedSchema.getType("Bing") != null
    }

    def "unreferenced types can have fields removed, and referenced type must not be removed if used elsewhere"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION
        
        type Query {
            foo: Foo 
        }
        
        type Foo {
            id: ID
        }
        
        type Bar {
            baz: String
            foo: Bing
            bing: Bing @private
        }
        
        type Bing {
            id: ID
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Bar.bing field must have been removed"
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("bing") == null

        and: "since Bing is used elsewhere, it SHOULD not be removed"
        restrictedSchema.getType("Bing") != null
    }

    def "use type references - private field declared with interface type removes both concrete and interface"() {
        given:
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("account").type(typeRef("Account")).build())
                .build()

        def privateDirective = GraphQLAppliedDirective.newDirective().name("private").build()
        def account = newObject()
                .name("Account")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString).build())
                .field(newFieldDefinition().name("billingStatus").type(typeRef("SuperSecretCustomerData")).withAppliedDirective(privateDirective).build())
                .build()

        def billingStatus = newObject()
                .name("BillingStatus")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .withInterface(typeRef("SuperSecretCustomerData"))
                .build()

        def secretData = newInterface()
                .name("SuperSecretCustomerData")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(secretData, Mock(TypeResolver))
                .build()

        def schema = GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(query)
                .additionalType(billingStatus)
                .additionalType(account)
                .additionalType(billingStatus)
                .additionalType(secretData)
                .build()
        when:

        System.out.println((new SchemaPrinter()).print(schema))
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }


    def "use type references - private field declared with concrete type removes both concrete and interface"() {
        given:
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("account").type(typeRef("Account")).build())
                .build()

        def privateDirective = GraphQLAppliedDirective.newDirective().name("private").build()
        def account = newObject()
                .name("Account")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString).build())
                .field(newFieldDefinition().name("billingStatus").type(typeRef("BillingStatus")).withAppliedDirective(privateDirective).build())
                .build()

        def billingStatus = newObject()
                .name("BillingStatus")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .withInterface(typeRef("SuperSecretCustomerData"))
                .build()

        def secretData = newInterface()
                .name("SuperSecretCustomerData")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(secretData, Mock(TypeResolver))
                .build()

        def schema = GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(query)
                .additionalType(billingStatus)
                .additionalType(account)
                .additionalType(billingStatus)
                .additionalType(secretData)
                .build()
        when:

        System.out.println((new SchemaPrinter()).print(schema))
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
        restrictedSchema.getType("SuperSecretCustomerData") == null
    }

    def "use type references - unreferenced types are removed"() {
        given:
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("account").type(typeRef("Account")).build())
                .build()

        def privateDirective = GraphQLAppliedDirective.newDirective().name("private").build()
        def account = newObject()
                .name("Account")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString).build())
                .field(newFieldDefinition().name("billingStatus").type(typeRef("BillingStatus")).withAppliedDirective(privateDirective).build())
                .build()

        def billingStatus = newObject()
                .name("BillingStatus")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .build()

        def schema = GraphQLSchema.newSchema()
                .query(query)
                .additionalType(billingStatus)
                .additionalType(account)
                .build()
        when:

        System.out.println((new SchemaPrinter()).print(schema))
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
    }

    def "before and after transformation hooks are run"() {

        given:
        def callbacks = []

        def visibilitySchemaTransformation = new FieldVisibilitySchemaTransformation({ environment ->
            def directives = (environment.schemaElement as GraphQLDirectiveContainer).appliedDirectives
            return directives.find({ directive -> directive.name == "private" }) == null
        }, { -> callbacks << "before" }, { -> callbacks << "after" })

        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
        }
        """)

        when:
        visibilitySchemaTransformation.apply(schema)

        then:
        callbacks.containsAll(["before", "after"])
    }

    def "handles types that become visible via types reachable by interface only"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
            node: Node
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
        }
       
        interface Node {
            id: ID!
        } 
        
        type Billing implements Node {
            id: ID!
            status: BillingStatus
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") != null
    }

    def "handles types that become visible via types reachable by interface that implements interface"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
            node: Node
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private
        }
        
        type BillingStatus {
            accountNumber: String
        }
       
        interface Node {
            id: ID!
        } 
        
        interface NamedNode implements Node {
            id: ID!
            name: String 
        }
        
        type Billing implements Node & NamedNode {
            id: ID!
            name: String
            status: BillingStatus
        }
        
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") != null
    }

    def "can remove a field with a directive containing enum argument"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private(privateType: SecretType) on FIELD_DEFINITION
        enum SecretType {
            SUPER_SECRET
            NOT_SO_SECRET
        }

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private(privateType: NOT_SO_SECRET)
        }
        
        type BillingStatus {
            accountNumber: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
    }

    def "can remove a field with a directive containing type argument"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private(privateType: SecretType) on FIELD_DEFINITION
        input SecretType {
            description: String
        }

        type Query {
            account: Account
        }
        
        type Account {
            name: String
            billingStatus: BillingStatus @private(privateType: { description: "secret" })
        }
        
        type BillingStatus {
            accountNumber: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        restrictedSchema.getType("BillingStatus") == null
    }

    def "issue 4191 - specific problem on field visibility"() {

        Runtime runtime = Runtime.getRuntime();

        long totalMemory = runtime.totalMemory(); // Current committed memory (bytes)
        long freeMemory = runtime.freeMemory();   // Free memory within the committed memory (bytes)
        long maxMemory = runtime.maxMemory();     // Maximum possible heap size (-Xmx value, in bytes)
        long usedMemory = totalMemory - freeMemory; // Currently used memory (bytes)

        System.out.println("Used Memory: " + usedMemory / (1024 * 1024) + " MB");
        System.out.println("Free Memory: " + freeMemory / (1024 * 1024) + " MB");
        System.out.println("Total Memory: " + totalMemory / (1024 * 1024) + " MB");
        System.out.println("Max Memory: " + maxMemory / (1024 * 1024) + " MB");

        def runtimeWiring = RuntimeWiring.MOCKED_WIRING

        def visibilitySchemaTransformation = new FieldVisibilitySchemaTransformation({ environment ->
            def schemaElement = environment.schemaElement
            if (!schemaElement instanceof GraphQLFieldDefinition) {
                return true
            }
            def parent = environment.parentElement as GraphQLFieldsContainer
            if (parent.name == "Query" && schemaElement.name == "admin_effectiveRoleAssignmentsByPrincipal") {
                return false
            }
            if (parent.name == "Query" && schemaElement.name == "admin_group") {
                return false
            }
            return true
        })

        def schema = TestUtil.schemaFile("4191/4191-smaller.graphqls", runtimeWiring)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)
        def schemaGenerator = new SchemaGenerator()


        def fromPrintedSchema = schemaGenerator.makeExecutableSchema(
                new SchemaParser().parse(new SchemaPrinter().print(restrictedSchema)),
                runtimeWiring
        )
        then:
        noExceptionThrown()
        fromPrintedSchema != null
    }

    def "issue 4191 - specific problem on field visibility - smaller reproduction"() {
        def schema = TestUtil.schemaFile("4191/4191-staging-raw-combined.graphqls", RuntimeWiring.MOCKED_WIRING)

        def listOfElements = fieldReferenced(schema, [FieldCoordinates.coordinates("Query", "admin_effectiveRoleAssignmentsByPrincipal"),
                                 FieldCoordinates.coordinates("Query", "admin_group")])

        def printer = new SchemaPrinter()
        for (GraphQLSchemaElement element  : listOfElements) {
            println(printer.print(element))
        }


        def visibilitySchemaTransformation = new FieldVisibilitySchemaTransformation({ environment ->
            def schemaElement = environment.schemaElement
            if (!schemaElement instanceof GraphQLFieldDefinition) {
                return true
            }
            def parent = environment.parentElement as GraphQLFieldsContainer
            if (parent.name == "Query" && schemaElement.name == "admin_effectiveRoleAssignmentsByPrincipal") {
                return false
            }
            if (parent.name == "Query" && schemaElement.name == "admin_group") {
                return false
            }
            return true
        })

        schema = TestUtil.schema("type Query { foo : String } ")

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)
        def schemaGenerator = new SchemaGenerator()


        def fromPrintedSchema = schemaGenerator.makeExecutableSchema(
                new SchemaParser().parse(new SchemaPrinter().print(restrictedSchema)),
                RuntimeWiring.MOCKED_WIRING
        )
        then:
        noExceptionThrown()
        fromPrintedSchema != null
    }

    private List<GraphQLSchemaElement> fieldReferenced(GraphQLSchema schema, List<FieldCoordinates> coordinates) {

        def setOfNamedElements = new HashSet<String>()
        def setOfElements = new HashSet<GraphQLSchemaElement>()
        coordinates.forEach {
            def fieldDefinition = schema.getFieldDefinition(it)
            def result = new SchemaTraverser().depthFirst(new GraphQLTypeVisitorStub() {

                @Override
                protected TraversalControl visitGraphQLType(GraphQLSchemaElement node, TraverserContext<GraphQLSchemaElement> context) {
                    if (node instanceof GraphQLType) {
                        GraphQLType type = GraphQLTypeUtil.unwrapAllAs(node)
                        if (type instanceof GraphQLNamedType) {
                            def name = ((GraphQLNamedType) type).getName()
                            if (!setOfNamedElements.contains(name)) {
                                setOfNamedElements.add(name)
                                setOfElements.add(type)
                            }
                        }
                    }
                    if (node instanceof GraphQLDirective) {
                        String name = ((GraphQLDirective) node).getName()
                        if (!setOfNamedElements.contains(name)) {
                            setOfNamedElements.add(name)
                            setOfElements.add(node)
                        }
                    }
                    return super.visitGraphQLType(node, context)
                }

                @Override
                TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
                    return super.visitGraphQLInterfaceType(node, context)
                }
            }, fieldDefinition)
        }
        return new ArrayList<GraphQLSchemaElement>(setOfElements)
    }
}
