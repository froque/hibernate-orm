/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.ComparisonOperator;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.Literal;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.SqlTupleContainer;
import org.hibernate.sql.ast.tree.expression.Summarization;
import org.hibernate.sql.ast.tree.predicate.BooleanExpressionPredicate;
import org.hibernate.sql.ast.tree.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.exec.spi.JdbcOperation;

/**
 * A SQL AST translator for H2.
 *
 * @author Christian Beikov
 */
public class H2SqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	private boolean renderAsArray;

	public H2SqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement) {
		super( sessionFactory, statement );
	}

	@Override
	protected void renderExpressionAsClauseItem(Expression expression) {
		expression.accept( this );
	}

	@Override
	public void visitBooleanExpressionPredicate(BooleanExpressionPredicate booleanExpressionPredicate) {
		booleanExpressionPredicate.getExpression().accept( this );
	}

	@Override
	public void visitOffsetFetchClause(QueryPart queryPart) {
		if ( isRowsOnlyFetchClauseType( queryPart ) ) {
			if ( supportsOffsetFetchClause() ) {
				renderOffsetFetchClause( queryPart, true );
			}
			else {
				renderLimitOffsetClause( queryPart );
			}
		}
		else {
			if ( supportsOffsetFetchClausePercentWithTies() ) {
				renderOffsetFetchClause( queryPart, true );
			}
			else {
				// FETCH PERCENT and WITH TIES were introduced along with window functions
				throw new IllegalArgumentException( "Can't emulate fetch clause type: " + queryPart.getFetchClauseType() );
			}
		}
	}

	@Override
	protected void renderSearchClause(CteStatement cte) {
		// H2 does not support this, but it's just a hint anyway
	}

	@Override
	protected void renderCycleClause(CteStatement cte) {
		// H2 does not support this, but it can be emulated
	}

	@Override
	protected void renderSelectTupleComparison(
			List<SqlSelection> lhsExpressions,
			SqlTuple tuple,
			ComparisonOperator operator) {
		emulateSelectTupleComparison( lhsExpressions, tuple.getExpressions(), operator, true );
	}

	@Override
	public void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate) {
		final SqlTuple lhsTuple;
		if ( ( lhsTuple = SqlTupleContainer.getSqlTuple( inSubQueryPredicate.getTestExpression() ) ) != null
				&& lhsTuple.getExpressions().size() != 1 ) {
			inSubQueryPredicate.getTestExpression().accept( this );
			if ( inSubQueryPredicate.isNegated() ) {
				appendSql( " not" );
			}
			appendSql( " in" );
			final boolean renderAsArray = this.renderAsArray;
			this.renderAsArray = true;
			inSubQueryPredicate.getSubQuery().accept( this );
			this.renderAsArray = renderAsArray;
		}
		else {
			super.visitInSubQueryPredicate( inSubQueryPredicate );
		}
	}

	@Override
	protected void visitSqlSelections(SelectClause selectClause) {
		final boolean renderAsArray = this.renderAsArray;
		this.renderAsArray = false;
		if ( renderAsArray ) {
			append( OPEN_PARENTHESIS );
		}
		super.visitSqlSelections( selectClause );
		if ( renderAsArray ) {
			append( CLOSE_PARENTHESIS );
		}
	}

	@Override
	protected void renderPartitionItem(Expression expression) {
		if ( expression instanceof Literal ) {
			appendSql( "'0' || '0'" );
		}
		else if ( expression instanceof Summarization ) {
			// This could theoretically be emulated by rendering all grouping variations of the query and
			// connect them via union all but that's probably pretty inefficient and would have to happen
			// on the query spec level
			throw new UnsupportedOperationException( "Summarization is not supported by DBMS!" );
		}
		else {
			expression.accept( this );
		}
	}

	@Override
	protected boolean supportsRowValueConstructorSyntax() {
		// Just a guess
		return getDialect().getVersion().isSameOrAfter( 1, 4, 197 );
	}

	@Override
	protected boolean supportsRowValueConstructorSyntaxInInList() {
		// Just a guess
		return getDialect().getVersion().isSameOrAfter( 1, 4, 197 );
	}

	@Override
	protected boolean supportsRowValueConstructorSyntaxInQuantifiedPredicates() {
		// Just a guess
		return getDialect().getVersion().isSameOrAfter( 1, 4, 197 );
	}

	@Override
	protected String getFromDual() {
		return " from dual";
	}

	private boolean supportsOffsetFetchClause() {
		return getDialect().getVersion().isSameOrAfter( 1, 4, 195 );
	}

	private boolean supportsOffsetFetchClausePercentWithTies() {
		// Introduction of TIES clause https://github.com/h2database/h2database/commit/876e9fbe7baf11d01675bfe871aac2cf1b6104ce
		// Introduction of PERCENT support https://github.com/h2database/h2database/commit/f45913302e5f6ad149155a73763c0c59d8205849
		return getDialect().getVersion().isSameOrAfter( 1, 4, 198 );
	}
}
