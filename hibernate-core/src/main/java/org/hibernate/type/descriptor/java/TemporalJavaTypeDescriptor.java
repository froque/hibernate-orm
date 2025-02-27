/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.type.descriptor.java;

import java.sql.Types;

import org.hibernate.Incubating;
import org.hibernate.type.spi.TypeConfiguration;

import jakarta.persistence.TemporalType;

/**
 * Specialized JavaTypeDescriptor for temporal types.
 *
 * @author Steve Ebersole
 */
@Incubating
public interface TemporalJavaTypeDescriptor<T> extends BasicJavaType<T> {

	static int resolveJdbcTypeCode(TemporalType requestedTemporalPrecision) {
		switch ( requestedTemporalPrecision ) {
			case DATE:
				return Types.DATE;
			case TIME:
				return Types.TIME;
			case TIMESTAMP:
				return Types.TIMESTAMP;
		}
		throw new UnsupportedOperationException( "Unsupported precision: " + requestedTemporalPrecision );
	}

	/**
	 * The precision represented by this type
	 */
	TemporalType getPrecision();

	/**
	 * Resolve the appropriate TemporalJavaTypeDescriptor for the given precision
	 * "relative" to this type.
	 */
	<X> TemporalJavaTypeDescriptor<X> resolveTypeForPrecision(
			TemporalType precision,
			TypeConfiguration typeConfiguration);
}
