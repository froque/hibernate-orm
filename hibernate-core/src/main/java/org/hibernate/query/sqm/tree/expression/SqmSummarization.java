/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.expression;

import java.util.List;

import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.tree.select.SqmSubQuery;

/**
 * @author Christian Beikov
 */
public class SqmSummarization<T> extends AbstractSqmExpression<T> {

	private final Kind kind;
	private final List<SqmExpression<?>> groupings;

	public SqmSummarization(Kind kind, List<SqmExpression<?>> groupings, NodeBuilder criteriaBuilder) {
		super( null, criteriaBuilder );
		this.kind = kind;
		this.groupings = groupings;
	}

	public Kind getKind() {
		return kind;
	}

	public List<SqmExpression<?>> getGroupings() {
		return groupings;
	}

	@Override
	public <X> X accept(SemanticQueryWalker<X> walker) {
		return walker.visitSummarization( this );
	}

	public enum Kind {
		ROLLUP,
		CUBE
	}
	@Override
	public void appendHqlString(StringBuilder sb) {
		sb.append( kind );
		sb.append( " (" );
		groupings.get( 0 ).appendHqlString( sb );
		for ( int i = 1; i < groupings.size(); i++ ) {
			sb.append(", ");
			groupings.get( i ).appendHqlString( sb );
		}
		sb.append( ')' );
	}

}
