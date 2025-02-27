/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.spi;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.sql.exec.spi.Callback;

/**
 * Context for execution of {@link org.hibernate.query.Query}"
 */
public interface DomainQueryExecutionContext {
	QueryOptions getQueryOptions();

	QueryParameterBindings getQueryParameterBindings();

	/**
	 * The callback reference
	 * @return
	 */
	Callback getCallback();

	SharedSessionContractImplementor getSession();
}
