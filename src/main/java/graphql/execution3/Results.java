/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import static graphql.Assert.assertNotNull;
import graphql.execution2.ValueFetcher;
import static graphql.execution3.NodeVertexVisitor.whenFieldVertex;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper to manipulate with fetched data and collect/join results
 */
public class Results {  
    private Results () {
        // disable instantiation
    }
    
    /**
     * Joins results of the provided vertex to the sources (parent results)
     * 
     * @param node a resolved Field vertex 
     */
    public static void joinResultsOf (FieldVertex node) {
        assertNotNull(node);        
        
        String on = node.getResponseKey();
        List<Object> sources = (List<Object>)node.getSource();
        List<Object> results = Optional
            .ofNullable((List<Object>)node.getResult())
            .orElseGet(Collections::emptyList);
        
        // will join relation dataset to target dataset
        // since we don't have any keys to build a cartesian product,
        // will be assuming that relation and target dataset s are alerady sorted by the
        // same key, so a pair {target[i], relation[i]} represents that cartesian product
        // we can use to join them together
        int index[] = {0};
        results
            .stream()
            .limit(Math.min(sources.size(), results.size()))
            .forEach(value -> {
                Map<String, Object> targetMap = (Map<String, Object>)sources.get(index[0]++);
                targetMap.put(on, value);

                // Here, verify if the value to join is valid
                if (value == null && node.isNotNull()) {
                    // need to set the paren't corresponding value to null
                    bubbleUpNIL(node, on);
                }                    
            });
    }
    
    private static void bubbleUpNIL (FieldVertex node, String responseKey) {
        node
          .dependencySet()
          .forEach(v -> {
                boolean hasNull = false;                
                ListIterator results = asList(v.getResult()).listIterator();
                while (results.hasNext()) {
                    Object o = results.next();                    
                    if (o == null) {
                        hasNull = true;
                        continue;
                    }
                    
                    boolean hasNullItems = false;                    
                    ListIterator items = asList(o).listIterator();
                    while (items.hasNext()) {
                        Map<String, Object> value = (Map<String, Object>)items.next();

                        if (value.getOrDefault(responseKey, ValueFetcher.NULL_VALUE) == null) {
                            items.set(null);
                            hasNullItems = true;
                        }
                    }
                    
                    if (hasNullItems && whenFieldVertex(v, node.isNotNull(), FieldVertex::isNotNullItems)) {
                        results.set(null);
                        hasNull = true;
                    }
                }

                if (hasNull) {
                    whenFieldVertex(v, null, n -> {
                        joinResultsOf(n);
                        return null;
                    });
                }
          });
    };
    
    /**
     * Takes care of ValueFetcher.NULL_VALUE.
     * Replaces them to nulls
     * 
     * @param fetchedValue fetched result
     * @param fieldNode destination Field vertex
     * @return corrected value
     */
    public static Object checkAndFixNILs (Object fetchedValue, FieldVertex fieldNode) {
        return (isNIL(fetchedValue) || isOneToOne(fieldNode))
            ? fixNIL(fetchedValue, () -> null)
            : fixNILs((List<Object>)fetchedValue, fieldNode);
    }
        
    private static boolean isNIL (Object value) {
        return value == null || value == ValueFetcher.NULL_VALUE;
    }
    
    private static boolean isOneToOne (FieldVertex fieldNode) {
        return fieldNode.getCardinality() == FieldVertex.Cardinality.OneToOne;
    }
    
    private static Object fixNIL (Object fetchedValue, Supplier<? super Object> mapper) {
        return (isNIL(fetchedValue))
            ? mapper.get()
            : fetchedValue;
    }

    private static Object fixNILs (List<Object> fetchedValues, FieldVertex fieldNode) {
        boolean hasNulls[] = {false};
        fetchedValues = flatten(fetchedValues, o -> true)
            .stream()
            .map(o -> fixNIL(o, 
                () -> { 
                    hasNulls[0] = true; 
                    return null; 
                }
            ))
            .collect(Collectors.toList());
        
        return (hasNulls[0] && fieldNode.isNotNullItems())
            ? null
            : fetchedValues;
    }

    /**
     * "Flattens" possibly multi-dimensional list into a single-dimensional one
     * filtering out {@code null} values.
     * 
     * @param result multi-dimensional list
     * @return single-dimensional list
     */
    public static List<Object> flatten (List<Object> result) {
        return flatten(result, o -> o != null);
    }
    
    /**
     * "Flattens" possibly multi-dimensional list into a single-dimensional one
     * filtering out values that don't match provided predicate.
     * 
     * @param filter predicate to filter out result elements
     * @param result multi-dimensional list
     * @return single-dimensional list
     */
    public static List<Object> flatten (List<Object> result, Predicate<? super Object> filter) {
        assertNotNull(filter);
        
        return Optional
            .ofNullable(result)
            .map(res -> res
                .stream()
                .flatMap(Results::asStream)
                .filter(filter)
                .collect(Collectors.toList())
            )
            .orElseGet(Collections::emptyList);
    }

    private static Stream<Object> asStream (Object o) {
        return (o instanceof Collection)
            ? ((Collection<Object>)o)
                .stream()
                .flatMap(Results::asStream)
            : Stream.of(o);
    }

    private static List<Object> asList (Object o) {
        return (o instanceof List)
            ? (List<Object>)o
            : Arrays.asList(o);
    }
    
    private static Object asObject (Object o) {
        List<Object> singletonList;
        return (o instanceof List && (singletonList = (List<Object>)o).size() <= 1)
            ? singletonList.size() == 1 
                ? singletonList.get(0) 
                : null
            : o;
    }
}
