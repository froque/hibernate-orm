/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.internal;

import java.util.function.Function;
import jakarta.persistence.metamodel.Bindable;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.metamodel.model.domain.AnyMappingDomainType;
import org.hibernate.metamodel.model.domain.BasicDomainType;
import org.hibernate.metamodel.model.domain.DomainType;
import org.hibernate.metamodel.model.domain.EmbeddableDomainType;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.metamodel.model.domain.internal.AnyMappingSqmPathSource;
import org.hibernate.metamodel.model.domain.internal.BasicSqmPathSource;
import org.hibernate.metamodel.model.domain.internal.DomainModelHelper;
import org.hibernate.metamodel.model.domain.internal.EmbeddedSqmPathSource;
import org.hibernate.metamodel.model.domain.internal.EntitySqmPathSource;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.sqm.SqmExpressable;
import org.hibernate.query.sqm.SqmPathSource;
import org.hibernate.query.sqm.sql.SqmToSqlAstConverter;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.query.sqm.tree.domain.AbstractSqmSpecificPluralPartPath;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.domain.SqmTreatedPath;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.type.BasicType;

/**
 * Helper for dealing with Hibernate's "mapping model" while processing an SQM which is defined
 * in terms of the JPA/SQM metamodel
 *
 * @author Steve Ebersole
 */
public class SqmMappingModelHelper {
	private SqmMappingModelHelper() {
	}

	/**
	 * Resolve a JPA EntityType descriptor to it's corresponding EntityPersister
	 * in the Hibernate mapping type system
	 */
	public static EntityPersister resolveEntityPersister(
			EntityDomainType<?> entityType,
			SessionFactoryImplementor sessionFactory) {
		// Our EntityTypeImpl#getType impl returns the Hibernate entity-name
		// which is exactly what we want
		final String hibernateEntityName = entityType.getHibernateEntityName();
		return sessionFactory.getMetamodel().entityPersister( hibernateEntityName );
	}

	public static <J> SqmPathSource<J> resolveSqmKeyPathSource(
			DomainType<J> valueDomainType,
			Bindable.BindableType jpaBindableType) {
		return resolveSqmPathSource(
				CollectionPart.Nature.INDEX.getName(),
				valueDomainType,
				jpaBindableType
		);
	}

	public static <J> SqmPathSource<J> resolveSqmPathSource(
			String name,
			DomainType<J> valueDomainType,
			Bindable.BindableType jpaBindableType) {

		if ( valueDomainType instanceof BasicDomainType ) {
			return new BasicSqmPathSource<>(
					name,
					(BasicDomainType<J>) valueDomainType,
					jpaBindableType
			);
		}

		if ( valueDomainType instanceof AnyMappingDomainType ) {
			return new AnyMappingSqmPathSource<>(
					name,
					(AnyMappingDomainType<J>) valueDomainType,
					jpaBindableType
			);
		}

		if ( valueDomainType instanceof EmbeddableDomainType ) {
			return new EmbeddedSqmPathSource<>(
					name,
					(EmbeddableDomainType<J>) valueDomainType,
					jpaBindableType
			);
		}

		if ( valueDomainType instanceof EntityDomainType ) {
			return new EntitySqmPathSource<>(
					name,
					(EntityDomainType<J>) valueDomainType,
					jpaBindableType
			);
		}

		throw new IllegalArgumentException(
				"Unrecognized value type Java-type [" + valueDomainType.getTypeName() + "] for plural attribute value"
		);
	}

	public static MappingModelExpressable resolveMappingModelExpressable(
			SqmTypedNode<?> sqmNode,
			MappingMetamodel domainModel,
			Function<NavigablePath,TableGroup> tableGroupLocator) {
		if ( sqmNode instanceof SqmPath ) {
			return resolveSqmPath( (SqmPath) sqmNode, domainModel, tableGroupLocator );
		}

		final SqmExpressable<?> nodeType = sqmNode.getNodeType();
		if ( nodeType instanceof BasicType ) {
			return ( (BasicType) nodeType );
		}

		return null;
	}

	private static ModelPart resolveSqmPath(
			SqmPath<?> sqmPath,
			MappingMetamodel domainModel,
			Function<NavigablePath,TableGroup> tableGroupLocator) {

		if ( sqmPath instanceof SqmTreatedPath ) {
			final SqmTreatedPath treatedPath = (SqmTreatedPath) sqmPath;
			final EntityDomainType treatTargetType = treatedPath.getTreatTarget();
			return domainModel.findEntityDescriptor( treatTargetType.getHibernateEntityName() );
		}

		// see if the LHS is treated
		if ( sqmPath.getLhs() instanceof SqmTreatedPath ) {
			final SqmTreatedPath treatedPath = (SqmTreatedPath) sqmPath.getLhs();
			final EntityDomainType treatTargetType = treatedPath.getTreatTarget();
			final EntityPersister container = domainModel.findEntityDescriptor( treatTargetType.getHibernateEntityName() );

			return container.findSubPart( sqmPath.getNavigablePath().getLocalName(), container );
		}

		// Plural path parts are not joined and thus also have no table group
		if ( sqmPath instanceof AbstractSqmSpecificPluralPartPath<?> ) {
			final TableGroup lhsTableGroup = tableGroupLocator.apply( sqmPath.getLhs().getLhs().getNavigablePath() );
			final ModelPartContainer pluralPart = (ModelPartContainer) lhsTableGroup.getModelPart().findSubPart(
					sqmPath.getLhs().getReferencedPathSource().getPathName(),
					null
			);
			return pluralPart.findSubPart( sqmPath.getReferencedPathSource().getPathName(), null );
		}

		if ( sqmPath.getLhs() == null ) {
			final EntityDomainType<?> entityDomainType = (EntityDomainType<?>) sqmPath.getReferencedPathSource();
			return domainModel.findEntityDescriptor( entityDomainType.getHibernateEntityName() );
		}
		final TableGroup lhsTableGroup = tableGroupLocator.apply( sqmPath.getLhs().getNavigablePath() );
		return lhsTableGroup.getModelPart().findSubPart( sqmPath.getReferencedPathSource().getPathName(), null );
	}

	public static EntityMappingType resolveExplicitTreatTarget(
			SqmPath<?> sqmPath,
			SqmToSqlAstConverter converter) {
		if ( sqmPath instanceof SqmTreatedPath ) {
			final SqmTreatedPath treatedPath = (SqmTreatedPath) sqmPath;
			return resolveEntityPersister( treatedPath.getTreatTarget(), converter.getCreationContext().getSessionFactory() );
		}

		return null;
	}
}
