/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.expression;

import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.SqmExpressable;
import org.hibernate.query.sqm.tree.select.SqmSubQuery;

/**
 * @author Gavin King
 */
public class SqmEvery<T> extends AbstractSqmExpression<T> {

	private final SqmSubQuery<T> subquery;

	public SqmEvery(SqmSubQuery<T> subquery, NodeBuilder criteriaBuilder) {
		super( subquery.getNodeType(), criteriaBuilder );
		this.subquery = subquery;
	}

	public SqmSubQuery<T> getSubquery() {
		return subquery;
	}

	@Override
	public <X> X accept(SemanticQueryWalker<X> walker) {
		return walker.visitEvery( this );
	}
	@Override
	public void appendHqlString(StringBuilder sb) {
		sb.append( "all " );
		subquery.appendHqlString( sb );
	}

}
