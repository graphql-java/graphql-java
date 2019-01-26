/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.execution2.ValueFetcher;
import static graphql.execution3.NodeVertexVisitor.whenFieldVertex;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author gkesler
 */
public class ResultCollector {    
    public DocumentVertex prepareResult (DocumentVertex document) {
        Objects.requireNonNull(document);
        
        return document.result(result);
    }
    
    public OperationVertex prepareResult (OperationVertex operation) {
        Objects.requireNonNull(operation);
        
        return operation.result(result);
    }
    
    public Object getResult () {
        return result.get(0);
    }
    
    public void joinResultsOf (FieldVertex node) {
        Objects.requireNonNull(node);
        
        String on = node.getResponseKey();
        List<Object> result = (List<Object>)node.getResult();
        List<Object> source = (List<Object>)node.getSource();
        
        // will join relation dataset to target dataset
        // since we don't have any keys to build a cartesian product,
        // will be assuming that relation and target dataset s are alerady sorted by the
        // same key, so a pair {target[i], relation[i]} represents that cartesian product
        // we can use to join them together
        int index[] = {0};
        result
            .stream()
            .limit(Math.min(source.size(), result.size()))
            .forEach(value -> {
                Map<String, Object> targetMap = (Map<String, Object>)source.get(index[0]++);
                targetMap.put(on, value);

                // Here, verify if the value to join is valid
                if (value == null && node.isNotNull()) {
                    // need to set the paren't corresponding value to null
                    bubbleUpNIL(node);
                }                    
            });
    }
    
    private void bubbleUpNIL (FieldVertex node) {
        String responseKey = node.getResponseKey();
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

                        if (value.getOrDefault(responseKey, this) == null) {
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
    
    public Object checkAndFixNILs (Object fetchedValue, FieldVertex fieldNode) {
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

    public static List<Object> flatten (List<Object> result) {
        return flatten(result, o -> o != null);
    }
    
    public static List<Object> flatten (List<Object> result, Predicate<? super Object> filter) {
        Objects.requireNonNull(filter);
        
        return Optional
            .ofNullable(result)
            .map(res -> res
                .stream()
                .flatMap(ResultCollector::asStream)
                .filter(filter)
                .collect(Collectors.toList())
            )
            .orElseGet(Collections::emptyList);
    }

    private static Stream<Object> asStream (Object o) {
        return (o instanceof Collection)
            ? ((Collection<Object>)o)
                .stream()
                .flatMap(ResultCollector::asStream)
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
    
    private final List<Object> result = Arrays.asList(new HashMap<String, Object>());
}
