package graphql.schema.bytecode

import graphql.ExecutionInput
import graphql.TestUtil
import spock.lang.Specification

class ByteCodePojoFetchingGeneratorTest extends Specification {

    def "can handle simple pojo"() {
        when:
        def body = ByteCodePojoFetchingGenerator.generateMethodBody(SimplePojo.class)
        then:
        body == """public Object fetch(Object sourceObject, String propertyName) {
   if (sourceObject == null) { return null; }
   graphql.schema.bytecode.SimplePojo source = (graphql.schema.bytecode.SimplePojo) sourceObject;
   if("age".equals(propertyName)) {
      return Integer.valueOf(source.getAge());
   } else if("interesting".equals(propertyName)) {
      return source.getInteresting();
   } else if("names".equals(propertyName)) {
      return source.getNames();
   } else {
      return null;
   }
}
"""
    }

    def "can generate method body for primitive methods"() {

        when:
        def body = ByteCodePojoFetchingGenerator.generateMethodBody(PrimitivePojo.class)
        then:
        body == """public Object fetch(Object sourceObject, String propertyName) {
   if (sourceObject == null) { return null; }
   graphql.schema.bytecode.PrimitivePojo source = (graphql.schema.bytecode.PrimitivePojo) sourceObject;
   if("pbyte".equals(propertyName)) {
      return Integer.valueOf(source.getPbyte());
   } else if("pchar".equals(propertyName)) {
      return Character.valueOf(source.getPchar());
   } else if("pdouble".equals(propertyName)) {
      return Double.valueOf(source.getPdouble());
   } else if("pfloat".equals(propertyName)) {
      return Float.valueOf(source.getPfloat());
   } else if("pint".equals(propertyName)) {
      return Integer.valueOf(source.getPint());
   } else if("plong".equals(propertyName)) {
      return Integer.valueOf(source.getPlong());
   } else if("pshort".equals(propertyName)) {
      return Integer.valueOf(source.getPshort());
   } else if("pbool".equals(propertyName)) {
      return Boolean.valueOf(source.isPbool());
   } else {
      return null;
   }
}
"""
    }

    def "can handle single method pojo"() {
        when:
        def body = ByteCodePojoFetchingGenerator.generateMethodBody(SinglePropertyPojo.class)
        then:
        body == """public Object fetch(Object sourceObject, String propertyName) {
   if (sourceObject == null) { return null; }
   graphql.schema.bytecode.SinglePropertyPojo source = (graphql.schema.bytecode.SinglePropertyPojo) sourceObject;
   if("single".equals(propertyName)) {
      return source.getSingle();
   } else {
      return null;
   }
}
"""
    }

    def "can handle setter methods and void pojo"() {
        when:
        def body = ByteCodePojoFetchingGenerator.generateMethodBody(SettersAndVoidPojo.class)
        then:
        body == """public Object fetch(Object sourceObject, String propertyName) {
   if (sourceObject == null) { return null; }
   graphql.schema.bytecode.SettersAndVoidPojo source = (graphql.schema.bytecode.SettersAndVoidPojo) sourceObject;
   if("name".equals(propertyName)) {
      return source.getName();
   } else {
      return null;
   }
}
"""
    }

    def "can handle empty method pojo"() {
        when:
        ByteCodePojoFetchingGenerator.generateMethodBody(EmptyPropertyPojo.class)
        then:
        thrown(ByteCodePojoFetchingGenerator.NoMethodsException)
    }

    def "can handle non accessible class pojo"() {
        when:
        ByteCodePojoFetchingGenerator.generateMethodBody(NotOpenPojo.class)
        then:
        thrown(ByteCodePojoFetchingGenerator.NoMethodsException)
    }

    def "can handle non accessible methods pojo"() {
        when:
        ByteCodePojoFetchingGenerator.generateMethodBody(NotAccessibleMethodsPojo.class)
        then:
        thrown(ByteCodePojoFetchingGenerator.NoMethodsException)
    }

    def "can generate class for simple pojo"() {
        when:
        def generatedClass = ByteCodePojoFetchingGenerator.generateClassFor(SimplePojo.class)
        then:
        generatedClass.getName() == "graphql.schema.bytecode.graphql.schema.bytecode.Fetcher4graphql.schema.bytecode.SimplePojoGen"
        ByteCodeFetcher.class.isAssignableFrom(generatedClass)

        when:
        // it can be created
        ByteCodeFetcher byteCodeFetcher = generatedClass.newInstance()
        def simplePojo = new SimplePojo(["name1"], 42, false)

        then:

        byteCodeFetcher.fetch(simplePojo, "names") == ["name1"]
        byteCodeFetcher.fetch(simplePojo, "age") == 42
        byteCodeFetcher.fetch(simplePojo, "interesting") == false
        byteCodeFetcher.fetch(simplePojo, "foo") == null
        byteCodeFetcher.fetch(simplePojo, "bar") == null
    }

    def "integration test on a POJO class"() {
        def sdl = """
            type Query {
                simplePojo : SimplePojo
            }
            
            type SimplePojo {
                names : [String]
                age : Int
                interesting: Boolean
                foo : ID
            }
                    
        """

        def graphQL = TestUtil.graphQL(sdl).build()
        when:
        def er = graphQL.execute(ExecutionInput.newExecutionInput("query q { simplePojo {names age interesting foo }}")
                .root([simplePojo: new SimplePojo(["brad"], 42, true)]))

        then:
        er.errors.isEmpty()
        er.data == [simplePojo: [names: ["brad"], age: 42, interesting: true, foo: null]]

    }
}
