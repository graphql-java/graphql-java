/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package graphql.util

import spock.lang.Specification

class TestVertex extends Vertex<TestVertex> {
    public int hashCode () {
        return Objects.hashCode(value)
    }

    public boolean equals (Object o) {
        if (o.is(this))
            return true
        else if (o == null)
            return false
        else if (o instanceof TestVertex) {
            TestVertex other = (TestVertex)o
            return this.value == other.value
        }

        return false
    }

    String value
}

class DependencyGraphTest extends Specification {    
    def "test empty graph ordering"() {
        given:
        def graph = [] as DependencyGraph
            
        when:
        def ordering = graph.orderDependencies([] as DependencyGraphContext)
            
        then:
            graph.size() == 0
            graph.order() == 0
            ordering.hasNext() == false
    }
    
    def "test 1 vertex ordering"() {
        given:
        def v1 = new TestVertex(value: "v1")
        def graph = [] as DependencyGraph
        graph
            .addDependency(v1, v1)
            
        when:
        def ordering = graph.orderDependencies([] as DependencyGraphContext)
        
        then:
        graph.size() == 0
        graph.order() == 1
        ordering.hasNext() == true
        ordering.next() == [v1] as Set
        ordering.hasNext() == false
    }
    
    def "test 2 independent vertices ordering"() {
        given:
        def v1 = new TestVertex(value: "v1")
        def v2 = new TestVertex(value: "v2")
        def graph = [] as DependencyGraph
        graph
            .addDependency(v1, v1)
            .addDependency(v2, v2)
            
        when:
        def ordering = graph.orderDependencies([] as DependencyGraphContext)
        
        then:
        graph.size() == 0
        graph.order() == 2
        ordering.hasNext() == true
        ordering.next() == [v1, v2] as Set
        ordering.hasNext() == false
    }
    
    def "test 2 dependent vertices ordering"() {
        given:
        def v1 = new TestVertex(value: "v1")
        def v2 = new TestVertex(value: "v2")
        def graph = [] as DependencyGraph
        graph
            .addDependency(v1, v2)
            
        when:
        def ordering = graph.orderDependencies([] as DependencyGraphContext)
        
        then:
        graph.size() == 1
        graph.order() == 2
        ordering.hasNext() == true
        ordering.next() == [v2] as Set
        ordering.hasNext() == true
        ordering.next() == [v1] as Set
        ordering.hasNext() == false
    }
    
    def "test 2 nodes undepend"() {
        given:
        def v1 = new TestVertex(value: "v1")
        def v2 = new TestVertex(value: "v2")
        def graph = [] as DependencyGraph
        graph
            .addDependency(v1, v2)
            
        when:
        v1.undependsOn(v2)
        def ordering = graph.orderDependencies([] as DependencyGraphContext)

        then:
        graph.size() == 0
        graph.order() == 2
        v1.dependencySet().isEmpty() == true
        v2.adjacencySet().isEmpty() == true
        ordering.hasNext() == true
        ordering.next() == [v1, v2] as Set
        ordering.hasNext() == false
    }
    
    def "test possible https://en.wikipedia.org/wiki/Dependency_graph example"() {
        given:
            def a = new TestVertex(value: "a")
            def b = new TestVertex(value: "b")
            def c = new TestVertex(value: "c")
            def d = new TestVertex(value: "d")
            def graph = [] as DependencyGraph
            graph
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            
        then:
            graph.order() == 4
            graph.size() == 3
            ordering.hasNext() == true
            ordering.next() == [c, d] as Set
            ordering.hasNext() == true
            ordering.next() == [b] as Set
            ordering.hasNext() == true
            ordering.next() == [a] as Set
            ordering.hasNext() == false
    } 
    
    def "test disconnect https://en.wikipedia.org/wiki/Dependency_graph example"() {
        given:
            def a = new TestVertex(value: "a")
            def b = new TestVertex(value: "b")
            def c = new TestVertex(value: "c")
            def d = new TestVertex(value: "d")
            def graph = [] as DependencyGraph
            graph
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            a.disconnect()
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            
        then:
            graph.order() == 4
            graph.size() == 1
            ordering.hasNext() == true
            ordering.next() == [c, d, a] as Set
            ordering.hasNext() == true
            ordering.next() == [b] as Set
            ordering.hasNext() == false
    } 
    
    def "test impossible https://en.wikipedia.org/wiki/Dependency_graph example"() {
        given:
            def a = new TestVertex(value: "a")
            def b = new TestVertex(value: "b")
            def c = new TestVertex(value: "c")
            def d = new TestVertex(value: "d")
            def graph = [] as DependencyGraph
            graph
                .addDependency(a, b)
                .addDependency(b, d)
                .addDependency(b, c)
                .addDependency(c, d)
                .addDependency(c, a)
                
        when:
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            ordering.hasNext()
            ordering.next() // [d]
            ordering.hasNext()
            
        then:
            graphql.AssertException e = thrown()
            e.message.contains("couldn't calculate next closure")
    }
    
