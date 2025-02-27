/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

@IdGeneratorType( org.hibernate.id.uuid.UuidGenerator.class )
@Retention(RetentionPolicy.RUNTIME)
@Target({ FIELD, METHOD })
public @interface UuidGenerator {

	enum Style {
		/**
		 * Defaults to {@link #RANDOM}
		 */
		AUTO,
		/**
		 * Uses {@link UUID#randomUUID()} to generate values
		 */
		RANDOM,
		/**
		 * Applies a time-based generation strategy consistent with IETF RFC 4122.  Uses
		 * IP address rather than mac address.
		 *
		 * NOTE : Can be a bottleneck due to the need to synchronize in order to increment an
		 * internal count as part of the algorithm.
		 */
		TIME
	}

	/**
	 * Which style of generation should be used
	 */
	Style style() default Style.AUTO;
}
