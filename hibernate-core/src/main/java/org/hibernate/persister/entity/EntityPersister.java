/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.persister.entity;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.MappingException;
import org.hibernate.bytecode.enhance.spi.interceptor.EnhancementAsProxyLazinessInterceptor;
import org.hibernate.bytecode.spi.BytecodeEnhancementMetadata;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.cache.spi.entry.CacheEntryStructure;
import org.hibernate.engine.spi.CascadeStyle;
import org.hibernate.engine.spi.EntityEntryFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.ValueInclusion;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.internal.FilterAliasGenerator;
import org.hibernate.internal.TableGroupFilterAliasGenerator;
import org.hibernate.loader.ast.spi.Loadable;
import org.hibernate.loader.ast.spi.MultiIdLoadOptions;
import org.hibernate.loader.ast.spi.MultiNaturalIdLoader;
import org.hibernate.loader.ast.spi.NaturalIdLoader;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.internal.InFlightEntityMappingType;
import org.hibernate.metamodel.spi.EntityRepresentationStrategy;
import org.hibernate.persister.walking.spi.EntityDefinition;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableInsertStrategy;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableMutationStrategy;
import org.hibernate.sql.ast.spi.SqlAliasStemHelper;
import org.hibernate.sql.ast.tree.from.RootTableGroupProducer;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.type.BasicType;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.java.VersionJavaType;

/**
 * Contract describing mapping information and persistence logic for a particular strategy of entity mapping.  A given
 * persister instance corresponds to a given mapped entity class.
 * <p/>
 * Implementations must be thread-safe (preferably immutable).
 * <p/>
 * Unless a custom {@link org.hibernate.persister.spi.PersisterFactory} is used, it is expected
 * that implementations of EntityPersister define a constructor accepting the following arguments:<ol>
 *     <li>
 *         {@link org.hibernate.mapping.PersistentClass} - describes the metadata about the entity
 *         to be handled by the persister
 *     </li>
 *     <li>
 *         {@link EntityDataAccess} - the second level caching strategy for this entity
 *     </li>
 *     <li>
 *         {@link NaturalIdDataAccess} - the second level caching strategy for the natural-id
 *         defined for this entity, if one
 *     </li>
 *     <li>
 *         {@link org.hibernate.persister.spi.PersisterCreationContext} - access to additional
 *         information useful while constructing the persister.
 *     </li>
 * </ol>
 *
 * @author Gavin King
 * @author Steve Ebersole
 *
 * @see org.hibernate.persister.spi.PersisterFactory
 * @see org.hibernate.persister.spi.PersisterClassResolver
 */