    def "test illegal next"() {
        given:
            def a = new TestVertex(value: "a")
            def b = new TestVertex(value: "b")
            def c = new TestVertex(value: "c")
            def d = new TestVertex(value: "d")
            def graph = [] as DependencyGraph
            graph
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            ordering.hasNext()
            ordering.next()
            ordering.next()
            
        then:
            java.util.NoSuchElementException e = thrown()
            e.message.contains("next closure hasn't been calculated yet")
    }    
    
    def "test hasNext idempotency"() {
        given:
            def a = new TestVertex(value: "a")
            def b = new TestVertex(value: "b")
            def c = new TestVertex(value: "c")
            def d = new TestVertex(value: "d")
            def graph = [] as DependencyGraph
            graph
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            
        then:
            ordering.hasNext() == ordering.hasNext()
            ordering.next() == [c, d] as Set
            ordering.hasNext() == ordering.hasNext()
            ordering.next() == [b] as Set
            ordering.hasNext() == ordering.hasNext()
            ordering.next() == [a] as Set
            ordering.hasNext() == ordering.hasNext()
    }
    
    def "test close by value"() {
        given:
            def a = new TestVertex(value: "a")
            def b = new TestVertex(value: "b")
            def c = new TestVertex(value: "c")
            def d = new TestVertex(value: "d")
            def graph = [] as DependencyGraph
            graph
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            
        then:
            ordering.hasNext() == true
            ordering.next() == [c, d] as Set
            ordering.close([new TestVertex(value: "c"), new TestVertex(value: "d")])
            ordering.hasNext() == true
            ordering.next() == [b] as Set
            ordering.close([new TestVertex(value: "b")])
            ordering.hasNext() == true
            ordering.next() == [new TestVertex(value: "a")] as Set
            ordering.close([a])
            ordering.hasNext() == false
    }
        
    def "test close by id"() {
        given:
            def a = new TestVertex(value: "a")
            def b = new TestVertex(value: "b")
            def c = new TestVertex(value: "c")
            def d = new TestVertex(value: "d")
            def graph = [] as DependencyGraph
            graph
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            
        then:
            ordering.hasNext() == true
            ordering.next() == [c, d] as Set
            ordering.close([new TestVertex(value: "c").id(c.getId()), new TestVertex(value: "d").id(d.getId())])
            ordering.hasNext() == true
            ordering.next() == [b] as Set
            ordering.close([new TestVertex(value: "b").id(b.getId())])
            ordering.hasNext() == true
            ordering.next() == [new TestVertex(value: "a").id(a.getId())] as Set
            ordering.close([a])
            ordering.hasNext() == false
    }
    
    def "test close by invalid id"() {
        given:
            def a = new TestVertex(value: "a")
            def b = new TestVertex(value: "b")
            def c = new TestVertex(value: "c")
            def d = new TestVertex(value: "d")
            def graph = [] as DependencyGraph
            graph
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            ordering.hasNext()
            ordering.next()
            ordering.close([new TestVertex(value: "c").id(c.getId()), new TestVertex(value: "d").id(12345)])
            
        then:
            java.lang.IllegalArgumentException e = thrown()
            e.message.contains("node not found")
    }
    
    def "test close by invalid value"() {
        given:
            def a = new TestVertex(value: "a")
            def b = new TestVertex(value: "b")
            def c = new TestVertex(value: "c")
            def d = new TestVertex(value: "d")
            def graph = [] as DependencyGraph
            graph
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            ordering.hasNext()
            ordering.next()
            ordering.close([new TestVertex(value: "c").id(c.getId()), new TestVertex(value: "e")])
            
        then:
            java.lang.IllegalArgumentException e = thrown()
            e.message.contains("node not found")
    }
    
    def "test possible https://en.wikipedia.org/wiki/Dependency_graph example via addEdge"() {
        given:
            def graph = [] as DependencyGraph
            def a = graph.addNode(new TestVertex(value: "a"))
            def b = graph.addNode(new TestVertex(value: "b"))
            def c = graph.addNode(new TestVertex(value: "c"))
            def d = graph.addNode(new TestVertex(value: "d"))
                
        when:
            graph
               .addEdge(new Edge<>(b, a))
               .addEdge(new Edge<>(c, a))
               .addEdge(new Edge<>(d, b))
            def ordering = graph.orderDependencies([] as DependencyGraphContext)
            
        then:
            graph.order() == 4
            graph.size() == 3
            ordering.hasNext() == true
            ordering.next() == [c, d] as Set
            ordering.hasNext() == true
            ordering.next() == [b] as Set
            ordering.hasNext() == true
            ordering.next() == [a] as Set
            ordering.hasNext() == false
    } 
}

