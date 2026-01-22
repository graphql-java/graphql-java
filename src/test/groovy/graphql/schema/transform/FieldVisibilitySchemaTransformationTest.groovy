package graphql.schema.transform

import graphql.Scalars
import graphql.TestUtil
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLDirectiveContainer
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.TypeResolver
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

        then: "BillingStatus and SuperSecretCustomerData were originally reachable from roots (via Account.billingStatus)"
        and: "After the private field is removed, they become unreachable and are removed"
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
        restrictedSchema.getType("Bar") != null

        and: "Baz, Bing, FooOrBar, Foo were originally reachable from roots via Query.private"
        and: "After the private field is removed, they become unreachable and are removed"
        restrictedSchema.getType("Foo") == null
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

        then: "Bar is preserved as a root unused type (additional type not reachable from roots)"
        restrictedSchema.getType("Bar") != null

        and: "Bar.bing field must have been removed"
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("bing") == null

        and: "Bing is also an additional type not reachable from roots, so it is preserved"
        restrictedSchema.getType("Bing") != null
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

        then: "Bar is preserved as a root unused type"
        restrictedSchema.getType("Bar") != null

        and: "Bar.bing field must have been removed"
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

        then: "Bar is preserved as a root unused type"
        restrictedSchema.getType("Bar") != null

        and: "Bar.bing field must have been removed"
        (restrictedSchema.getType("Bar") as GraphQLObjectType).getFieldDefinition("bing") == null

        and: "since Bing is used elsewhere (Bar.foo), it SHOULD not be removed"
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

        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        (restrictedSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null

        and: "BillingStatus and SuperSecretCustomerData were originally reachable via Account.billingStatus"
        and: "After the private field is removed, they become unreachable and are removed"
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

    def "remove all fields from a type which is referenced via additional types"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
        directive @private on FIELD_DEFINITION
        type Query {
         foo: Foo
        }
        type Foo {
         foo: String
         toDelete: ToDelete @private
        } 
        type ToDelete {
         toDelete:String @private
        }
        """)

        when:
        schema.typeMap
        def patchedSchema = schema.transform { builder ->
            schema.typeMap.each { entry ->
                def type = entry.value
                if (type != schema.queryType && type != schema.mutationType && type != schema.subscriptionType) {
                    builder.additionalType(type)
                }
            }
        }
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(patchedSchema)
        then:
        (restrictedSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("toDelete") == null
    }

    def "remove field from a type which is referenced via additional types and an additional not reachable child is deleted"() {
        given:
        /*
        the test case here is that ToDelete is changed, because ToDelete.toDelete is deleted
        and additionally Indirect needs to be deleted because it is not reachable via the
        Query type anymore.
        We had a bug where ToDeleted was not deleted correctly, but because Indirect was, it resulted
        in an invalid schema and exception.
        */
        GraphQLSchema schema = TestUtil.schema("""
        directive @private on FIELD_DEFINITION
        type Query {
         foo: String
         toDelete: ToDelete @private
        }
        type ToDelete {
         bare: String @private
         toDelete:[Indirect!] 
        }
        type Indirect {
          foo: String
        }
        """)

        when:
        schema.typeMap
        def patchedSchema = schema.transform { builder ->
            schema.typeMap.each { entry ->
                def type = entry.value
                if (type != schema.queryType && type != schema.mutationType && type != schema.subscriptionType) {
                    builder.additionalType(type)
                }
            }
        }
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(patchedSchema)
        then:
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("toDelete") == null
    }


    def "remove all fields from an input type which is referenced via additional types"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
        directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
        type Query {
         foo(input: Input): String
        }
        input Input {
         foo: String
         toDelete:ToDelete @private
        }
        input ToDelete {
         toDelete:String @private
        }
        """)

        when:
        schema.typeMap
        def patchedSchema = schema.transform { builder ->
            schema.typeMap.each { entry ->
                def type = entry.value
                if (type != schema.queryType && type != schema.mutationType && type != schema.subscriptionType) {
                    builder.additionalType(type)
                }
            }
        }
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(patchedSchema)
        then:
        (restrictedSchema.getType("Input") as GraphQLInputObjectType).getFieldDefinition("toDelete") == null
    }

    /**
     * This test verifies the fix for issue 4133 with FieldVisibilitySchemaTransformation.
     * <p>
     * <h3>The Problem (Fixed)</h3>
     * When a field is marked @private and deleted, the traversal doesn't continue to children of 
     * deleted nodes. Types only reachable through deleted fields were not being visited, and those
     * unvisited types with circular references using GraphQLTypeReference would cause errors during
     * schema rebuild.
     * <p>
     * <h3>Schema Structure</h3>
     * <ul>
     *   <li>{@code Query.rental @private} → Rental (would not be visited because parent field deleted)</li>
     *   <li>{@code Query.customer} → Customer (visited)</li>
     *   <li>{@code Customer.rental} → TypeReference("Rental") (placeholder, doesn't cause Rental to be visited)</li>
     *   <li>{@code Rental.customer} → Customer (actual object reference)</li>
     *   <li>{@code Customer.payment @private} → Payment (deleted, Payment would not be visited)</li>
     *   <li>{@code Payment.inventory} → TypeReference("Inventory")</li>
     * </ul>
     * <p>
     * <h3>The Fix</h3>
     * {@code FieldVisibilitySchemaTransformation} now uses {@code SchemaTransformer.transformSchemaWithDeletes()}
     * which ensures all types are visited by temporarily adding them as extra root types during transformation.
     */
    def "issue 4133 - circular references with private fields - fixed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            rental: Rental @private
            customer: Customer
        }
        
        type Customer {
            rental: Rental
            payment: Payment @private
        }
        
        type Rental {
            id: ID
            customer: Customer @private
        }
        
        type Payment {
            inventory: Inventory @private
        }
        
        type Inventory {
            payment: Payment @private
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then:
        // Query should only have customer field (rental is private)
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("rental") == null
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("customer") != null

        // Customer should only have rental field (payment is private)
        (restrictedSchema.getType("Customer") as GraphQLObjectType).getFieldDefinition("rental") != null
        (restrictedSchema.getType("Customer") as GraphQLObjectType).getFieldDefinition("payment") == null

        // Rental should only have id field (customer is private)
        (restrictedSchema.getType("Rental") as GraphQLObjectType).getFieldDefinition("id") != null
        (restrictedSchema.getType("Rental") as GraphQLObjectType).getFieldDefinition("customer") == null
    }

    def "originally unused type subgraph is fully preserved with private field removal"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            account: Account
        }
        
        type Account {
            name: String
        }
        
        # This is an originally unused subgraph - not reachable from Query
        type UnusedRoot {
            id: ID
            child: UnusedChild
            privateField: PrivateOnlyType @private
        }
        
        type UnusedChild {
            value: String
            grandchild: UnusedGrandchild
        }
        
        type UnusedGrandchild {
            data: Int
        }
        
        type PrivateOnlyType {
            secret: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Query and Account are preserved as normal"
        restrictedSchema.getType("Query") != null
        restrictedSchema.getType("Account") != null

        and: "Originally unused root type is preserved"
        restrictedSchema.getType("UnusedRoot") != null

        and: "Private field on unused root is removed"
        (restrictedSchema.getType("UnusedRoot") as GraphQLObjectType).getFieldDefinition("privateField") == null

        and: "Non-private fields on unused root are preserved"
        (restrictedSchema.getType("UnusedRoot") as GraphQLObjectType).getFieldDefinition("id") != null
        (restrictedSchema.getType("UnusedRoot") as GraphQLObjectType).getFieldDefinition("child") != null

        and: "Types reachable from preserved unused root are preserved"
        restrictedSchema.getType("UnusedChild") != null
        restrictedSchema.getType("UnusedGrandchild") != null

        and: "PrivateOnlyType is also an additional type not reachable from roots, so it is preserved"
        restrictedSchema.getType("PrivateOnlyType") != null
    }

    def "multiple originally unused type subgraphs are all preserved"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            main: MainType
        }
        
        type MainType {
            id: ID
        }
        
        # First unused subgraph
        type UnusedA {
            aValue: String
            aChild: UnusedAChild
        }
        
        type UnusedAChild {
            aChildValue: Int
        }
        
        # Second unused subgraph
        type UnusedB {
            bValue: String
            bChild: UnusedBChild
        }
        
        type UnusedBChild {
            bChildValue: Int
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "First unused subgraph is fully preserved"
        restrictedSchema.getType("UnusedA") != null
        restrictedSchema.getType("UnusedAChild") != null

        and: "Second unused subgraph is fully preserved"
        restrictedSchema.getType("UnusedB") != null
        restrictedSchema.getType("UnusedBChild") != null
    }

    def "findRootUnusedTypes considers interface implementations as reachable from roots"() {
        given:
        // This test verifies that interface implementations are correctly identified as 
        // reachable from roots when finding root unused types, not just preserved by accident
        GraphQLSchema schema = TestUtil.schema("""

        directive @private on FIELD_DEFINITION

        type Query {
            node: Node
        }

        interface Node {
            id: ID!
        }

        # NodeImpl implements Node and is reachable via interface
        # It should be considered reachable from roots, NOT a root unused type
        type NodeImpl implements Node {
            id: ID!
            data: String
        }

        # TrulyUnused is not connected to Query at all - it IS a root unused type
        type TrulyUnused {
            value: String
        }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "NodeImpl is reachable from Query via Node interface, so it should be preserved"
        restrictedSchema.getType("Node") != null
        restrictedSchema.getType("NodeImpl") != null

        and: "TrulyUnused is an additional type not reachable from roots, so it is preserved as root unused type"
        restrictedSchema.getType("TrulyUnused") != null
    }

    def "findRootUnusedTypes ignores special introspection types starting with underscore"() {
        given:
        // This test verifies that types starting with "_" (like _AppliedDirective) are ignored
        // when finding root unused types, matching the behavior of TypeRemovalVisitor
        // We use IntrospectionWithDirectivesSupport which adds real "_" types to the schema
        def baseSchema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION
            directive @example on FIELD_DEFINITION
            
            type Query {
                publicField: String @example
                privateField: SecretData @private
            }
            
            type SecretData {
                secret: String
            }
        """)

        // Apply IntrospectionWithDirectivesSupport which adds _AppliedDirective and _DirectiveArgument types
        def schema = new graphql.introspection.IntrospectionWithDirectivesSupport().apply(baseSchema)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Private field and its type should be removed"
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("privateField") == null
        restrictedSchema.getType("SecretData") == null

        and: "Public field should be preserved"
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("publicField") != null

        and: "Special introspection types starting with _ should be preserved"
        restrictedSchema.getType("_AppliedDirective") != null
        restrictedSchema.getType("_DirectiveArgument") != null
    }

    def "custom scalar types are removed when only referenced by private fields"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION
            
            scalar CustomDate
            scalar SecretToken
            
            type Query {
                publicDate: CustomDate
                secretToken: SecretToken @private
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Private field should be removed"
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("secretToken") == null

        and: "Public field should be preserved"
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("publicDate") != null

        and: "CustomDate scalar is still used by publicDate, so it should be preserved"
        restrictedSchema.getType("CustomDate") != null

        and: "SecretToken scalar is only used by private field, so it should be removed"
        restrictedSchema.getType("SecretToken") == null
    }

    def "originally unused enum types are preserved"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION
            
            type Query {
                status: Status
            }
            
            enum Status {
                ACTIVE
                INACTIVE
            }
            
            # UnusedEnum is not connected to Query - it's an additional type
            enum UnusedEnum {
                VALUE_A
                VALUE_B
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Status enum is used by Query, so it should be preserved"
        restrictedSchema.getType("Status") != null

        and: "UnusedEnum is an additional type not reachable from roots, so it is preserved"
        restrictedSchema.getType("UnusedEnum") != null
    }

    def "originally unused scalar types are preserved"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION
            
            scalar UsedScalar
            scalar UnusedScalar
            
            type Query {
                value: UsedScalar
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "UsedScalar is used by Query, so it should be preserved"
        restrictedSchema.getType("UsedScalar") != null

        and: "UnusedScalar is an additional type not reachable from roots, so it is preserved"
        restrictedSchema.getType("UnusedScalar") != null
    }

    def "enum and scalar types only reachable via private fields are removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION
            
            scalar SecretScalar
            
            enum SecretEnum {
                SECRET_A
                SECRET_B
            }
            
            type Query {
                publicField: String
                secretScalar: SecretScalar @private
                secretEnum: SecretEnum @private
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "Private fields should be removed"
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("secretScalar") == null
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("secretEnum") == null

        and: "Public field should be preserved"
        (restrictedSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("publicField") != null

        and: "SecretScalar and SecretEnum are only reachable via private fields, so they should be removed"
        restrictedSchema.getType("SecretScalar") == null
        restrictedSchema.getType("SecretEnum") == null
    }

    def "input object type only reachable via private field is removed along with nested inputs"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
            
            type Query {
                getValue: String
            }
            
            type Mutation {
                publicAction: String
                privateAction(input: SecretInput): String @private
            }
            
            input SecretInput {
                field1: String
                nested: NestedSecretInput
            }
            
            input NestedSecretInput {
                deepField: String
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "privateAction should be removed"
        (restrictedSchema.getType("Mutation") as GraphQLObjectType).getFieldDefinition("privateAction") == null

        and: "SecretInput and NestedSecretInput should be removed as they're only reachable via private field"
        restrictedSchema.getType("SecretInput") == null
        restrictedSchema.getType("NestedSecretInput") == null
    }

    def "nested input types are removed when parent input field is private"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
            
            type Query {
                getValue: String
            }
            
            type Mutation {
                createItem(input: CreateItemInput): String
            }
            
            input CreateItemInput {
                name: String
                secretData: SecretDataInput @private
            }
            
            input SecretDataInput {
                token: String
                nested: DeepSecretInput
            }
            
            input DeepSecretInput {
                deepSecret: String
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "CreateItemInput should exist but without the secretData field"
        restrictedSchema.getType("CreateItemInput") != null
        (restrictedSchema.getType("CreateItemInput") as GraphQLInputObjectType).getFieldDefinition("name") != null
        (restrictedSchema.getType("CreateItemInput") as GraphQLInputObjectType).getFieldDefinition("secretData") == null

        and: "SecretDataInput and DeepSecretInput should be removed as they're only reachable via private field"
        restrictedSchema.getType("SecretDataInput") == null
        restrictedSchema.getType("DeepSecretInput") == null
    }

    def "originally unused input types are preserved"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
            
            type Query {
                getValue(input: UsedInput): String
            }
            
            input UsedInput {
                field: String
            }
            
            # UnusedInput is not connected to any operation - it's an additional type
            input UnusedInput {
                unusedField: String
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "UsedInput is used by Query, so it should be preserved"
        restrictedSchema.getType("UsedInput") != null

        and: "UnusedInput is an additional type not reachable from roots, so it is preserved"
        restrictedSchema.getType("UnusedInput") != null
    }

    def "input types only reachable via private input fields are removed"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
            
            type Query {
                getValue: String
            }
            
            type Mutation {
                updateItem(input: UpdateInput): String
            }
            
            input UpdateInput {
                publicField: String
                privateRef: PrivateRefInput @private
            }
            
            input PrivateRefInput {
                data: String
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "UpdateInput should exist but without the privateRef field"
        restrictedSchema.getType("UpdateInput") != null
        (restrictedSchema.getType("UpdateInput") as GraphQLInputObjectType).getFieldDefinition("publicField") != null
        (restrictedSchema.getType("UpdateInput") as GraphQLInputObjectType).getFieldDefinition("privateRef") == null

        and: "PrivateRefInput should be removed as it's only reachable via private input field"
        restrictedSchema.getType("PrivateRefInput") == null
    }

    def "input type used by both public and private fields is preserved"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
            
            type Query {
                getValue: String
            }
            
            type Mutation {
                publicAction(input: SharedInput): String
                privateAction(input: SharedInput): String @private
            }
            
            input SharedInput {
                data: String
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "privateAction should be removed"
        (restrictedSchema.getType("Mutation") as GraphQLObjectType).getFieldDefinition("privateAction") == null

        and: "publicAction should be preserved"
        (restrictedSchema.getType("Mutation") as GraphQLObjectType).getFieldDefinition("publicAction") != null

        and: "SharedInput should be preserved because it's still used by publicAction"
        restrictedSchema.getType("SharedInput") != null
    }

    def "input field with nested input referencing enum and scalar"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION
            
            scalar SecretToken
            
            enum SecretLevel {
                LOW
                HIGH
            }
            
            type Query {
                getValue: String
            }
            
            type Mutation {
                createItem(input: ItemInput): String
            }
            
            input ItemInput {
                name: String
                secretConfig: SecretConfigInput @private
            }
            
            input SecretConfigInput {
                token: SecretToken
                level: SecretLevel
            }
        """)

        when:
        GraphQLSchema restrictedSchema = visibilitySchemaTransformation.apply(schema)

        then: "ItemInput should exist but without secretConfig field"
        restrictedSchema.getType("ItemInput") != null
        (restrictedSchema.getType("ItemInput") as GraphQLInputObjectType).getFieldDefinition("name") != null
        (restrictedSchema.getType("ItemInput") as GraphQLInputObjectType).getFieldDefinition("secretConfig") == null

        and: "SecretConfigInput, SecretToken, and SecretLevel should all be removed"
        restrictedSchema.getType("SecretConfigInput") == null
        restrictedSchema.getType("SecretToken") == null
        restrictedSchema.getType("SecretLevel") == null
    }

}
