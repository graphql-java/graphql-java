package graphql.util.javac

import spock.lang.Specification

class DynamicJavacSupportTest extends Specification {

    String sourceCode = '''
        package com.dynamic;
        public class TestClass {
            public String hello() {
                return "world";
            }
        }
      '''

    def "can compile things without a parent class loader"() {

        def javacSupport = new DynamicJavacSupport(null)

        when:
        def compiledClass = javacSupport.compile("com.dynamic.TestClass", sourceCode)
        def instance = compiledClass.getDeclaredConstructor().newInstance()
        def runCodeMethod = compiledClass.getMethod("hello")
        def value = runCodeMethod.invoke(instance)

        then:
        value == "world"

        // with a null parent class loader, this class loader should not be able to see this code
        when:
        compiledClass.getClassLoader().loadClass(this.getClass().getCanonicalName())
        then:
        thrown(ClassNotFoundException)
    }


    def "can compile things with a parent class loader"() {

        def javacSupport = new DynamicJavacSupport(this.getClass().getClassLoader())

        when:
        def compiledClass = javacSupport.compile("com.dynamic.TestClass", sourceCode)
        def instance = compiledClass.getDeclaredConstructor().newInstance()
        def runCodeMethod = compiledClass.getMethod("hello")
        def value = runCodeMethod.invoke(instance)

        then:
        noExceptionThrown()
        value == "world"

        // with a parent class loader, this class loader should be able to see this code
        when:
        def backToUs = compiledClass.getClassLoader().loadClass(this.getClass().getCanonicalName())
        then:
        backToUs === this.getClass()
    }
}
