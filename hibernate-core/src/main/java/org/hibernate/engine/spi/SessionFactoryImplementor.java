/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.engine.spi;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.EntityGraph;

import org.hibernate.CustomEntityDirtinessStrategy;
import org.hibernate.EntityNameResolver;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.Metamodel;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.cache.spi.CacheImplementor;
import org.hibernate.cfg.Settings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.jdbc.spi.SqlExceptionHelper;
import org.hibernate.engine.profile.FetchProfile;
import org.hibernate.event.spi.EventEngine;
import org.hibernate.exception.spi.SQLExceptionConverter;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.RuntimeMetamodels;
import org.hibernate.internal.FastSessionServices;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.spi.QueryParameterBindingTypeResolver;
import org.hibernate.query.sqm.spi.SqmCreationContext;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.sql.ast.spi.SqlAstCreationContext;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Defines the internal contract between the <tt>SessionFactory</tt> and other parts of
 * Hibernate such as implementors of <tt>Type</tt>.
 *
 * @see SessionFactory
 * @see org.hibernate.internal.SessionFactoryImpl
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public interface SessionFactoryImplementor
		extends Mapping, SessionFactory, SqmCreationContext, SqlAstCreationContext, QueryParameterBindingTypeResolver {
	/**
	 * Get the UUID for this SessionFactory.  The value is generated as a {@link java.util.UUID}, but kept
	 * as a String.
	 *
	 * @return The UUID for this SessionFactory.
	 *
	 * @see org.hibernate.internal.SessionFactoryRegistry#getSessionFactory
	 */
	String getUuid();

	/**
	 * Access to the name (if one) assigned to the SessionFactory
	 *
	 * @return The name for the SessionFactory
	 */
	String getName();

	TypeConfiguration getTypeConfiguration();

	default SessionFactoryImplementor getSessionFactory() {
		return this;
	}

	@Override
	default MappingMetamodel getDomainModel() {
		return getMetamodel();
	}

	QueryEngine getQueryEngine();

	@Override
	HibernateCriteriaBuilder getCriteriaBuilder();

	@Override
	SessionBuilderImplementor withOptions();

	/**
	 * Get a non-transactional "current" session (used by hibernate-envers)
	 */
	Session openTemporarySession() throws HibernateException;

	@Override
	CacheImplementor getCache();

	@Override
	StatisticsImplementor getStatistics();

	RuntimeMetamodels getRuntimeMetamodels();

	/**
	 * Access to the ServiceRegistry for this SessionFactory.
	 *
	 * @return The factory's ServiceRegistry
	 */
	ServiceRegistryImplementor getServiceRegistry();

	/**
	 * Get the EventEngine associated with this SessionFactory
	 */
	EventEngine getEventEngine();

	/**
	 * Retrieve fetch profile by name.
	 *
	 * @param name The name of the profile to retrieve.
	 * @return The profile definition
	 */
	FetchProfile getFetchProfile(String name);

	/**
	 * Get the identifier generator for the hierarchy
	 */
	IdentifierGenerator getIdentifierGenerator(String rootEntityName);


	EntityNotFoundDelegate getEntityNotFoundDelegate();

	void addObserver(SessionFactoryObserver observer);

	/**
	 * @todo make a Service ?
	 */
	CustomEntityDirtinessStrategy getCustomEntityDirtinessStrategy();

	/**
	 * @todo make a Service ?
	 */
	CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver();

	/**
	 * @deprecated (since 5.2) use {@link #getMetamodel()} -> {@link MetamodelImplementor#getEntityNameResolvers()}
	 */
	@Deprecated
	default Iterable<EntityNameResolver> iterateEntityNameResolvers() {
		return getMetamodel().getEntityNameResolvers();
	}

	/**
	 * @return the FastSessionServices instance associated with this SessionFactory
	 */
	FastSessionServices getFastSessionServices();

	WrapperOptions getWrapperOptions();

	/**
	 * Contract for resolving this SessionFactory on deserialization
	 */
	interface DeserializationResolver<T extends SessionFactoryImplementor> extends Serializable {
		T resolve();
	}

	DeserializationResolver getDeserializationResolver();



	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Deprecations

	/**
	 * @deprecated (since 5.2) Just use {@link #getStatistics} (with covariant return here as {@link StatisticsImplementor}).
	 */
	@Deprecated
	default StatisticsImplementor getStatisticsImplementor() {
		return getStatistics();
	}

	/**
	 * Get the JdbcServices.
	 *
	 * @return the JdbcServices
	 */
	JdbcServices getJdbcServices();

	/**
	 * Get the SQL dialect.
	 * <p/>
	 * Shorthand for {@code getJdbcServices().getDialect()}
	 *
	 * @return The dialect
	 *
	 * @deprecated (since 5.2) instead, use {@link JdbcServices#getDialect()}
	 */
	@Deprecated
	default Dialect getDialect() {
		return getJdbcServices().getDialect();
	}

	SqlStringGenerationContext getSqlStringGenerationContext();

	/**
	 * Retrieves the SQLExceptionConverter in effect for this SessionFactory.
	 *
	 * @return The SQLExceptionConverter for this SessionFactory.
	 *
	 * @deprecated since 5.0; use {@link JdbcServices#getSqlExceptionHelper()} ->
	 * {@link SqlExceptionHelper#getSqlExceptionConverter()} instead as obtained from {@link #getServiceRegistry()}
	 */
	@Deprecated
	default SQLExceptionConverter getSQLExceptionConverter() {
		return getJdbcServices().getSqlExceptionHelper().getSqlExceptionConverter();
	}

	/**
	 * Retrieves the SqlExceptionHelper in effect for this SessionFactory.
	 *
	 * @return The SqlExceptionHelper for this SessionFactory.
	 *
	 * @deprecated since 5.0; use {@link JdbcServices#getSqlExceptionHelper()} instead as
	 * obtained from {@link #getServiceRegistry()}
	 */
	@Deprecated
	default SqlExceptionHelper getSQLExceptionHelper() {
		return getJdbcServices().getSqlExceptionHelper();
	}

	/**
	 * @deprecated since 5.0; use {@link #getSessionFactoryOptions()} instead
	 */
	@Deprecated
	@SuppressWarnings("deprecation")
	Settings getSettings();


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// map these to Metamodel


	@Override
	MetamodelImplementor getMetamodel();

	@Override
	@SuppressWarnings("unchecked")
	default <T> List<EntityGraph<? super T>> findEntityGraphsByType(Class<T> entityClass) {
		return (List) findEntityGraphsByJavaType( entityClass );
	}

	<T> List<RootGraphImplementor<? super T>> findEntityGraphsByJavaType(Class<T> entityClass);

	RootGraphImplementor<?> findEntityGraphByName(String name);

	/**
	 * The best guess entity name for an entity not in an association
	 */
	String bestGuessEntityName(Object object);

	/**
	 * @deprecated (since 5.2) Use {@link MetamodelImplementor#entityPersister(Class)} instead.
	 */
	@Deprecated
	default EntityPersister getEntityPersister(String entityName) throws MappingException {
		return getMetamodel().entityPersister( entityName );
	}

	/**
	 * @deprecated (since 5.2) Use {@link MetamodelImplementor#entityPersisters} instead.
	 */
	@Deprecated
	default Map<String,EntityPersister> getEntityPersisters() {
		return getMetamodel().entityPersisters();
	}

	/**
	 * @deprecated (since 5.2) Use {@link MetamodelImplementor#collectionPersister(String)} instead.
	 */
	@Deprecated
	default CollectionPersister getCollectionPersister(String role) throws MappingException {
		return getMetamodel().collectionPersister( role );
	}

	/**
	 * @deprecated (since 5.2) Use {@link MetamodelImplementor#collectionPersisters} instead.
	 */
	@Deprecated
	default Map<String, CollectionPersister> getCollectionPersisters() {
		return getMetamodel().collectionPersisters();
	}

	/**
	 * @deprecated (since 5.2) Use {@link MetamodelImplementor#collectionPersisters} instead.
	 * Retrieves a set of all the collection roles in which the given entity
	 * is a participant, as either an index or an element.
	 *
	 * @param entityName The entity name for which to get the collection roles.
	 * @return set of all the collection roles in which the given entityName participates.
	 */
	@Deprecated
	default Set<String> getCollectionRolesByEntityParticipant(String entityName) {
		return getMetamodel().getCollectionRolesByEntityParticipant( entityName );
	}

	/**
	 * @deprecated (since 5.2) Use {@link MetamodelImplementor#locateEntityPersister(Class)} instead.
	 */
	@Deprecated
	default EntityPersister locateEntityPersister(Class byClass) {
		return getMetamodel().locateEntityPersister( byClass );
	}

	/**
	 * @deprecated (since 5.2) Use {@link MetamodelImplementor#locateEntityPersister(String)} instead.
	 */
	@Deprecated
	default EntityPersister locateEntityPersister(String byName) {
		return getMetamodel().locateEntityPersister( byName );
	}

	/**
	 * Get the names of all persistent classes that implement/extend the given interface/class
	 *
	 * @deprecated Use {@link Metamodel#getImplementors(String)} instead
	 */
	@Deprecated
	default String[] getImplementors(String entityName) {
		return getMetamodel().getImplementors( entityName );
	}

	/**
	 * Get a class name, using query language imports
	 *
	 * @deprecated Use {@link Metamodel#getImportedClassName(String)} instead
	 */
	@Deprecated
	default String getImportedClassName(String name) {
		return getMetamodel().getImportedClassName( name );
	}
}
