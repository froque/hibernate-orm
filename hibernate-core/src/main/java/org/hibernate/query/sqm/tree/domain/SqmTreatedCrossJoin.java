/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.domain;

import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.query.TreatedNavigablePath;
import org.hibernate.query.sqm.SqmPathSource;
import org.hibernate.query.sqm.tree.from.SqmCrossJoin;
import org.hibernate.query.sqm.tree.from.SqmJoin;

/**
 * @author Steve Ebersole
 */
public class SqmTreatedCrossJoin<T, S extends T> extends SqmCrossJoin<S> implements SqmTreatedPath<T, S> {
	private final SqmCrossJoin<T> wrappedPath;
	private final EntityDomainType<S> treatTarget;

	public SqmTreatedCrossJoin(
			SqmCrossJoin<T> wrappedPath,
			EntityDomainType<S> treatTarget,
			String alias) {
		//noinspection unchecked
		super(
				wrappedPath.getNavigablePath().treatAs( treatTarget.getHibernateEntityName(), alias ),
				(EntityDomainType<S>) wrappedPath.getReferencedPathSource().getSqmPathType(),
				alias,
				wrappedPath.getRoot()
		);
		this.wrappedPath = wrappedPath;
		this.treatTarget = treatTarget;
	}

	@Override
	public void addSqmJoin(SqmJoin<S, ?> join) {
		super.addSqmJoin( join );
		//noinspection unchecked
		wrappedPath.addSqmJoin( (SqmJoin<T, ?>) join );
	}

	@Override
	public EntityDomainType<S> getTreatTarget() {
		return treatTarget;
	}

	@Override
	public EntityDomainType<S> getModel() {
		return getTreatTarget();
	}

	@Override
	public SqmPath<T> getWrappedPath() {
		return wrappedPath;
	}

	@Override
	public SqmPathSource<S> getNodeType() {
		return treatTarget;
	}

	@Override
	public EntityDomainType<S> getReferencedPathSource() {
		//noinspection unchecked
		return (EntityDomainType<S>) wrappedPath.getReferencedPathSource();
	}

	@Override
	public void appendHqlString(StringBuilder sb) {
		sb.append( "treat(" );
		wrappedPath.appendHqlString( sb );
		sb.append( " as " );
		sb.append( treatTarget.getName() );
		sb.append( ')' );
	}
}