public interface EntityPersister
		extends EntityMappingType, Loadable, RootTableGroupProducer, EntityDefinition {

	/**
	 * The property name of the "special" identifier property in HQL
	 */
	String ENTITY_ID = "id";

	/**
	 * Generate the entity definition for this object. This must be done for all
	 * entity persisters before calling {@link #postInstantiate()}.
	 *
	 * @deprecated The legacy "walking model" is deprecated in favor of the newer "mapping model".
	 * This method is no longer called by Hibernate.  See {@link InFlightEntityMappingType#prepareMappingModel} instead
	 */
	@Deprecated
	void generateEntityDefinition();

	/**
	 * Finish the initialization of this object. {@link InFlightEntityMappingType#prepareMappingModel}
	 * must be called for all entity persisters before calling this method.
	 * <p/>
	 * Called only once per {@link org.hibernate.SessionFactory} lifecycle,
	 * after all entity persisters have been instantiated.
	 *
	 * @throws MappingException Indicates an issue in the metadata.
	 */
	void postInstantiate() throws MappingException;

	/**
	 * Return the SessionFactory to which this persister "belongs".
	 *
	 * @return The owning SessionFactory.
	 */
	SessionFactoryImplementor getFactory();

	@Override
	default String getSqlAliasStem() {
		return SqlAliasStemHelper.INSTANCE.generateStemFromEntityName( getEntityName() );
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // stuff that is persister-centric and/or EntityInfo-centric ~~~~~~~~~~~~~~
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Get the EntityEntryFactory indicated for the entity mapped by this persister.
	 *
	 * @return The proper EntityEntryFactory.
	 */
	EntityEntryFactory getEntityEntryFactory();

	/**
	 * Returns an object that identifies the space in which identifiers of
	 * this entity hierarchy are unique.  Might be a table name, a JNDI URL, etc.
	 *
	 * @return The root entity name.
	 */
	String getRootEntityName();

	/**
	 * The entity name which this persister maps.
	 *
	 * @return The name of the entity which this persister maps.
	 */
	String getEntityName();

	/**
	 * The strategy to use for SQM mutation statements where the target entity
	 * has multiple tables.  Returns {@code null} to indicate that the entity
	 * does not define multiple tables
	 */
	SqmMultiTableMutationStrategy getSqmMultiTableMutationStrategy();

	SqmMultiTableInsertStrategy getSqmMultiTableInsertStrategy();
	/**
	 * Retrieve the underlying entity metamodel instance...
	 *
	 *@return The metamodel
	 */
	EntityMetamodel getEntityMetamodel();

	/**
	 * Called from {@link EnhancementAsProxyLazinessInterceptor} to trigger load of
	 * the entity's non-lazy state as well as the named attribute we are accessing
	 * if it is still uninitialized after fetching non-lazy state
	 */
	default Object initializeEnhancedEntityUsedAsProxy(
			Object entity,
			String nameOfAttributeBeingAccessed,
			SharedSessionContractImplementor session) {
		throw new UnsupportedOperationException(
				"Initialization of entity enhancement used to act like a proxy is not supported by this EntityPersister : " + getClass().getName()
		);
	}

	/**
	 * Determine whether the given name represents a subclass entity
	 * (or this entity itself) of the entity mapped by this persister.
	 *
	 * @param entityName The entity name to be checked.
	 * @return True if the given entity name represents either the entity
	 * mapped by this persister or one of its subclass entities; false
	 * otherwise.
	 */
	boolean isSubclassEntityName(String entityName);

	/**
	 * Returns an array of objects that identify spaces in which properties of
	 * this entity are persisted, for instances of this class only.
	 * <p/>
	 * For most implementations, this returns the complete set of table names
	 * to which instances of the mapped entity are persisted (not accounting
	 * for superclass entity mappings).
	 *
	 * @return The property spaces.
	 */
	Serializable[] getPropertySpaces();

	/**
	 * Returns an array of objects that identify spaces in which properties of
	 * this entity are persisted, for instances of this class and its subclasses.
	 * <p/>
	 * Much like {@link #getPropertySpaces()}, except that here we include subclass
	 * entity spaces.
	 *
	 * @return The query spaces.
	 */
	Serializable[] getQuerySpaces();

	/**
	 * The table names this entity needs to be synchronized against.
	 * <p>
	 * Much like {@link #getPropertySpaces()}, except that here we include subclass
	 * entity spaces.
	 *
	 * @return The synchronization spaces.
	 */
	default String[] getSynchronizationSpaces() {
		return (String[]) getQuerySpaces();
	}

	/**
	 * Returns an array of objects that identify spaces in which properties of
	 * this entity are persisted, for instances of this class and its subclasses.
	 * <p/>
	 * Much like {@link #getPropertySpaces()}, except that here we include subclass
	 * entity spaces.
	 *
	 * @return The query spaces.
	 */
	default String[] getSynchronizedQuerySpaces() {
		return (String[]) getQuerySpaces();
	}

	default void visitQuerySpaces(Consumer<String> querySpaceConsumer) {
		final String[] spaces = getSynchronizedQuerySpaces();
		for ( int i = 0; i < spaces.length; i++ ) {
			querySpaceConsumer.accept( spaces[ i ] );
		}
	}

	/**
	 * Determine whether this entity supports dynamic proxies.
	 *
	 * @return True if the entity has dynamic proxy support; false otherwise.
	 */
	boolean hasProxy();

	/**
	 * Determine whether this entity contains references to persistent collections.
	 *
	 * @return True if the entity does contain persistent collections; false otherwise.
	 */
	boolean hasCollections();

	/**
	 * Determine whether any properties of this entity are considered mutable.
	 *
	 * @return True if any properties of the entity are mutable; false otherwise (meaning none are).
	 */
	boolean hasMutableProperties();

	/**
	 * Determine whether this entity contains references to persistent collections
	 * which are fetchable by subselect?
	 *
	 * @return True if the entity contains collections fetchable by subselect; false otherwise.
	 */
	boolean hasSubselectLoadableCollections();

	/**
	 * Determine whether this entity has any non-none cascading.
	 *
	 * @return True if the entity has any properties with a cascade other than NONE;
	 * false otherwise (aka, no cascading).
	 */
	boolean hasCascades();

	/**
	 * Determine whether instances of this entity are considered mutable.
	 *
	 * @return True if the entity is considered mutable; false otherwise.
	 */
	boolean isMutable();

	/**
	 * Determine whether the entity is inherited one or more other entities.
	 * In other words, is this entity a subclass of other entities.
	 *
	 * @return True if other entities extend this entity; false otherwise.
	 */
	boolean isInherited();

	/**
	 * Are identifiers of this entity assigned known before the insert execution?
	 * Or, are they generated (in the database) by the insert execution.
	 *
	 * @return True if identifiers for this entity are generated by the insert
	 * execution.
	 */
	boolean isIdentifierAssignedByInsert();

	/**
	 * Get the type of a particular property by name.
	 *
	 * @param propertyName The name of the property for which to retrieve
	 * the type.
	 * @return The type.
	 * @throws MappingException Typically indicates an unknown
	 * property name.
	 */
	Type getPropertyType(String propertyName) throws MappingException;

	/**
	 * Compare the two snapshots to determine if they represent dirty state.
	 *
	 * @param currentState The current snapshot
	 * @param previousState The baseline snapshot
	 * @param owner The entity containing the state
	 * @param session The originating session
	 * @return The indices of all dirty properties, or null if no properties
	 * were dirty.
	 */
	int[] findDirty(Object[] currentState, Object[] previousState, Object owner, SharedSessionContractImplementor session);

	/**
	 * Compare the two snapshots to determine if they represent modified state.
	 *
	 * @param old The baseline snapshot
	 * @param current The current snapshot
	 * @param object The entity containing the state
	 * @param session The originating session
	 * @return The indices of all modified properties, or null if no properties
	 * were modified.
	 */
	int[] findModified(Object[] old, Object[] current, Object object, SharedSessionContractImplementor session);

	/**
	 * Determine whether the entity has a particular property holding
	 * the identifier value.
	 *
	 * @return True if the entity has a specific property holding identifier value.
	 */
	boolean hasIdentifierProperty();

	/**
	 * Determine whether detached instances of this entity carry their own
	 * identifier value.
	 * <p/>
	 * The other option is the deprecated feature where users could supply
	 * the id during session calls.
	 *
	 * @return True if either (1) {@link #hasIdentifierProperty()} or
	 * (2) the identifier is an embedded composite identifier; false otherwise.
	 */
	boolean canExtractIdOutOfEntity();

	/**
	 * Determine whether optimistic locking by column is enabled for this
	 * entity.
	 *
	 * @return True if optimistic locking by column (i.e., <version/> or
	 * <timestamp/>) is enabled; false otherwise.
	 */
	boolean isVersioned();

	/**
	 * If {@link #isVersioned()}, then what is the type of the property
	 * holding the locking value.
	 *
	 * @return The type of the version property; or null, if not versioned.
	 */
	BasicType<?> getVersionType();

	default VersionJavaType<Object> getVersionJavaTypeDescriptor() {
		final BasicType<?> versionType = getVersionType();
		//noinspection unchecked
		return versionType == null
				? null
				: (VersionJavaType<Object>) versionType.getJavaTypeDescriptor();
	}

	/**
	 * If {@link #isVersioned()}, then what is the index of the property
	 * holding the locking value.
	 *
	 * @return The type of the version property; or -66, if not versioned.
	 */
	int getVersionProperty();

	/**
	 * Determine whether this entity defines a natural identifier.
	 *
	 * @return True if the entity defines a natural id; false otherwise.
	 */
	boolean hasNaturalIdentifier();

	/**
	 * If the entity defines a natural id ({@link #hasNaturalIdentifier()}), which
	 * properties make up the natural id.
	 *
	 * @return The indices of the properties making of the natural id; or
	 * null, if no natural id is defined.
	 */
	int[] getNaturalIdentifierProperties();

	/**
	 * Retrieve the current state of the natural-id properties from the database.
	 *
	 * @param id The identifier of the entity for which to retrieve the natural-id values.
	 * @param session The session from which the request originated.
	 * @return The natural-id snapshot.
	 */
	Object getNaturalIdentifierSnapshot(Object id, SharedSessionContractImplementor session);

	/**
	 * Determine which identifier generation strategy is used for this entity.
	 *
	 * @return The identifier generation strategy.
	 */
	IdentifierGenerator getIdentifierGenerator();

	@Override
	default AttributeMapping getAttributeMapping(int position) {
		return getAttributeMappings().get( position );
	}

	@Override
	default void breakDownJdbcValues(Object domainValue, JdbcValueConsumer valueConsumer, SharedSessionContractImplementor session) {
		final List<AttributeMapping> attributeMappings = getAttributeMappings();
		if ( domainValue instanceof Object[] ) {
			final Object[] values = (Object[]) domainValue;
			for ( int i = 0; i < attributeMappings.size(); i++ ) {
				final AttributeMapping attributeMapping = attributeMappings.get( i );
				attributeMapping.breakDownJdbcValues( values[ i ], valueConsumer, session );
			}
		}
		else {
			for ( int i = 0; i < attributeMappings.size(); i++ ) {
				final AttributeMapping attributeMapping = attributeMappings.get( i );
				final Object attributeValue = attributeMapping.getPropertyAccess().getGetter().get( domainValue );
				attributeMapping.breakDownJdbcValues( attributeValue, valueConsumer, session );
			}
		}
	}

	/**
	 * Determine whether this entity defines any lazy properties (ala
	 * bytecode instrumentation).
	 *
	 * @return True if the entity has properties mapped as lazy; false otherwise.
	 */
	boolean hasLazyProperties();

	default NaturalIdLoader<?> getNaturalIdLoader() {
		throw new UnsupportedOperationException(
				"EntityPersister implementation `" + getClass().getName() + "` does not support `NaturalIdLoader`"
		);
	}

	default MultiNaturalIdLoader<?> getMultiNaturalIdLoader() {
		throw new UnsupportedOperationException(
				"EntityPersister implementation `" + getClass().getName() + "` does not support `MultiNaturalIdLoader`"
		);
	}

	/**
	 * Load the id for the entity based on the natural id.
	 */
	Object loadEntityIdByNaturalId(
			Object[] naturalIdValues,
			LockOptions lockOptions,
			SharedSessionContractImplementor session);

	/**
	 * Load an instance of the persistent class.
	 */
	Object load(Object id, Object optionalObject, LockMode lockMode, SharedSessionContractImplementor session);

	default Object load(Object id, Object optionalObject, LockMode lockMode, SharedSessionContractImplementor session, Boolean readOnly)
	throws HibernateException {
		return load( id, optionalObject, lockMode, session );
	}

	/**
	 * Load an instance of the persistent class.
	 */
	Object load(Object id, Object optionalObject, LockOptions lockOptions, SharedSessionContractImplementor session);

	default Object load(Object id, Object optionalObject, LockOptions lockOptions, SharedSessionContractImplementor session, Boolean readOnly)
			throws HibernateException {
		return load( id, optionalObject, lockOptions, session );
	}

	/**
	 * Performs a load of multiple entities (of this type) by identifier simultaneously.
	 *
	 * @param ids The identifiers to load
	 * @param session The originating Session
	 * @param loadOptions The options for loading
	 *
	 * @return The loaded, matching entities
	 */
	List<?> multiLoad(Object[] ids, SharedSessionContractImplementor session, MultiIdLoadOptions loadOptions);

	/**
	 * Do a version check (optional operation)
	 */
	void lock(Object id, Object version, Object object, LockMode lockMode, SharedSessionContractImplementor session);

	/**
	 * Do a version check (optional operation)
	 */
	void lock(Object id, Object version, Object object, LockOptions lockOptions, SharedSessionContractImplementor session);

	/**
	 * Persist an instance
	 */
	void insert(Object id, Object[] fields, Object object, SharedSessionContractImplementor session);

	/**
	 * Persist an instance, using a natively generated identifier (optional operation)
	 */
	Object insert(Object[] fields, Object object, SharedSessionContractImplementor session);

	/**
	 * Delete a persistent instance
	 */
	void delete(Object id, Object version, Object object, SharedSessionContractImplementor session);

	/**
	 * Update a persistent instance
	 */
	void update(
			Object id,
			Object[] fields,
			int[] dirtyFields,
			boolean hasDirtyCollection,
			Object[] oldFields,
			Object oldVersion,
			Object object,
			Object rowId,
			SharedSessionContractImplementor session);

	/**
	 * Get the Hibernate types of the class properties
	 */
	Type[] getPropertyTypes();

	/**
	 * Get the names of the class properties - doesn't have to be the names of the
	 * actual Java properties (used for XML generation only)
	 */
	String[] getPropertyNames();

	/**
	 * Get the "insertability" of the properties of this class
	 * (does the property appear in an SQL INSERT)
	 */
	boolean[] getPropertyInsertability();

	/**
	 * Which of the properties of this class are database generated values on insert?
	 *
	 * @deprecated Replaced internally with InMemoryValueGenerationStrategy / InDatabaseValueGenerationStrategy
	 */
	@Deprecated
	ValueInclusion[] getPropertyInsertGenerationInclusions();

	/**
	 * Which of the properties of this class are database generated values on update?
	 *
	 * @deprecated Replaced internally with InMemoryValueGenerationStrategy / InDatabaseValueGenerationStrategy
	 */
	@Deprecated
	ValueInclusion[] getPropertyUpdateGenerationInclusions();

	/**
	 * Get the "updateability" of the properties of this class
	 * (does the property appear in an SQL UPDATE)
	 */
	boolean[] getPropertyUpdateability();

	/**
	 * Get the "checkability" of the properties of this class
	 * (is the property dirty checked, does the cache need
	 * to be updated)
	 */
	boolean[] getPropertyCheckability();

	/**
	 * Get the nullability of the properties of this class
	 */
	boolean[] getPropertyNullability();

	/**
	 * Get the "versionability" of the properties of this class
	 * (is the property optimistic-locked)
	 */
	boolean[] getPropertyVersionability();
	boolean[] getPropertyLaziness();
	/**
	 * Get the cascade styles of the properties (optional operation)
	 */
	CascadeStyle[] getPropertyCascadeStyles();

	/**
	 * Get the identifier type
	 */
	Type getIdentifierType();

	/**
	 * Get the name of the identifier property (or return null) - need not return the
	 * name of an actual Java property
	 */
	String getIdentifierPropertyName();

	/**
	 * Should we always invalidate the cache instead of
	 * recaching updated state
	 */
	boolean isCacheInvalidationRequired();
	/**
	 * Should lazy properties of this entity be cached?
	 */
	boolean isLazyPropertiesCacheable();

	boolean canReadFromCache();
	boolean canWriteToCache();

	/**
	 * Does this class have a cache.
	 *
	 * @deprecated Use {@link #canReadFromCache()} and/or {@link #canWriteToCache()} depending on need
	 */
	@Deprecated
	boolean hasCache();
	/**
	 * Get the cache (optional operation)
	 */
	EntityDataAccess getCacheAccessStrategy();
	/**
	 * Get the cache structure
	 */
	CacheEntryStructure getCacheEntryStructure();

	CacheEntry buildCacheEntry(Object entity, Object[] state, Object version, SharedSessionContractImplementor session);

	/**
	 * Does this class have a natural id cache
	 */
	boolean hasNaturalIdCache();

	/**
	 * Get the NaturalId cache (optional operation)
	 */
	NaturalIdDataAccess getNaturalIdCacheAccessStrategy();

	/**
	 * Get the user-visible metadata for the class (optional operation)
	 */
	ClassMetadata getClassMetadata();

	/**
	 * Is batch loading enabled?
	 */
	boolean isBatchLoadable();

	/**
	 * Is select snapshot before update enabled?
	 */
	boolean isSelectBeforeUpdateRequired();

	/**
	 * Get the current database state of the object, in a "hydrated" form, without
	 * resolving identifiers
	 * @return null if there is no row in the database
	 */
	Object[] getDatabaseSnapshot(Object id, SharedSessionContractImplementor session) throws HibernateException;

	Object getIdByUniqueKey(Object key, String uniquePropertyName, SharedSessionContractImplementor session);

	/**
	 * Get the current version of the object, or return null if there is no row for
	 * the given identifier. In the case of unversioned data, return any object
	 * if the row exists.
	 */
	Object getCurrentVersion(Object id, SharedSessionContractImplementor session) throws HibernateException;

	Object forceVersionIncrement(Object id, Object currentVersion, SharedSessionContractImplementor session) throws HibernateException;

	/**
	 * Has the class actually been bytecode instrumented?
	 */
	boolean isInstrumented();

	/**
	 * Does this entity define any properties as being database generated on insert?
	 *
	 * @return True if this entity contains at least one property defined
	 * as generated (including version property, but not identifier).
	 */
	boolean hasInsertGeneratedProperties();

	/**
	 * Does this entity define any properties as being database generated on update?
	 *
	 * @return True if this entity contains at least one property defined
	 * as generated (including version property, but not identifier).
	 */
	boolean hasUpdateGeneratedProperties();

	/**
	 * Does this entity contain a version property that is defined
	 * to be database generated?
	 *
	 * @return true if this entity contains a version property and that
	 * property has been marked as generated.
	 */
	boolean isVersionPropertyGenerated();


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// stuff that is tuplizer-centric, but is passed a session ~~~~~~~~~~~~~~~~
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Called just after the entities properties have been initialized
	 */
	void afterInitialize(Object entity, SharedSessionContractImplementor session);

	/**
	 * Called just after the entity has been reassociated with the session
	 */
	void afterReassociate(Object entity, SharedSessionContractImplementor session);

	/**
	 * Create a new proxy instance
	 */
	Object createProxy(Object id, SharedSessionContractImplementor session);

	/**
	 * Is this a new transient instance?
	 */
	Boolean isTransient(Object object, SharedSessionContractImplementor session);

	/**
	 * Return the values of the insertable properties of the object (including backrefs)
	 */
	Object[] getPropertyValuesToInsert(Object object, Map mergeMap, SharedSessionContractImplementor session);

	/**
	 * Perform a select to retrieve the values of any generated properties
	 * back from the database, injecting these generated values into the
	 * given entity as well as writing this state to the
	 * {@link org.hibernate.engine.spi.PersistenceContext}.
	 * <p/>
	 * Note, that because we update the PersistenceContext here, callers
	 * need to take care that they have already written the initial snapshot
	 * to the PersistenceContext before calling this method.
	 */
	void processInsertGeneratedProperties(Object id, Object entity, Object[] state, SharedSessionContractImplementor session);

	/**
	 * Perform a select to retrieve the values of any generated properties
	 * back from the database, injecting these generated values into the
	 * given entity as well as writing this state to the
	 * {@link org.hibernate.engine.spi.PersistenceContext}.
	 * <p/>
	 * Note, that because we update the PersistenceContext here, callers
	 * need to take care that they have already written the initial snapshot
	 * to the PersistenceContext before calling this method.
	 */
	void processUpdateGeneratedProperties(Object id, Object entity, Object[] state, SharedSessionContractImplementor session);


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// stuff that is Tuplizer-centric ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * The persistent class, or null
	 */
	Class<?> getMappedClass();

	/**
	 * Does the class implement the {@link org.hibernate.classic.Lifecycle} interface.
	 */
	boolean implementsLifecycle();

	/**
	 * Get the proxy interface that instances of <em>this</em> concrete class will be
	 * cast to (optional operation).
	 */
	Class<?> getConcreteProxyClass();

	default void setValues(Object object, Object[] values) {
		setPropertyValues( object, values );
	}

	/**
	 * Set the given values to the mapped properties of the given object
	 *
	 * @deprecated as of 6.0.  Use {@link #setValues} instead
	 */
	@Deprecated
	void setPropertyValues(Object object, Object[] values);

	default void setValue(Object object, int i, Object value) {
		setPropertyValue( object, i, value );
	}

	/**
	 * Set the value of a particular property
	 *
	 * @deprecated as of 6.0.  Use {@link #setValue} instead
	 */
	@Deprecated
	void setPropertyValue(Object object, int i, Object value);

	default Object[] getValues(Object object) {
		return getPropertyValues( object );
	}

	/**
	 * @deprecated as of 6.0.  Use {@link #getValues} instead
	 */
	@Deprecated
	Object[] getPropertyValues(Object object);

	default Object getValue(Object object, int i) {
		return getPropertyValue( object, i );
	}

	/**
	 * @deprecated as of 6.0.  Use {@link #getValue} instead
	 */
	@Deprecated
	Object getPropertyValue(Object object, int i) throws HibernateException;

	/**
	 * Get the value of a particular property
	 */
	Object getPropertyValue(Object object, String propertyName);

	/**
	 * Get the identifier of an instance (throw an exception if no identifier property)
	 */
	Object getIdentifier(Object entity, SharedSessionContractImplementor session);

    /**
     * Inject the identifier value into the given entity.
	 */
	void setIdentifier(Object entity, Object id, SharedSessionContractImplementor session);

	/**
	 * Get the version number (or timestamp) from the object's version property (or return null if not versioned)
	 */
	Object getVersion(Object object) throws HibernateException;

	/**
	 * Create a class instance initialized with the given identifier
	 *
	 * @param id The identifier value to use (may be null to represent no value)
	 * @param session The session from which the request originated.
	 *
	 * @return The instantiated entity.
	 */
	Object instantiate(Object id, SharedSessionContractImplementor session);

	/**
	 * Is the given object an instance of this entity?
	 */
	boolean isInstance(Object object);

	/**
	 * Does the given instance have any uninitialized lazy properties?
	 */
	boolean hasUninitializedLazyProperties(Object object);

	/**
	 * Set the identifier and version of the given instance back to its "unsaved" value.
	 */
	void resetIdentifier(Object entity, Object currentId, Object currentVersion, SharedSessionContractImplementor session);

	/**
	 * A request has already identified the entity-name of this persister as the mapping for the given instance.
	 * However, we still need to account for possible subclassing and potentially re-route to the more appropriate
	 * persister.
	 * <p/>
	 * For example, a request names <tt>Animal</tt> as the entity-name which gets resolved to this persister.  But the
	 * actual instance is really an instance of <tt>Cat</tt> which is a subclass of <tt>Animal</tt>.  So, here the
	 * <tt>Animal</tt> persister is being asked to return the persister specific to <tt>Cat</tt>.
	 * <p/>
	 * It is also possible that the instance is actually an <tt>Animal</tt> instance in the above example in which
	 * case we would return <tt>this</tt> from this method.
	 *
	 * @param instance The entity instance
	 * @param factory Reference to the SessionFactory
	 *
	 * @return The appropriate persister
	 *
	 * @throws HibernateException Indicates that instance was deemed to not be a subclass of the entity mapped by
	 * this persister.
	 */
	EntityPersister getSubclassEntityPersister(Object instance, SessionFactoryImplementor factory);

	EntityRepresentationStrategy getRepresentationStrategy();

	@Override
	default EntityMappingType getEntityMappingType() {
		return this;
	}

	/**
	 * @deprecated Use {@link #getRepresentationStrategy()}
	 */
	@Deprecated
	EntityTuplizer getEntityTuplizer();

	BytecodeEnhancementMetadata getInstrumentationMetadata();

	default BytecodeEnhancementMetadata getBytecodeEnhancementMetadata() {
		return getInstrumentationMetadata();
	}

	FilterAliasGenerator getFilterAliasGenerator(final String rootAlias);

	default FilterAliasGenerator getFilterAliasGenerator(TableGroup rootTableGroup) {
		assert this instanceof Joinable;
		return new TableGroupFilterAliasGenerator( ( (Joinable) this ).getTableName(), rootTableGroup );
	}

	/**
	 * Converts an array of attribute names to a set of indexes, according to the entity metamodel
	 *
	 * @param attributeNames Array of names to be resolved
	 *
	 * @return A set of unique indexes of the attribute names found in the metamodel
	 */
	int[] resolveAttributeIndexes(String[] attributeNames);

	/**
	 * Like {@link #resolveAttributeIndexes(String[])} but also always returns mutable attributes
	 *
	 *
	 * @param values
	 * @param loadedState
	 * @param attributeNames Array of names to be resolved
	 *
	 * @param session
	 * @return A set of unique indexes of the attribute names found in the metamodel
	 */
	default int[] resolveDirtyAttributeIndexes(
			Object[] values,
			Object[] loadedState,
			String[] attributeNames,
			SessionImplementor session) {
		return resolveAttributeIndexes( attributeNames );
	}

	boolean canUseReferenceCacheEntries();

	/**
	 * @deprecated Since 5.4.1, this is no longer used.
	 */
	@Deprecated
	default boolean canIdentityInsertBeDelayed() {
		return false;
	}
}
