/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

/**
 * Represents an operation upon an operand of Boolean type and two other operands of the same type, 
 * producing a result of the same type as the operands. 
 * This is a specialization of {@link graphql.util.TriFunction} for the case where 
 * the operands and the result are all of the same type.
 * Follows semantics of Java ternary operation
 * 
 * {@code condition ? true-branch : false-branch}
 * 
 * 
 * This is a functional interface whose functional method is BiFunction.apply(Object, Object).
 *
 * @param <T> the type of the operands and result of the operator
 */
@FunctionalInterface
public interface TernaryOperator<T> extends TriFunction<Boolean, T, T, T> {    
}
