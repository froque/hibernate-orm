/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.tuple.GeneratedValueGeneration;

/**
 * Specifies that the value of the annotated property is generated by the database.
 * The generated value will be automatically retrieved using a SQL {@code SELECT}
 * after it is generated.
 * <p>
 * {@code Generated} relieves the program of the need to call
 * {@link org.hibernate.Session#refresh(Object)} explicitly to synchronize state
 * held in memory with state generated by the database when a SQL {@code INSERT}
 * or {@code UPDATE} is executed.
 * <p>
 * This is most useful for working with database tables where a column value is
 * populated by a database trigger. A second possible scenario is the use of
 * {@code Generated(INSERT)} with {@link ColumnDefault}.
 * <ul>
 *     <li>For identity/autoincrement columns mapped to an identifier property,
 *     use {@link jakarta.persistence.GeneratedValue}.
 *     <li>For columns with a {@code generated always as} clause, prefer the
 *     {@link GeneratedColumn} annotation.
 * </ul>
 *
 * @author Emmanuel Bernard
 *
 * @see jakarta.persistence.GeneratedValue
 * @see ColumnDefault
 * @see GeneratedColumn
 */
@ValueGenerationType( generatedBy = GeneratedValueGeneration.class )
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Generated {
	/**
	 * Specifies the events that cause the value to be generated by the database.
	 * <ul>
	 *     <li>If {@link GenerationTime#INSERT}, the generated value will be selected
	 *     after each SQL {@code INSERT} statement is executed.
	 *     <li>If {@link GenerationTime#ALWAYS}, the generated value will be selected
	 *     after each SQL {@code INSERT} or {@code UPDATE} statement is executed.
	 * </ul>
	 */
	GenerationTime value();
}
