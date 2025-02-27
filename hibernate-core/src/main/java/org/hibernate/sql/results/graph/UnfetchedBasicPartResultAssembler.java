/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.sql.results.graph;

import org.hibernate.sql.results.jdbc.spi.JdbcValuesSourceProcessingOptions;
import org.hibernate.sql.results.jdbc.spi.RowProcessingState;
import org.hibernate.type.descriptor.java.JavaType;

public class UnfetchedBasicPartResultAssembler<J>  implements DomainResultAssembler<J> {

	private final JavaType<J> javaTypeDescriptor;

	public UnfetchedBasicPartResultAssembler(JavaType<J> javaTypeDescriptor) {
		this.javaTypeDescriptor = javaTypeDescriptor;
	}

	@Override
	public J assemble(RowProcessingState rowProcessingState, JdbcValuesSourceProcessingOptions options) {
		return null;
	}

	@Override
	public JavaType<J> getAssembledJavaTypeDescriptor() {
		return javaTypeDescriptor;
	}
}
