/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.ordering.ast;

import org.hibernate.metamodel.UnsupportedMappingException;
import org.hibernate.metamodel.mapping.MappingType;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.ordering.TranslationContext;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.SortOrder;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SortSpecification;

/**
 * Represents a column-reference used in an order-by fragment
 *
 * @apiNote This is Hibernate-specific feature.  For {@link jakarta.persistence.OrderBy} (JPA)
 * all path references are expected to be domain paths (attributes).
 *
 * @author Steve Ebersole
 */
public class ColumnReference implements OrderingExpression, SequencePart {
	private final String columnExpression;
	private final boolean isColumnExpressionFormula;

	public ColumnReference(String columnExpression, boolean isColumnExpressionFormula) {
		this.columnExpression = columnExpression;
		this.isColumnExpressionFormula = isColumnExpressionFormula;
	}

	public String getColumnExpression() {
		return columnExpression;
	}

	public boolean isColumnExpressionFormula() {
		return isColumnExpressionFormula;
	}

	@Override
	public Expression resolve(
			QuerySpec ast,
			TableGroup tableGroup,
			String modelPartName,
			SqlAstCreationState creationState) {
		TableReference tableReference;

		tableReference = getTableReference( tableGroup );

		final SqlExpressionResolver sqlExpressionResolver = creationState.getSqlExpressionResolver();
		return sqlExpressionResolver.resolveSqlExpression(
				SqlExpressionResolver.createColumnReferenceKey( tableReference, columnExpression ),
				sqlAstProcessingState -> new org.hibernate.sql.ast.tree.expression.ColumnReference(
						tableReference,
						columnExpression,
						isColumnExpressionFormula,
						// because these ordering fragments are only ever part of the order-by clause, there
						//		is no need for the JdbcMapping
						null,
						null,
						null,
						creationState.getCreationContext().getSessionFactory()
				)
		);
	}

	@Override
	public SequencePart resolvePathPart(
			String name,
			String identifier,
			boolean isTerminal,
			TranslationContext translationContext) {
		throw new UnsupportedMappingException( "ColumnReference cannot be de-referenced" );
	}

	@Override
	public void apply(
			QuerySpec ast,
			TableGroup tableGroup,
			String collation,
			String modelPartName,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence,
			SqlAstCreationState creationState) {
		final Expression expression = resolve( ast, tableGroup, modelPartName, creationState );
		// It makes no sense to order by an expression multiple times
		// SQL Server even reports a query error in this case
		if ( ast.hasSortSpecifications() ) {
			for ( SortSpecification sortSpecification : ast.getSortSpecifications() ) {
				if ( sortSpecification.getSortExpression() == expression ) {
					return;
				}
			}
		}

		ast.addSortSpecification( new SortSpecification( expression, collation, sortOrder, nullPrecedence ) );
	}

	TableReference getTableReference(TableGroup tableGroup) {
		ModelPartContainer modelPart = tableGroup.getModelPart();
		if ( modelPart instanceof PluralAttributeMapping ) {
			final PluralAttributeMapping pluralAttribute = (PluralAttributeMapping) modelPart;
			if ( !pluralAttribute.getCollectionDescriptor().hasManyToManyOrdering() ) {
				return tableGroup.getPrimaryTableReference();
			}

			final MappingType elementMappingType = pluralAttribute.getElementDescriptor().getPartMappingType();

			if ( elementMappingType instanceof AbstractEntityPersister ) {
				final AbstractEntityPersister abstractEntityPersister = (AbstractEntityPersister) elementMappingType;
				final int tableNumber = abstractEntityPersister.determineTableNumberForColumn( columnExpression );
				final String tableName = abstractEntityPersister.getTableName( tableNumber );

				return tableGroup.getTableReference( tableGroup.getNavigablePath(), tableName );
			}
			else {
				return tableGroup.getPrimaryTableReference();
			}
		}
		return null;
	}

}
