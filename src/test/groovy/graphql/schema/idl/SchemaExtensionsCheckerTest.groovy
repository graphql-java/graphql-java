package graphql.schema.idl

import graphql.schema.idl.errors.MissingTypeError
import graphql.schema.idl.errors.OperationRedefinitionError
import graphql.schema.idl.errors.OperationTypesMustBeObjects
import graphql.schema.idl.errors.QueryOperationMissingError
import graphql.schema.idl.errors.SchemaProblem
import spock.lang.Specification

class SchemaExtensionsCheckerTest extends Specification {

    static TypeDefinitionRegistry typeRegistry(String sdl) {
        def registry = new SchemaParser().parse(sdl)
        registry
    }

    def "schema def missing query operation and query type"() {
        def sdl = '''
        type Foo { f : String }
        '''

        when:
        def errors = []
        SchemaExtensionsChecker.checkSchemaInvariants(errors, typeRegistry(sdl))

        then:
        !errors.isEmpty()
        errors[0] instanceof QueryOperationMissingError
    }

    def "schema def missing query operation and query type but has extensions but without query op"() {
        def sdl = '''
        type Foo { f : String }
        
        extend schema {
            mutation : Foo
        }
        '''

        when:
        def errors = []
        SchemaExtensionsChecker.checkSchemaInvariants(errors, typeRegistry(sdl))

        then:
        !errors.isEmpty()
        errors[0] instanceof QueryOperationMissingError
    }

    def "schema def missing query operation and has query type"() {
        def sdl = '''
        type Query { f : String }
        '''

        when:
        def errors = []
        SchemaExtensionsChecker.checkSchemaInvariants(errors, typeRegistry(sdl))

        then:
        errors.isEmpty()
    }

    def "schema def missing query operation and query type but has extensions with query op"() {
        def sdl = '''
        type Foo { f : String }
        
        extend schema {
            query : Foo
        }
        '''

        when:
        def errors = []
        SchemaExtensionsChecker.checkSchemaInvariants(errors, typeRegistry(sdl))

        then:
        errors.isEmpty()
    }

    def "schema def present with query operation but extension redefines its"() {
        def sdl = '''
        type Foo { f : String }
        
        schema {
            query : Foo
        }
        
        extend schema {
            query : Foo
        }
        '''

        when:
        typeRegistry(sdl)

        then:
        //parsing picks this up but via SchemaExtensionsChecker
        def schemaProblem = thrown(SchemaProblem)
        schemaProblem.errors[0] instanceof OperationRedefinitionError
    }


    def "operation is not a defined type"() {
        def sdl = '''
        type Foo { f : String }
        
        schema {
            query : Bar
        }
        '''

        when:
        def errors = []
        SchemaExtensionsChecker.checkSchemaInvariants(errors, typeRegistry(sdl))

        then:
        !errors.isEmpty()
        errors[0] instanceof MissingTypeError
    }

    def "operation is not an object type"() {
        def sdl = '''
        input Foo { f : String }
        
        schema {
            query : Foo
        }
        '''

        when:
        def errors = []
        SchemaExtensionsChecker.checkSchemaInvariants(errors, typeRegistry(sdl))

        then:
        !errors.isEmpty()
        errors[0] instanceof OperationTypesMustBeObjects
    }

    def "can gather all the different types"() {
        def sdl = '''
        type Foo { f : String }
        
        type Mutate { f : String }
        
        type Subscribe { f : String }
        
        schema {
            query : Foo
        }
        
        extend schema {
            mutation : Mutate   
        }

        extend schema {
            subscription : Subscribe   
        }
        '''

        when:
        def errors = []
        def typeRegistry = typeRegistry(sdl)
        def operationDefs = SchemaExtensionsChecker.checkSchemaInvariants(errors, typeRegistry)

        then:
        errors.isEmpty()
        operationDefs.size() == 3
        operationDefs.collect { opTypeDef -> opTypeDef.getName() }.contains("query")
        operationDefs.collect { opTypeDef -> opTypeDef.getName() }.contains("mutation")
        operationDefs.collect { opTypeDef -> opTypeDef.getName() }.contains("subscription")
    }
}
