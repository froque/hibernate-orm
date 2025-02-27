/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.CacheMode;
import org.hibernate.Filter;
import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.IdentifierLoadAccess;
import org.hibernate.JDBCException;
import org.hibernate.LobHelper;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.MappingException;
import org.hibernate.MultiIdentifierLoadAccess;
import org.hibernate.NaturalIdLoadAccess;
import org.hibernate.NaturalIdMultiLoadAccess;
import org.hibernate.ObjectDeletedException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionException;
import org.hibernate.SharedSessionBuilder;
import org.hibernate.SimpleNaturalIdLoadAccess;
import org.hibernate.Transaction;
import org.hibernate.TransientObjectException;
import org.hibernate.TypeMismatchException;
import org.hibernate.UnknownProfileException;
import org.hibernate.UnresolvableObjectException;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.internal.StatefulPersistenceContext;
import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.jdbc.NonContextualLobCreator;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.spi.ActionQueue;
import org.hibernate.engine.spi.EffectiveEntityGraph;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.engine.transaction.spi.TransactionImplementor;
import org.hibernate.engine.transaction.spi.TransactionObserver;
import org.hibernate.event.spi.AutoFlushEvent;
import org.hibernate.event.spi.AutoFlushEventListener;
import org.hibernate.event.spi.ClearEvent;
import org.hibernate.event.spi.ClearEventListener;
import org.hibernate.event.spi.DeleteEvent;
import org.hibernate.event.spi.DeleteEventListener;
import org.hibernate.event.spi.DirtyCheckEvent;
import org.hibernate.event.spi.DirtyCheckEventListener;
import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.EvictEvent;
import org.hibernate.event.spi.EvictEventListener;
import org.hibernate.event.spi.FlushEvent;
import org.hibernate.event.spi.FlushEventListener;
import org.hibernate.event.spi.InitializeCollectionEvent;
import org.hibernate.event.spi.InitializeCollectionEventListener;
import org.hibernate.event.spi.LoadEvent;
import org.hibernate.event.spi.LoadEventListener;
import org.hibernate.event.spi.LoadEventListener.LoadType;
import org.hibernate.event.spi.LockEvent;
import org.hibernate.event.spi.LockEventListener;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.event.spi.MergeEventListener;
import org.hibernate.event.spi.PersistEvent;
import org.hibernate.event.spi.PersistEventListener;
import org.hibernate.event.spi.RefreshEvent;
import org.hibernate.event.spi.RefreshEventListener;
import org.hibernate.event.spi.ReplicateEvent;
import org.hibernate.event.spi.ReplicateEventListener;
import org.hibernate.event.spi.ResolveNaturalIdEvent;
import org.hibernate.event.spi.ResolveNaturalIdEventListener;
import org.hibernate.event.spi.SaveOrUpdateEvent;
import org.hibernate.event.spi.SaveOrUpdateEventListener;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.graph.internal.RootGraphImpl;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.internal.util.ExceptionHelper;
import org.hibernate.jpa.QueryHints;
import org.hibernate.jpa.internal.util.CacheModeHelper;
import org.hibernate.jpa.internal.util.ConfigurationHelper;
import org.hibernate.jpa.internal.util.FlushModeTypeHelper;
import org.hibernate.jpa.internal.util.LockModeTypeHelper;
import org.hibernate.jpa.internal.util.LockOptionsHelper;
import org.hibernate.loader.access.IdentifierLoadAccessImpl;
import org.hibernate.loader.access.LoadAccessContext;
import org.hibernate.loader.access.NaturalIdLoadAccessImpl;
import org.hibernate.loader.access.SimpleNaturalIdLoadAccessImpl;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.pretty.MessageHelper;
import org.hibernate.procedure.ProcedureCall;
import org.hibernate.procedure.spi.NamedCallableQueryMemento;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.query.Query;
import org.hibernate.query.UnknownSqlResultSetMappingException;
import org.hibernate.resource.transaction.TransactionRequiredForJoinException;
import org.hibernate.resource.transaction.backend.jta.internal.JtaTransactionCoordinatorImpl;
import org.hibernate.resource.transaction.spi.TransactionCoordinator;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.hibernate.stat.SessionStatistics;
import org.hibernate.stat.internal.SessionStatisticsImpl;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.hibernate.tuple.TenantIdBinder;

import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TransactionRequiredException;

import static org.hibernate.cfg.AvailableSettings.JAKARTA_LOCK_SCOPE;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_LOCK_TIMEOUT;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_SHARED_CACHE_RETRIEVE_MODE;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_SHARED_CACHE_STORE_MODE;
import static org.hibernate.cfg.AvailableSettings.JPA_LOCK_SCOPE;
import static org.hibernate.cfg.AvailableSettings.JPA_LOCK_TIMEOUT;
import static org.hibernate.cfg.AvailableSettings.JPA_SHARED_CACHE_RETRIEVE_MODE;
import static org.hibernate.cfg.AvailableSettings.JPA_SHARED_CACHE_STORE_MODE;

/**
 * Concrete implementation of a the {@link Session} API.
 * <p/>
 * Exposes two interfaces:<ul>
 * <li>{@link Session} to the application</li>
 * <li>{@link SessionImplementor} to other Hibernate components (SPI)</li>
 * </ul>
 * <p/>
 * This class is not thread-safe.
 *
 * @author Gavin King
 * @author Steve Ebersole
 * @author Brett Meyer
 * @author Chris Cranford
 * @author Sanne Grinovero
 */
public class SessionImpl
		extends AbstractSessionImpl
		implements SessionImplementor, LoadAccessContext, EventSource {
	private static final EntityManagerMessageLogger log = HEMLogging.messageLogger( SessionImpl.class );

	// Defaults to null which means the properties are the default - as defined in FastSessionServices#defaultSessionProperties
	private Map<String, Object> properties;

	private transient ActionQueue actionQueue;
	private transient StatefulPersistenceContext persistenceContext;

	private transient LoadQueryInfluencers loadQueryInfluencers;

	private LockOptions lockOptions;

	private boolean autoClear;
	private boolean autoClose;
	private boolean queryParametersValidationEnabled;

	private transient int dontFlushFromFind;

	private transient LoadEvent loadEvent; //cached LoadEvent instance

	private transient TransactionObserver transactionObserver;

	private transient boolean isEnforcingFetchGraph;

	public SessionImpl(SessionFactoryImpl factory, SessionCreationOptions options) {
		super( factory, options );

		this.persistenceContext = createPersistenceContext();
		this.actionQueue = createActionQueue();

		this.autoClear = options.shouldAutoClear();
		this.autoClose = options.shouldAutoClose();
		this.queryParametersValidationEnabled = options.isQueryParametersValidationEnabled();

		if ( options instanceof SharedSessionCreationOptions ) {
			final SharedSessionCreationOptions sharedOptions = (SharedSessionCreationOptions) options;
			final ActionQueue.TransactionCompletionProcesses transactionCompletionProcesses = sharedOptions.getTransactionCompletionProcesses();
			if ( sharedOptions.isTransactionCoordinatorShared() && transactionCompletionProcesses != null ) {
				actionQueue.setTransactionCompletionProcesses(
						transactionCompletionProcesses,
						true
				);
			}
		}

		loadQueryInfluencers = new LoadQueryInfluencers( factory );

		final StatisticsImplementor statistics = factory.getStatistics();
		if ( statistics.isStatisticsEnabled() ) {
			statistics.openSession();
		}

		if ( this.properties != null ) {
			//There might be custom properties for this session that affect the LockOptions state
			LockOptionsHelper.applyPropertiesToLockOptions( this.properties, this::getLockOptionsForWrite );
		}
		setCacheMode( fastSessionServices.initialSessionCacheMode );

		// NOTE : pulse() already handles auto-join-ability correctly
		getTransactionCoordinator().pulse();

		// do not override explicitly set flush mode ( SessionBuilder#flushMode() )
		if ( getHibernateFlushMode() == null ) {
			final FlushMode initialMode;
			if ( this.properties == null ) {
				initialMode = fastSessionServices.initialSessionFlushMode;
			}
			else {
				initialMode = ConfigurationHelper.getFlushMode( getSessionProperty( AvailableSettings.FLUSH_MODE ), FlushMode.AUTO );
			}
			setHibernateFlushMode( initialMode );
		}

		if ( factory.getDefinedFilterNames().contains( TenantIdBinder.FILTER_NAME ) ) {
			String tenantIdentifier = getTenantIdentifier();
			if ( tenantIdentifier == null ) {
				throw new HibernateException( "SessionFactory configured for multi-tenancy, but no tenant identifier specified" );
			}
			else {
				CurrentTenantIdentifierResolver resolver = factory.getCurrentTenantIdentifierResolver();
				if ( resolver==null || !resolver.isRoot(tenantIdentifier) ) {
					// turn on the filter, unless this is the "root" tenant with access to all partitions
					getLoadQueryInfluencers()
							.enableFilter( TenantIdBinder.FILTER_NAME )
							.setParameter( TenantIdBinder.PARAMETER_NAME, tenantIdentifier );
				}
			}
		}

		if ( log.isTraceEnabled() ) {
			log.tracef( "Opened Session [%s] at timestamp: %s", getSessionIdentifier(), getTimestamp() );
		}
	}

	protected StatefulPersistenceContext createPersistenceContext() {
		return new StatefulPersistenceContext( this );
	}

	protected ActionQueue createActionQueue() {
		return new ActionQueue( this );
	}

	private LockOptions getLockOptionsForRead() {
		return this.lockOptions == null ? fastSessionServices.defaultLockOptions : this.lockOptions;
	}

	private LockOptions getLockOptionsForWrite() {
		if ( this.lockOptions == null ) {
			this.lockOptions = new LockOptions();
		}
		return this.lockOptions;
	}

	protected void applyQuerySettingsAndHints(Query<?> query) {
		final LockOptions lockOptionsForRead = getLockOptionsForRead();
		if ( lockOptionsForRead.getLockMode() != LockMode.NONE ) {
			query.setLockMode( getLockMode( lockOptionsForRead.getLockMode() ) );
		}
		final Object queryTimeout;
		if ( ( queryTimeout = getSessionProperty( QueryHints.SPEC_HINT_TIMEOUT )  ) != null ) {
			query.setHint( QueryHints.SPEC_HINT_TIMEOUT, queryTimeout );
		}
		final Object jakartaQueryTimeout;
		if ( ( jakartaQueryTimeout = getSessionProperty( QueryHints.JAKARTA_SPEC_HINT_TIMEOUT )  ) != null ) {
			query.setHint( QueryHints.JAKARTA_SPEC_HINT_TIMEOUT, jakartaQueryTimeout );
		}
		final Object lockTimeout;
		final Object jpaLockTimeout = getSessionProperty( JPA_LOCK_TIMEOUT );
		if ( jpaLockTimeout == null ) {
			lockTimeout = getSessionProperty( JAKARTA_LOCK_TIMEOUT );
		}
		else if ( Integer.valueOf( LockOptions.WAIT_FOREVER ).equals( jpaLockTimeout ) ) {
			final Object jakartaLockTimeout = getSessionProperty( JAKARTA_LOCK_TIMEOUT );
			if ( jakartaLockTimeout == null ) {
				lockTimeout = jpaLockTimeout;
			}
			else {
				lockTimeout = jakartaLockTimeout;
			}
		}
		else {
			lockTimeout = jpaLockTimeout;
		}
		if ( lockTimeout != null ) {
			query.setHint( JPA_LOCK_TIMEOUT, lockTimeout );
		}
	}

	private Object getSessionProperty(final String name) {
		if ( properties == null ) {
			return fastSessionServices.defaultSessionProperties.get( name );
		}
		else {
			return properties.get( name );
		}
	}

	@Override
	public SharedSessionBuilder sessionWithOptions() {
		return new SharedSessionBuilderImpl( this );
	}

	@Override
	public void clear() {
		checkOpen();

		// Do not call checkTransactionSynchStatus() here -- if a delayed
		// afterCompletion exists, it can cause an infinite loop.
		pulseTransactionCoordinator();

		try {
			internalClear();
		}
		catch (RuntimeException e) {
			throw getExceptionConverter().convert( e );
		}
	}

	private void internalClear() {
		persistenceContext.clear();
		actionQueue.clear();

		fastSessionServices.eventListenerGroup_CLEAR.fireLazyEventOnEachListener( this::createClearEvent, ClearEventListener::onClear );
	}

	private ClearEvent createClearEvent() {
		return new ClearEvent( this );
	}

	@Override
	@SuppressWarnings("StatementWithEmptyBody")
	public void close() throws HibernateException {
		if ( isClosed() ) {
			if ( getFactory().getSessionFactoryOptions().getJpaCompliance().isJpaClosedComplianceEnabled() ) {
				throw new IllegalStateException( "Illegal call to #close() on already closed Session/EntityManager" );
			}

			log.trace( "Already closed" );
			return;
		}

		closeWithoutOpenChecks();
	}

	public void closeWithoutOpenChecks() throws HibernateException {
		if ( log.isTraceEnabled() ) {
			log.tracef( "Closing session [%s]", getSessionIdentifier() );
		}

		// todo : we want this check if usage is JPA, but not native Hibernate usage
		final SessionFactoryImplementor sessionFactory = getSessionFactory();
		if ( sessionFactory.getSessionFactoryOptions().isJpaBootstrap() ) {
			// Original hibernate-entitymanager EM#close behavior
			checkSessionFactoryOpen();
			checkOpenOrWaitingForAutoClose();
			if ( fastSessionServices.discardOnClose || !isTransactionInProgress( false ) ) {
				super.close();
			}
			else {
				//Otherwise, session auto-close will be enabled by shouldAutoCloseSession().
				prepareForAutoClose();
			}
		}
		else {
			super.close();
		}

		final StatisticsImplementor statistics = sessionFactory.getStatistics();
		if ( statistics.isStatisticsEnabled() ) {
			statistics.closeSession();
		}
	}

	private boolean isTransactionInProgress(boolean isMarkedRollbackConsideredActive) {
		if ( waitingForAutoClose ) {
			return getSessionFactory().isOpen() &&
					getTransactionCoordinator().isTransactionActive( isMarkedRollbackConsideredActive );
		}
		return !isClosed() &&
				getTransactionCoordinator().isTransactionActive( isMarkedRollbackConsideredActive );
	}

	@Override
	protected boolean shouldCloseJdbcCoordinatorOnClose(boolean isTransactionCoordinatorShared) {
		if ( !isTransactionCoordinatorShared ) {
			return super.shouldCloseJdbcCoordinatorOnClose( isTransactionCoordinatorShared );
		}

		final ActionQueue actionQueue = getActionQueue();
		if ( actionQueue.hasBeforeTransactionActions() || actionQueue.hasAfterTransactionActions() ) {
			log.warn(
					"On close, shared Session had before/after transaction actions that have not yet been processed"
			);
		}
		return false;
	}

	@Override
	public boolean isAutoCloseSessionEnabled() {
		return autoClose;
	}

	@Override
	public boolean isQueryParametersValidationEnabled() {
		return queryParametersValidationEnabled;
	}

	@Override
	public boolean isOpen() {
		checkSessionFactoryOpen();
		checkTransactionSynchStatus();
		try {
			return !isClosed();
		}
		catch (HibernateException he) {
			throw getExceptionConverter().convert( he );
		}
	}

	protected void checkSessionFactoryOpen() {
		if ( !getFactory().isOpen() ) {
			log.debug( "Forcing Session/EntityManager closed as SessionFactory/EntityManagerFactory has been closed" );
			setClosed();
		}
	}

	private void managedFlush() {
		if ( isClosed() && !waitingForAutoClose ) {
			log.trace( "Skipping auto-flush due to session closed" );
			return;
		}
		log.trace( "Automatically flushing session" );
		doFlush();
	}

	@Override
	public boolean shouldAutoClose() {
		if ( waitingForAutoClose ) {
			return true;
		}
		else if ( isClosed() ) {
			return false;
		}
		else {
			// JPA technically requires that this be a PersistentUnityTransactionType#JTA to work,
			// but we do not assert that here...
			//return isAutoCloseSessionEnabled() && getTransactionCoordinator().getTransactionCoordinatorBuilder().isJta();
			return isAutoCloseSessionEnabled();
		}
	}

	private void managedClose() {
		log.trace( "Automatically closing session" );
		closeWithoutOpenChecks();
	}

	@Override
	public void setAutoClear(boolean enabled) {
		checkOpenOrWaitingForAutoClose();
		autoClear = enabled;
	}

	public void afterOperation(boolean success) {
		if ( !isTransactionInProgress() ) {
			getJdbcCoordinator().afterTransaction();
		}
	}

	@Override
	public void addEventListeners(SessionEventListener... listeners) {
		getEventListenerManager().addListener( listeners );
	}

	/**
	 * clear all the internal collections, just
	 * to help the garbage collector, does not
	 * clear anything that is needed during the
	 * afterTransactionCompletion() phase
	 */
	@Override
	protected void cleanupOnClose() {
		persistenceContext.clear();
	}

	@Override
	public LockMode getCurrentLockMode(Object object) throws HibernateException {
		checkOpen();
		checkTransactionSynchStatus();
		if ( object == null ) {
			throw new NullPointerException( "null object passed to getCurrentLockMode()" );
		}

		if ( object instanceof HibernateProxy ) {
			object = ( (HibernateProxy) object ).getHibernateLazyInitializer().getImplementation( this );
			if ( object == null ) {
				return LockMode.NONE;
			}
		}

		final EntityEntry e = persistenceContext.getEntry( object );
		if ( e == null ) {
			throw new TransientObjectException( "Given object not associated with the session" );
		}

		if ( e.getStatus() != Status.MANAGED ) {
			throw new ObjectDeletedException(
					"The given object was deleted",
					e.getId(),
					e.getPersister().getEntityName()
			);
		}

		return e.getLockMode();
	}

	@Override
	public Object getEntityUsingInterceptor(EntityKey key) throws HibernateException {
		checkOpenOrWaitingForAutoClose();
		// todo : should this get moved to PersistentContext?
		// logically, is PersistentContext the "thing" to which an interceptor gets attached?
		final Object result = persistenceContext.getEntity( key );
		if ( result == null ) {
			final Object newObject = getInterceptor().getEntity( key.getEntityName(), key.getIdentifier() );
			if ( newObject != null ) {
				lock( newObject, LockMode.NONE );
			}
			return newObject;
		}
		else {
			return result;
		}
	}

	protected void checkNoUnresolvedActionsBeforeOperation() {
		if ( persistenceContext.getCascadeLevel() == 0 && actionQueue.hasUnresolvedEntityInsertActions() ) {
			throw new IllegalStateException( "There are delayed insert actions before operation as cascade level 0." );
		}
	}

	protected void checkNoUnresolvedActionsAfterOperation() {
		if ( persistenceContext.getCascadeLevel() == 0 ) {
			actionQueue.checkNoUnresolvedActionsAfterOperation();
		}
		delayedAfterCompletion();
	}

	@Override
	public void delayedAfterCompletion() {
		if ( getTransactionCoordinator() instanceof JtaTransactionCoordinatorImpl ) {
			( (JtaTransactionCoordinatorImpl) getTransactionCoordinator() ).getSynchronizationCallbackCoordinator()
					.processAnyDelayedAfterCompletion();
		}
	}

	@Override
	public void pulseTransactionCoordinator() {
		super.pulseTransactionCoordinator();
	}

	@Override
	public void checkOpenOrWaitingForAutoClose() {
		super.checkOpenOrWaitingForAutoClose();
	}

	// saveOrUpdate() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void saveOrUpdate(Object object) throws HibernateException {
		saveOrUpdate( null, object );
	}

	@Override
	public void saveOrUpdate(String entityName, Object obj) throws HibernateException {
		fireSaveOrUpdate( new SaveOrUpdateEvent( entityName, obj, this ) );
	}

	private void fireSaveOrUpdate(final SaveOrUpdateEvent event) {
		checkOpen();
		checkTransactionSynchStatus();
		checkNoUnresolvedActionsBeforeOperation();
		fastSessionServices.eventListenerGroup_SAVE_UPDATE.fireEventOnEachListener( event, SaveOrUpdateEventListener::onSaveOrUpdate );
		checkNoUnresolvedActionsAfterOperation();
	}

	// save() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public Object save(Object obj) throws HibernateException {
		return save( null, obj );
	}

	@Override
	public Object save(String entityName, Object object) throws HibernateException {
		return fireSave( new SaveOrUpdateEvent( entityName, object, this ) );
	}

	private Object fireSave(final SaveOrUpdateEvent event) {
		checkOpen();
		checkTransactionSynchStatus();
		checkNoUnresolvedActionsBeforeOperation();
		fastSessionServices.eventListenerGroup_SAVE.fireEventOnEachListener( event, SaveOrUpdateEventListener::onSaveOrUpdate );
		checkNoUnresolvedActionsAfterOperation();
		return event.getResultId();
	}


	// update() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void update(Object obj) throws HibernateException {
		update( null, obj );
	}

	@Override
	public void update(String entityName, Object object) throws HibernateException {
		fireUpdate( new SaveOrUpdateEvent( entityName, object, this ) );
	}

	private void fireUpdate(SaveOrUpdateEvent event) {
		checkOpen();
		checkTransactionSynchStatus();
		checkNoUnresolvedActionsBeforeOperation();
		fastSessionServices.eventListenerGroup_UPDATE.fireEventOnEachListener( event, SaveOrUpdateEventListener::onSaveOrUpdate );
		checkNoUnresolvedActionsAfterOperation();
	}


	// lock() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void lock(String entityName, Object object, LockMode lockMode) throws HibernateException {
		fireLock( new LockEvent( entityName, object, lockMode, this ) );
	}

	@Override
	public LockRequest buildLockRequest(LockOptions lockOptions) {
		return new LockRequestImpl( lockOptions );
	}

	@Override
	public void lock(Object object, LockMode lockMode) throws HibernateException {
		fireLock( new LockEvent( object, lockMode, this ) );
	}

	private void fireLock(String entityName, Object object, LockOptions options) {
		fireLock( new LockEvent( entityName, object, options, this ) );
	}

	private void fireLock(Object object, LockOptions options) {
		fireLock( new LockEvent( object, options, this ) );
	}

	private void fireLock(LockEvent event) {
		checkOpen();
		pulseTransactionCoordinator();
		fastSessionServices.eventListenerGroup_LOCK.fireEventOnEachListener( event, LockEventListener::onLock );
		delayedAfterCompletion();
	}

	// persist() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void persist(String entityName, Object object) throws HibernateException {
		checkOpen();
		firePersist( new PersistEvent( entityName, object, this ) );
	}

	@Override
	public void persist(Object object) throws HibernateException {
		checkOpen();
		firePersist( new PersistEvent( null, object, this ) );
	}

	@Override
	public void persist(String entityName, Object object, Map copiedAlready) throws HibernateException {
		checkOpenOrWaitingForAutoClose();
		firePersist( copiedAlready, new PersistEvent( entityName, object, this ) );
	}

	private void firePersist(final PersistEvent event) {
		Throwable originalException = null;
		try {
			checkTransactionSynchStatus();
			checkNoUnresolvedActionsBeforeOperation();

			fastSessionServices.eventListenerGroup_PERSIST.fireEventOnEachListener( event, PersistEventListener::onPersist );
		}
		catch (MappingException e) {
			originalException = getExceptionConverter().convert( new IllegalArgumentException( e.getMessage() ) );
		}
		catch (RuntimeException e) {
			originalException = getExceptionConverter().convert( e );
		}
		catch (Throwable t1) {
			originalException = t1;
		}
		finally {
			Throwable suppressed = null;
			try {
				checkNoUnresolvedActionsAfterOperation();
			}
			catch (RuntimeException e) {
				suppressed = getExceptionConverter().convert( e );
			}
			catch (Throwable t2) {
				suppressed = t2;
			}
			if ( suppressed != null ) {
				if ( originalException == null ) {
					originalException = suppressed;
				}
				else {
					originalException.addSuppressed( suppressed );
				}
			}
		}
		if ( originalException != null ) {
			ExceptionHelper.doThrow( originalException );
		}
	}

	private void firePersist(final Map copiedAlready, final PersistEvent event) {
		pulseTransactionCoordinator();

		try {
			//Uses a capturing lambda in this case as we need to carry the additional Map parameter:
			fastSessionServices.eventListenerGroup_PERSIST
					.fireEventOnEachListener( event, copiedAlready, PersistEventListener::onPersist );
		}
		catch ( MappingException e ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( e.getMessage() ) ) ;
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e );
		}
		finally {
			delayedAfterCompletion();
		}
	}


	// persistOnFlush() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void persistOnFlush(String entityName, Object object, Map copiedAlready) {
		checkOpenOrWaitingForAutoClose();
		pulseTransactionCoordinator();
		PersistEvent event = new PersistEvent( entityName, object, this );
		fastSessionServices.eventListenerGroup_PERSIST_ONFLUSH.fireEventOnEachListener( event, copiedAlready, PersistEventListener::onPersist );
		delayedAfterCompletion();
	}

	// merge() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public <T> T merge(String entityName, T object) throws HibernateException {
		checkOpen();
		return (T) fireMerge( new MergeEvent( entityName, object, this ) );
	}

	@Override
	public <T> T merge(T object) throws HibernateException {
		checkOpen();
		return (T) fireMerge( new MergeEvent( null, object, this ));
	}

	@Override
	public void merge(String entityName, Object object, Map copiedAlready) throws HibernateException {
		checkOpenOrWaitingForAutoClose();
		fireMerge( copiedAlready, new MergeEvent( entityName, object, this ) );
	}

	private Object fireMerge(MergeEvent event) {
		try {
			checkTransactionSynchStatus();
			checkNoUnresolvedActionsBeforeOperation();
			fastSessionServices.eventListenerGroup_MERGE.fireEventOnEachListener( event, MergeEventListener::onMerge );
			checkNoUnresolvedActionsAfterOperation();
		}
		catch ( ObjectDeletedException sse ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( sse ) );
		}
		catch ( MappingException e ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( e.getMessage(), e ) );
		}
		catch ( RuntimeException e ) {
			//including HibernateException
			throw getExceptionConverter().convert( e );
		}

		return event.getResult();
	}

	private void fireMerge(final Map copiedAlready, final MergeEvent event) {
		try {
			pulseTransactionCoordinator();
			fastSessionServices.eventListenerGroup_MERGE.fireEventOnEachListener( event, copiedAlready, MergeEventListener::onMerge );
		}
		catch ( ObjectDeletedException sse ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( sse ) );
		}
		catch ( MappingException e ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( e.getMessage(), e ) );
		}
		catch ( RuntimeException e ) {
			//including HibernateException
			throw getExceptionConverter().convert( e );
		}
		finally {
			delayedAfterCompletion();
		}
	}


	// delete() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void delete(Object object) throws HibernateException {
		checkOpen();
		fireDelete( new DeleteEvent( object, this ) );
	}

	@Override
	public void delete(String entityName, Object object) throws HibernateException {
		checkOpen();
		fireDelete( new DeleteEvent( entityName, object, this ) );
	}

	@Override
	public void delete(String entityName, Object object, boolean isCascadeDeleteEnabled, Set transientEntities)
			throws HibernateException {
		checkOpenOrWaitingForAutoClose();
		final boolean removingOrphanBeforeUpates = persistenceContext.isRemovingOrphanBeforeUpates();
		final boolean traceEnabled = log.isTraceEnabled();
		if ( traceEnabled && removingOrphanBeforeUpates ) {
			logRemoveOrphanBeforeUpdates( "before continuing", entityName, object );
		}
		fireDelete(
				new DeleteEvent(
						entityName,
						object,
						isCascadeDeleteEnabled,
						removingOrphanBeforeUpates,
						this
				),
				transientEntities
		);
		if ( traceEnabled && removingOrphanBeforeUpates ) {
			logRemoveOrphanBeforeUpdates( "after continuing", entityName, object );
		}
	}

	@Override
	public void removeOrphanBeforeUpdates(String entityName, Object child) {
		// TODO: The removeOrphan concept is a temporary "hack" for HHH-6484.  This should be removed once action/task
		// ordering is improved.
		final boolean traceEnabled = log.isTraceEnabled();
		if ( traceEnabled ) {
			logRemoveOrphanBeforeUpdates( "begin", entityName, child );
		}
		persistenceContext.beginRemoveOrphanBeforeUpdates();
		try {
			checkOpenOrWaitingForAutoClose();
			fireDelete( new DeleteEvent( entityName, child, false, true, this ) );
		}
		finally {
			persistenceContext.endRemoveOrphanBeforeUpdates();
			if ( traceEnabled ) {
				logRemoveOrphanBeforeUpdates( "end", entityName, child );
			}
		}
	}

	private void logRemoveOrphanBeforeUpdates(String timing, String entityName, Object entity) {
		if ( log.isTraceEnabled() ) {
			final EntityEntry entityEntry = persistenceContext.getEntry( entity );
			log.tracef(
					"%s remove orphan before updates: [%s]",
					timing,
					entityEntry == null ? entityName : MessageHelper.infoString( entityName, entityEntry.getId() )
			);
		}
	}

	private void fireDelete(final DeleteEvent event) {
		try{
			pulseTransactionCoordinator();
			fastSessionServices.eventListenerGroup_DELETE.fireEventOnEachListener( event, DeleteEventListener::onDelete );
		}
		catch ( ObjectDeletedException sse ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( sse ) );
		}
		catch ( MappingException e ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( e.getMessage(), e ) );
		}
		catch ( RuntimeException e ) {
			//including HibernateException
			throw getExceptionConverter().convert( e );
		}
		finally {
			delayedAfterCompletion();
		}
	}

	private void fireDelete(final DeleteEvent event, final Set transientEntities) {
		try{
			pulseTransactionCoordinator();
			fastSessionServices.eventListenerGroup_DELETE.fireEventOnEachListener( event, transientEntities, DeleteEventListener::onDelete );
		}
		catch ( ObjectDeletedException sse ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( sse ) );
		}
		catch ( MappingException e ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( e.getMessage(), e ) );
		}
		catch ( RuntimeException e ) {
			//including HibernateException
			throw getExceptionConverter().convert( e );
		}
		finally {
			delayedAfterCompletion();
		}
	}


	// load()/get() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void load(Object object, Object id) throws HibernateException {
		LoadEvent event = loadEvent;
		loadEvent = null;
		if ( event == null ) {
			event = new LoadEvent( id, object, this, getReadOnlyFromLoadQueryInfluencers() );
		}
		else {
			event.setEntityClassName( null );
			event.setEntityId( id );
			event.setInstanceToLoad( object );
			event.setLockMode( LoadEvent.DEFAULT_LOCK_MODE );
			event.setLockScope( LoadEvent.DEFAULT_LOCK_OPTIONS.getScope() );
			event.setLockTimeout( LoadEvent.DEFAULT_LOCK_OPTIONS.getTimeOut() );
		}

		fireLoad( event, LoadEventListener.RELOAD );

		if ( loadEvent == null ) {
			event.setEntityClassName( null );
			event.setEntityId( null );
			event.setInstanceToLoad( null );
			event.setResult( null );
			loadEvent = event;
		}
	}

	@Override
	public <T> T load(Class<T> entityClass, Object id) throws HibernateException {
		return this.byId( entityClass ).getReference( id );
	}

	@Override
	public Object load(String entityName, Object id) throws HibernateException {
		return this.byId( entityName ).getReference( id );
	}

	@Override
	public <T> T get(Class<T> entityClass, Object id) throws HibernateException {
		return this.byId( entityClass ).load( id );
	}

	@Override
	public Object get(String entityName, Object id) throws HibernateException {
		return this.byId( entityName ).load( id );
	}

	/**
	 * Load the data for the object with the specified id into a newly created object.
	 * This is only called when lazily initializing a proxy.
	 * Do NOT return a proxy.
	 */
	@Override
	public Object immediateLoad(String entityName, Object id) throws HibernateException {
		if ( log.isDebugEnabled() ) {
			EntityPersister persister = getFactory().getMetamodel().entityPersister( entityName );
			log.debugf( "Initializing proxy: %s", MessageHelper.infoString( persister, id, getFactory() ) );
		}
		LoadEvent event = loadEvent;
		loadEvent = null;
		event = recycleEventInstance( event, id, entityName );
		fireLoadNoChecks( event, LoadEventListener.IMMEDIATE_LOAD );
		Object result = event.getResult();
		if ( loadEvent == null ) {
			event.setEntityClassName( null );
			event.setEntityId( null );
			event.setInstanceToLoad( null );
			event.setResult( null );
			loadEvent = event;
		}
		if ( result instanceof HibernateProxy ) {
			return ( (HibernateProxy) result ).getHibernateLazyInitializer().getImplementation();
		}
		return result;
	}

	@Override
	public Object internalLoad(
			String entityName,
			Object id,
			boolean eager,
			boolean nullable) {
		final EffectiveEntityGraph effectiveEntityGraph = getLoadQueryInfluencers().getEffectiveEntityGraph();
		final GraphSemantic semantic = effectiveEntityGraph.getSemantic();
		final RootGraphImplementor<?> graph = effectiveEntityGraph.getGraph();
		boolean clearedEffectiveGraph = false;
		if ( semantic != null ) {
			if ( ! graph.appliesTo( entityName ) ) {
				log.debug( "Clearing effective entity graph for subsequent-select" );
				clearedEffectiveGraph = true;
				effectiveEntityGraph.clear();
			}
		}

		try {
			final LoadType type;
			if ( nullable ) {
				type = LoadEventListener.INTERNAL_LOAD_NULLABLE;
			}
			else {
				type = eager
						? LoadEventListener.INTERNAL_LOAD_EAGER
						: LoadEventListener.INTERNAL_LOAD_LAZY;
			}

			LoadEvent event = loadEvent;
			loadEvent = null;

			event = recycleEventInstance( event, id, entityName );

			fireLoadNoChecks( event, type );

			Object result = event.getResult();

			if ( !nullable ) {
				UnresolvableObjectException.throwIfNull( result, id, entityName );
			}

			if ( loadEvent == null ) {
				event.setEntityClassName( null );
				event.setEntityId( null );
				event.setInstanceToLoad( null );
				event.setResult( null );
				loadEvent = event;
			}
			return result;
		}
		finally {
			if ( clearedEffectiveGraph ) {
				effectiveEntityGraph.applyGraph( graph, semantic );
			}
		}
	}

	/**
	 * Helper to avoid creating many new instances of LoadEvent: it's an allocation hot spot.
	 */
	private LoadEvent recycleEventInstance(final LoadEvent event, final Object id, final String entityName) {
		if ( event == null ) {
			return new LoadEvent( id, entityName, true, this, getReadOnlyFromLoadQueryInfluencers() );
		}
		else {
			event.setEntityClassName( entityName );
			event.setEntityId( id );
			event.setInstanceToLoad( null );
			event.setLockMode( LoadEvent.DEFAULT_LOCK_MODE );
			event.setLockScope( LoadEvent.DEFAULT_LOCK_OPTIONS.getScope() );
			event.setLockTimeout( LoadEvent.DEFAULT_LOCK_OPTIONS.getTimeOut() );
			return event;
		}
	}

	@Override
	public <T> T load(Class<T> entityClass, Object id, LockMode lockMode) throws HibernateException {
		return this.byId( entityClass ).with( new LockOptions( lockMode ) ).getReference( id );
	}

	@Override
	public <T> T load(Class<T> entityClass, Object id, LockOptions lockOptions) throws HibernateException {
		return this.byId( entityClass ).with( lockOptions ).getReference( id );
	}

	@Override
	public Object load(String entityName, Object id, LockMode lockMode) throws HibernateException {
		return this.byId( entityName ).with( new LockOptions( lockMode ) ).getReference( id );
	}

	@Override
	public Object load(String entityName, Object id, LockOptions lockOptions) throws HibernateException {
		return this.byId( entityName ).with( lockOptions ).getReference( id );
	}

	@Override
	public <T> T get(Class<T> entityClass, Object id, LockMode lockMode) throws HibernateException {
		return this.byId( entityClass ).with( new LockOptions( lockMode ) ).load( id );
	}

	@Override
	public <T> T get(Class<T> entityClass, Object id, LockOptions lockOptions) throws HibernateException {
		return this.byId( entityClass ).with( lockOptions ).load( id );
	}

	@Override
	public Object get(String entityName, Object id, LockMode lockMode) throws HibernateException {
		return this.byId( entityName ).with( new LockOptions( lockMode ) ).load( id );
	}

	@Override
	public Object get(String entityName, Object id, LockOptions lockOptions) throws HibernateException {
		return this.byId( entityName ).with( lockOptions ).load( id );
	}

	@Override
	public <T> IdentifierLoadAccessImpl<T> byId(String entityName) {
		return new IdentifierLoadAccessImpl<>( this, requireEntityPersister( entityName ) );
	}

	@Override
	public <T> IdentifierLoadAccessImpl<T> byId(Class<T> entityClass) {
		return new IdentifierLoadAccessImpl<>( this, requireEntityPersister( entityClass ) );
	}

	@Override
	public <T> MultiIdentifierLoadAccess<T> byMultipleIds(Class<T> entityClass) {
		return new MultiIdentifierLoadAccessImpl<>( this, requireEntityPersister( entityClass ) );
	}

	@Override
	public <T> MultiIdentifierLoadAccess<T> byMultipleIds(String entityName) {
		return new MultiIdentifierLoadAccessImpl<>( this, requireEntityPersister( entityName ) );
	}

	@Override
	public <T> NaturalIdLoadAccess<T> byNaturalId(String entityName) {
		return new NaturalIdLoadAccessImpl<>( this, requireEntityPersister( entityName ) );
	}

	@Override
	public <T> NaturalIdLoadAccess<T> byNaturalId(Class<T> entityClass) {
		return new NaturalIdLoadAccessImpl<>( this, requireEntityPersister( entityClass ) );
	}

	@Override
	public <T> SimpleNaturalIdLoadAccess<T> bySimpleNaturalId(String entityName) {
		return new SimpleNaturalIdLoadAccessImpl<>( this, requireEntityPersister( entityName ) );
	}

	@Override
	public <T> SimpleNaturalIdLoadAccess<T> bySimpleNaturalId(Class<T> entityClass) {
		return new SimpleNaturalIdLoadAccessImpl<>( this, requireEntityPersister( entityClass ) );
	}

	@Override
	public <T> NaturalIdMultiLoadAccess<T> byMultipleNaturalId(Class<T> entityClass) {
		return new NaturalIdMultiLoadAccessStandard<>( requireEntityPersister( entityClass ), this );
	}

	@Override
	public <T> NaturalIdMultiLoadAccess<T> byMultipleNaturalId(String entityName) {
		return new NaturalIdMultiLoadAccessStandard<>( requireEntityPersister( entityName ), this );
	}

	@Override
	public void fireLoad(LoadEvent event, LoadType loadType) {
		checkOpenOrWaitingForAutoClose();
		fireLoadNoChecks( event, loadType );
		delayedAfterCompletion();
	}

	//Performance note:
	// This version of #fireLoad is meant to be invoked by internal methods only,
	// so to skip the session open, transaction synch, etc.. checks,
	// which have been proven to be not particularly cheap:
	// it seems they prevent these hot methods from being inlined.
	private void fireLoadNoChecks(final LoadEvent event, final LoadType loadType) {
		pulseTransactionCoordinator();
		fastSessionServices.eventListenerGroup_LOAD.fireEventOnEachListener( event, loadType, LoadEventListener::onLoad );
	}

	private void fireResolveNaturalId(final ResolveNaturalIdEvent event) {
		checkOpenOrWaitingForAutoClose();
		pulseTransactionCoordinator();
		fastSessionServices.eventListenerGroup_RESOLVE_NATURAL_ID.fireEventOnEachListener( event, ResolveNaturalIdEventListener::onResolveNaturalId );
		delayedAfterCompletion();
	}


	// refresh() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void refresh(Object object) throws HibernateException {
		checkOpen();
		fireRefresh( new RefreshEvent( null, object, this ) );
	}

	@Override
	public void refresh(String entityName, Object object) throws HibernateException {
		checkOpen();
		fireRefresh( new RefreshEvent( entityName, object, this ) );
	}

	@Override
	public void refresh(Object object, LockMode lockMode) throws HibernateException {
		checkOpen();
		fireRefresh( new RefreshEvent( object, lockMode, this ) );
	}

	@Override
	public void refresh(Object object, LockOptions lockOptions) throws HibernateException {
		checkOpen();
		refresh( null, object, lockOptions );
	}

	@Override
	public void refresh(String entityName, Object object, LockOptions lockOptions) throws HibernateException {
		checkOpen();
		fireRefresh( new RefreshEvent( entityName, object, lockOptions, this ) );
	}

	@Override
	public void refresh(String entityName, Object object, Map refreshedAlready) throws HibernateException {
		checkOpenOrWaitingForAutoClose();
		fireRefresh( refreshedAlready, new RefreshEvent( entityName, object, this ) );
	}

	private void fireRefresh(final RefreshEvent event) {
		try {
			if ( !getSessionFactory().getSessionFactoryOptions().isAllowRefreshDetachedEntity() ) {
				if ( event.getEntityName() != null ) {
					if ( !contains( event.getEntityName(), event.getObject() ) ) {
						throw new IllegalArgumentException( "Entity not managed" );
					}
				}
				else {
					if ( !contains( event.getObject() ) ) {
						throw new IllegalArgumentException( "Entity not managed" );
					}
				}
			}
			pulseTransactionCoordinator();
			fastSessionServices.eventListenerGroup_REFRESH.fireEventOnEachListener( event, RefreshEventListener::onRefresh );
		}
		catch (RuntimeException e) {
			if ( !getSessionFactory().getSessionFactoryOptions().isJpaBootstrap() ) {
				if ( e instanceof HibernateException ) {
					throw e;
				}
			}
			//including HibernateException
			throw getExceptionConverter().convert( e );
		}
		finally {
			delayedAfterCompletion();
		}
	}

	private void fireRefresh(final Map refreshedAlready, final RefreshEvent event) {
		try {
			pulseTransactionCoordinator();
			fastSessionServices.eventListenerGroup_REFRESH.fireEventOnEachListener( event, refreshedAlready, RefreshEventListener::onRefresh );
		}
		catch (RuntimeException e) {
			throw getExceptionConverter().convert( e );
		}
		finally {
			delayedAfterCompletion();
		}
	}


	// replicate() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void replicate(Object obj, ReplicationMode replicationMode) throws HibernateException {
		fireReplicate( new ReplicateEvent( obj, replicationMode, this ) );
	}

	@Override
	public void replicate(String entityName, Object obj, ReplicationMode replicationMode)
			throws HibernateException {
		fireReplicate( new ReplicateEvent( entityName, obj, replicationMode, this ) );
	}

	private void fireReplicate(final ReplicateEvent event) {
		checkOpen();
		pulseTransactionCoordinator();
		fastSessionServices.eventListenerGroup_REPLICATE.fireEventOnEachListener( event, ReplicateEventListener::onReplicate );
		delayedAfterCompletion();
	}


	// evict() operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * remove any hard references to the entity that are held by the infrastructure
	 * (references held by application or other persistent instances are okay)
	 */
	@Override
	public void evict(Object object) throws HibernateException {
		checkOpen();
		pulseTransactionCoordinator();
		final EvictEvent event = new EvictEvent( object, this );
		fastSessionServices.eventListenerGroup_EVICT.fireEventOnEachListener( event, EvictEventListener::onEvict );
		delayedAfterCompletion();
	}

	@Override
	public boolean autoFlushIfRequired(Set<String> querySpaces) throws HibernateException {
		checkOpen();
		if ( !isTransactionInProgress() ) {
			// do not auto-flush while outside a transaction
			return false;
		}
		AutoFlushEvent event = new AutoFlushEvent( querySpaces, this );
		fastSessionServices.eventListenerGroup_AUTO_FLUSH.fireEventOnEachListener( event, AutoFlushEventListener::onAutoFlush );
		return event.isFlushRequired();
	}

	@Override
	public boolean isDirty() throws HibernateException {
		checkOpen();
		pulseTransactionCoordinator();
		log.debug( "Checking session dirtiness" );
		if ( actionQueue.areInsertionsOrDeletionsQueued() ) {
			log.debug( "Session dirty (scheduled updates and insertions)" );
			return true;
		}
		DirtyCheckEvent event = new DirtyCheckEvent( this );
		fastSessionServices.eventListenerGroup_DIRTY_CHECK.fireEventOnEachListener( event, DirtyCheckEventListener::onDirtyCheck );
		delayedAfterCompletion();
		return event.isDirty();
	}

	@Override
	public void flush() throws HibernateException {
		checkOpen();
		doFlush();
	}

	private void doFlush() {
		pulseTransactionCoordinator();
		checkTransactionNeededForUpdateOperation();

		try {
			if ( persistenceContext.getCascadeLevel() > 0 ) {
				throw new HibernateException( "Flush during cascade is dangerous" );
			}

			FlushEvent event = new FlushEvent( this );
			fastSessionServices.eventListenerGroup_FLUSH.fireEventOnEachListener( event, FlushEventListener::onFlush );
			delayedAfterCompletion();
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public void setFlushMode(FlushModeType flushModeType) {
		checkOpen();
		setHibernateFlushMode( FlushModeTypeHelper.getFlushMode( flushModeType ) );
	}

	@Override
	public void forceFlush(EntityEntry entityEntry) throws HibernateException {
		if ( log.isDebugEnabled() ) {
			log.debugf(
					"Flushing to force deletion of re-saved object: %s",
					MessageHelper.infoString( entityEntry.getPersister(), entityEntry.getId(), getFactory() )
			);
		}

		if ( persistenceContext.getCascadeLevel() > 0 ) {
			throw new ObjectDeletedException(
					"deleted object would be re-saved by cascade (remove deleted object from associations)",
					entityEntry.getId(),
					entityEntry.getPersister().getEntityName()
			);
		}
		checkOpenOrWaitingForAutoClose();
		doFlush();
	}

	@Override
	public Object instantiate(String entityName, Object id) throws HibernateException {
		return instantiate( getFactory().getMetamodel().entityPersister( entityName ), id );
	}

	/**
	 * give the interceptor an opportunity to override the default instantiation
	 */
	@Override
	public Object instantiate(EntityPersister persister, Object id) throws HibernateException {
		checkOpenOrWaitingForAutoClose();
		pulseTransactionCoordinator();
		Object result = getInterceptor().instantiate(
				persister.getEntityName(),
				persister.getRepresentationStrategy(),
				id
		);
		if ( result == null ) {
			result = persister.instantiate( id, this );
		}
		delayedAfterCompletion();
		return result;
	}

	@Override
	public EntityPersister getEntityPersister(final String entityName, final Object object) {
		checkOpenOrWaitingForAutoClose();
		if ( entityName == null ) {
			return getFactory().getMetamodel().entityPersister( guessEntityName( object ) );
		}
		else {
			// try block is a hack around fact that currently tuplizers are not
			// given the opportunity to resolve a subclass entity name.  this
			// allows the (we assume custom) interceptor the ability to
			// influence this decision if we were not able to based on the
			// given entityName
			try {
				return getFactory().getMetamodel().entityPersister( entityName ).getSubclassEntityPersister( object, getFactory() );
			}
			catch (HibernateException e) {
				try {
					return getEntityPersister( null, object );
				}
				catch (HibernateException e2) {
					throw e;
				}
			}
		}
	}

	// not for internal use:
	@Override
	public Object getIdentifier(Object object) throws HibernateException {
		checkOpen();
		checkTransactionSynchStatus();
		if ( object instanceof HibernateProxy ) {
			LazyInitializer li = ( (HibernateProxy) object ).getHibernateLazyInitializer();
			if ( li.getSession() != this ) {
				throw new TransientObjectException( "The proxy was not associated with this session" );
			}
			return li.getInternalIdentifier();
		}
		else {
			EntityEntry entry = persistenceContext.getEntry( object );
			if ( entry == null ) {
				throw new TransientObjectException( "The instance was not associated with this session" );
			}
			return entry.getId();
		}
	}

	/**
	 * Get the id value for an object that is actually associated with the session. This
	 * is a bit stricter than getEntityIdentifierIfNotUnsaved().
	 */
	@Override
	public Object getContextEntityIdentifier(Object object) {
		checkOpenOrWaitingForAutoClose();
		if ( object instanceof HibernateProxy ) {
			return getProxyIdentifier( object );
		}
		else {
			EntityEntry entry = persistenceContext.getEntry( object );
			return entry != null ? entry.getId() : null;
		}
	}

	private Object getProxyIdentifier(Object proxy) {
		return ( (HibernateProxy) proxy ).getHibernateLazyInitializer().getInternalIdentifier();
	}

	@Override
	public boolean contains(Object object) {
		checkOpen();
		pulseTransactionCoordinator();

		if ( object == null ) {
			return false;
		}

		try {
			if ( object instanceof HibernateProxy ) {
				//do not use proxiesByKey, since not all
				//proxies that point to this session's
				//instances are in that collection!
				LazyInitializer li = ( (HibernateProxy) object ).getHibernateLazyInitializer();
				if ( li.isUninitialized() ) {
					//if it is an uninitialized proxy, pointing
					//with this session, then when it is accessed,
					//the underlying instance will be "contained"
					return li.getSession() == this;
				}
				else {
					//if it is initialized, see if the underlying
					//instance is contained, since we need to
					//account for the fact that it might have been
					//evicted
					object = li.getImplementation();
				}
			}

			// A session is considered to contain an entity only if the entity has
			// an entry in the session's persistence context and the entry reports
			// that the entity has not been removed
			EntityEntry entry = persistenceContext.getEntry( object );
			delayedAfterCompletion();

			if ( entry == null ) {
				if ( !HibernateProxy.class.isInstance( object ) && persistenceContext.getEntry( object ) == null ) {
					// check if it is even an entity -> if not throw an exception (per JPA)
					try {
						final String entityName = getEntityNameResolver().resolveEntityName( object );
						if ( entityName == null ) {
							throw new IllegalArgumentException( "Could not resolve entity-name [" + object + "]" );
						}
						getSessionFactory().getMetamodel().entityPersister( entityName );
					}
					catch (HibernateException e) {
						throw new IllegalArgumentException( "Not an entity [" + object.getClass() + "]", e );
					}
				}
				return false;
			}
			else {
				return entry.getStatus() != Status.DELETED && entry.getStatus() != Status.GONE;
			}
		}
		catch (MappingException e) {
			throw new IllegalArgumentException( e.getMessage(), e );
		}
		catch (RuntimeException e) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public boolean contains(String entityName, Object object) {
		checkOpenOrWaitingForAutoClose();
		pulseTransactionCoordinator();

		if ( object == null ) {
			return false;
		}

		try {
			//noinspection RedundantClassCall
			if ( !HibernateProxy.class.isInstance( object ) && persistenceContext.getEntry( object ) == null ) {
				// check if it is an entity -> if not throw an exception (per JPA)
				try {
					getSessionFactory().getMetamodel().entityPersister( entityName );
				}
				catch (HibernateException e) {
					throw new IllegalArgumentException( "Not an entity [" + entityName + "] : " + object );
				}
			}

			if ( object instanceof HibernateProxy ) {
				//do not use proxiesByKey, since not all
				//proxies that point to this session's
				//instances are in that collection!
				LazyInitializer li = ( (HibernateProxy) object ).getHibernateLazyInitializer();
				if ( li.isUninitialized() ) {
					//if it is an uninitialized proxy, pointing
					//with this session, then when it is accessed,
					//the underlying instance will be "contained"
					return li.getSession() == this;
				}
				else {
					//if it is initialized, see if the underlying
					//instance is contained, since we need to
					//account for the fact that it might have been
					//evicted
					object = li.getImplementation();
				}
			}
			// A session is considered to contain an entity only if the entity has
			// an entry in the session's persistence context and the entry reports
			// that the entity has not been removed
			EntityEntry entry = persistenceContext.getEntry( object );
			delayedAfterCompletion();
			return entry != null && entry.getStatus() != Status.DELETED && entry.getStatus() != Status.GONE;
		}
		catch (MappingException e) {
			throw new IllegalArgumentException( e.getMessage(), e );
		}
		catch (RuntimeException e) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public ProcedureCall createStoredProcedureCall(String procedureName) {
		checkOpen();
//		checkTransactionSynchStatus();
		return super.createStoredProcedureCall( procedureName );
	}

	@Override
	public ProcedureCall createStoredProcedureCall(String procedureName, String... resultSetMappings) {
		checkOpen();
//		checkTransactionSynchStatus();
		return super.createStoredProcedureCall( procedureName, resultSetMappings );
	}

	@Override
	public ProcedureCall createStoredProcedureCall(String procedureName, Class<?>... resultClasses) {
		checkOpen();
//		checkTransactionSynchStatus();
		return super.createStoredProcedureCall( procedureName, resultClasses );
	}

	@Override
	public SessionFactoryImplementor getSessionFactory() {
//		checkTransactionSynchStatus();
		return getFactory();
	}

	@Override
	public void initializeCollection(PersistentCollection<?> collection, boolean writing) {
		checkOpenOrWaitingForAutoClose();
		pulseTransactionCoordinator();
		InitializeCollectionEvent event = new InitializeCollectionEvent( collection, this );
		fastSessionServices.eventListenerGroup_INIT_COLLECTION.fireEventOnEachListener( event, InitializeCollectionEventListener::onInitializeCollection );
		delayedAfterCompletion();
	}

	@Override
	public String bestGuessEntityName(Object object) {
		if ( object instanceof HibernateProxy ) {
			LazyInitializer initializer = ( (HibernateProxy) object ).getHibernateLazyInitializer();
			// it is possible for this method to be called during flush processing,
			// so make certain that we do not accidentally initialize an uninitialized proxy
			if ( initializer.isUninitialized() ) {
				return initializer.getEntityName();
			}
			object = initializer.getImplementation();
		}
		EntityEntry entry = persistenceContext.getEntry( object );
		if ( entry == null ) {
			return guessEntityName( object );
		}
		else {
			return entry.getPersister().getEntityName();
		}
	}

	@Override
	public String getEntityName(Object object) {
		checkOpen();
//		checkTransactionSynchStatus();
		if ( object instanceof HibernateProxy ) {
			if ( !persistenceContext.containsProxy( object ) ) {
				throw new TransientObjectException( "proxy was not associated with the session" );
			}
			object = ( (HibernateProxy) object ).getHibernateLazyInitializer().getImplementation();
		}

		EntityEntry entry = persistenceContext.getEntry( object );
		if ( entry == null ) {
			throwTransientObjectException( object );
		}
		return entry.getPersister().getEntityName();
	}

	private void throwTransientObjectException(Object object) throws HibernateException {
		throw new TransientObjectException(
				"object references an unsaved transient instance - save the transient instance before flushing: " +
						guessEntityName( object )
		);
	}

	@Override
	public String guessEntityName(Object object) throws HibernateException {
		checkOpenOrWaitingForAutoClose();
		return getEntityNameResolver().resolveEntityName( object );
	}

	@Override
	public void cancelQuery() throws HibernateException {
		checkOpen();
		getJdbcCoordinator().cancelLastQuery();
	}


	@Override
	public int getDontFlushFromFind() {
		return dontFlushFromFind;
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder( 500 )
				.append( "SessionImpl(" ).append( System.identityHashCode( this ) );
		if ( !isClosed() ) {
			if ( log.isTraceEnabled() ) {
				buf.append( persistenceContext )
					.append( ";" )
					.append( actionQueue );
			}
			else {
				buf.append( "<open>" );
			}
		}
		else {
			buf.append( "<closed>" );
		}
		return buf.append( ')' ).toString();
	}

	@Override
	public ActionQueue getActionQueue() {
		checkOpenOrWaitingForAutoClose();
//		checkTransactionSynchStatus();
		return actionQueue;
	}

	@Override
	public PersistenceContext getPersistenceContext() {
		checkOpenOrWaitingForAutoClose();
//		checkTransactionSynchStatus();
		return persistenceContext;
	}

	@Override
	public PersistenceContext getPersistenceContextInternal() {
		return persistenceContext;
	}

	@Override
	public SessionStatistics getStatistics() {
		pulseTransactionCoordinator();
		return new SessionStatisticsImpl( this );
	}

	@Override
	public boolean isEventSource() {
		pulseTransactionCoordinator();
		return true;
	}

	@Override
	public boolean isDefaultReadOnly() {
		return persistenceContext.isDefaultReadOnly();
	}

	@Override
	public void setDefaultReadOnly(boolean defaultReadOnly) {
		persistenceContext.setDefaultReadOnly( defaultReadOnly );
	}

	@Override
	public boolean isReadOnly(Object entityOrProxy) {
		checkOpen();
//		checkTransactionSynchStatus();
		return persistenceContext.isReadOnly( entityOrProxy );
	}

	@Override
	public void setReadOnly(Object entity, boolean readOnly) {
		checkOpen();
//		checkTransactionSynchStatus();
		persistenceContext.setReadOnly( entity, readOnly );
	}

	@Override
	public void afterScrollOperation() {
		// nothing to do in a stateful session
	}

	@Override
	public LoadQueryInfluencers getLoadQueryInfluencers() {
		return loadQueryInfluencers;
	}

	// filter support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public Filter getEnabledFilter(String filterName) {
		pulseTransactionCoordinator();
		return loadQueryInfluencers.getEnabledFilter( filterName );
	}

	@Override
	public Filter enableFilter(String filterName) {
		checkOpen();
		pulseTransactionCoordinator();
		return loadQueryInfluencers.enableFilter( filterName );
	}

	@Override
	public void disableFilter(String filterName) {
		checkOpen();
		pulseTransactionCoordinator();
		loadQueryInfluencers.disableFilter( filterName );
	}


	// fetch profile support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public boolean isFetchProfileEnabled(String name) throws UnknownProfileException {
		return loadQueryInfluencers.isFetchProfileEnabled( name );
	}

	@Override
	public void enableFetchProfile(String name) throws UnknownProfileException {
		loadQueryInfluencers.enableFetchProfile( name );
	}

	@Override
	public void disableFetchProfile(String name) throws UnknownProfileException {
		loadQueryInfluencers.disableFetchProfile( name );
	}

	@Override
	public LobHelper getLobHelper() {
		if ( lobHelper == null ) {
			lobHelper = new LobHelperImpl( this );
		}
		return lobHelper;
	}

	private transient LobHelperImpl lobHelper;

	private Transaction getTransactionIfAccessible() {
		// We do not want an exception to be thrown if the transaction
		// is not accessible. If the transaction is not accessible,
		// then return null.
		return fastSessionServices.isJtaTransactionAccessible ? accessTransaction() : null;
	}

	@Override
	public void beforeTransactionCompletion() {
		log.trace( "SessionImpl#beforeTransactionCompletion()" );
		flushBeforeTransactionCompletion();
		actionQueue.beforeTransactionCompletion();
		try {
			getInterceptor().beforeTransactionCompletion( getTransactionIfAccessible() );
		}
		catch (Throwable t) {
			log.exceptionInBeforeTransactionCompletionInterceptor( t );
		}
		super.beforeTransactionCompletion();
	}

	@Override
	public void afterTransactionCompletion(boolean successful, boolean delayed) {
		if ( log.isTraceEnabled() ) {
			log.tracef( "SessionImpl#afterTransactionCompletion(successful=%s, delayed=%s)", successful, delayed );
		}

		if ( !isClosed() || waitingForAutoClose ) {
			if ( autoClear ||!successful ) {
				internalClear();
			}
		}

		persistenceContext.afterTransactionCompletion();
		actionQueue.afterTransactionCompletion( successful );

		getEventListenerManager().transactionCompletion( successful );

		final StatisticsImplementor statistics = getFactory().getStatistics();
		if ( statistics.isStatisticsEnabled() ) {
			statistics.endTransaction( successful );
		}

		try {
			getInterceptor().afterTransactionCompletion( getTransactionIfAccessible() );
		}
		catch (Throwable t) {
			log.exceptionInAfterTransactionCompletionInterceptor( t );
		}

		if ( !delayed ) {
			if ( shouldAutoClose() && (!isClosed() || waitingForAutoClose) ) {
				managedClose();
			}
		}

		super.afterTransactionCompletion( successful, delayed );
	}

	private static class LobHelperImpl implements LobHelper {
		private final SessionImpl session;

		private LobHelperImpl(SessionImpl session) {
			this.session = session;
		}

		@Override
		public Blob createBlob(byte[] bytes) {
			return lobCreator().createBlob( bytes );
		}

		private LobCreator lobCreator() {
			// Always use NonContextualLobCreator.  If ContextualLobCreator is
			// used both here and in WrapperOptions,
			return NonContextualLobCreator.INSTANCE;
		}

		@Override
		public Blob createBlob(InputStream stream, long length) {
			return lobCreator().createBlob( stream, length );
		}

		@Override
		public Clob createClob(String string) {
			return lobCreator().createClob( string );
		}

		@Override
		public Clob createClob(Reader reader, long length) {
			return lobCreator().createClob( reader, length );
		}

		@Override
		public NClob createNClob(String string) {
			return lobCreator().createNClob( string );
		}

		@Override
		public NClob createNClob(Reader reader, long length) {
			return lobCreator().createNClob( reader, length );
		}
	}

	private static class SharedSessionBuilderImpl<T extends SharedSessionBuilder>
			extends SessionFactoryImpl.SessionBuilderImpl<T>
			implements SharedSessionBuilder<T>, SharedSessionCreationOptions {
		private final SessionImpl session;
		private boolean shareTransactionContext;

		private SharedSessionBuilderImpl(SessionImpl session) {
			super( (SessionFactoryImpl) session.getFactory() );
			this.session = session;
			super.tenantIdentifier( session.getTenantIdentifier() );
		}

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// SharedSessionBuilder


		@Override
		public T tenantIdentifier(String tenantIdentifier) {
			// todo : is this always true?  Or just in the case of sharing JDBC resources?
			throw new SessionException( "Cannot redefine tenant identifier on child session" );
		}

		@Override
		public T interceptor() {
			return interceptor( session.getInterceptor() );
		}

		@Override
		@SuppressWarnings("unchecked")
		public T connection() {
			this.shareTransactionContext = true;
			return (T) this;
		}

		@Override
		public T connectionReleaseMode() {
			return connectionReleaseMode( session.getJdbcCoordinator().getLogicalConnection().getConnectionHandlingMode().getReleaseMode() );
		}

		@Override
		public T connectionHandlingMode() {
			return connectionHandlingMode( session.getJdbcCoordinator().getLogicalConnection().getConnectionHandlingMode() );
		}

		@Override
		public T autoJoinTransactions() {
			return autoJoinTransactions( session.isAutoCloseSessionEnabled() );
		}

		@Override
		public T flushMode() {
			return flushMode( session.getHibernateFlushMode() );
		}

		@Override
		public T autoClose() {
			return autoClose( session.autoClose );
		}

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// SharedSessionCreationOptions

		@Override
		public boolean isTransactionCoordinatorShared() {
			return shareTransactionContext;
		}

		@Override
		public TransactionCoordinator getTransactionCoordinator() {
			return shareTransactionContext ? session.getTransactionCoordinator() : null;
		}

		@Override
		public JdbcCoordinator getJdbcCoordinator() {
			return shareTransactionContext ? session.getJdbcCoordinator() : null;
		}

		@Override
		public TransactionImplementor getTransaction() {
			return shareTransactionContext ? session.getCurrentTransaction() : null;
		}

		@Override
		public ActionQueue.TransactionCompletionProcesses getTransactionCompletionProcesses() {
			return shareTransactionContext ?
					session.getActionQueue().getTransactionCompletionProcesses() :
					null;
		}

		@Override
		public boolean isQueryParametersValidationEnabled() {
			return session.isQueryParametersValidationEnabled();
		}
	}

	private class LockRequestImpl implements LockRequest {
		private final LockOptions lockOptions;

		private LockRequestImpl(LockOptions lo) {
			lockOptions = new LockOptions();
			LockOptions.copy( lo, lockOptions );
		}

		@Override
		public LockMode getLockMode() {
			return lockOptions.getLockMode();
		}

		@Override
		public LockRequest setLockMode(LockMode lockMode) {
			lockOptions.setLockMode( lockMode );
			return this;
		}

		@Override
		public int getTimeOut() {
			return lockOptions.getTimeOut();
		}

		@Override
		public LockRequest setTimeOut(int timeout) {
			lockOptions.setTimeOut( timeout );
			return this;
		}

		@Override
		public boolean getScope() {
			return lockOptions.getScope();
		}

		@Override
		public LockRequest setScope(boolean scope) {
			lockOptions.setScope( scope );
			return this;
		}

		@Override
		public void lock(String entityName, Object object) throws HibernateException {
			fireLock( entityName, object, lockOptions );
		}

		@Override
		public void lock(Object object) throws HibernateException {
			fireLock( object, lockOptions );
		}
	}

	@Override
	protected void addSharedSessionTransactionObserver(TransactionCoordinator transactionCoordinator) {
		this.transactionObserver = new TransactionObserver() {
			@Override
			public void afterBegin() {
			}

			@Override
			public void beforeCompletion() {
				if ( isOpen() && getHibernateFlushMode() !=  FlushMode.MANUAL ) {
					managedFlush();
				}
				actionQueue.beforeTransactionCompletion();
				try {
					getInterceptor().beforeTransactionCompletion( getTransactionIfAccessible() );
				}
				catch (Throwable t) {
					log.exceptionInBeforeTransactionCompletionInterceptor( t );
				}
			}

			@Override
			public void afterCompletion(boolean successful, boolean delayed) {
				afterTransactionCompletion( successful, delayed );
				if ( !isClosed() && autoClose ) {
					managedClose();
				}
			}
		};
		transactionCoordinator.addObserver(transactionObserver);
	}

	@Override
	protected void removeSharedSessionTransactionObserver(TransactionCoordinator transactionCoordinator) {
		super.removeSharedSessionTransactionObserver( transactionCoordinator );
		transactionCoordinator.removeObserver( transactionObserver );
	}

	private EntityPersister requireEntityPersister(Class entityClass) {
		return getFactory().getMetamodel().locateEntityPersister( entityClass );
	}

	private EntityPersister requireEntityPersister(String entityName) {
		return getFactory().getMetamodel().locateEntityPersister( entityName );
	}

	@Override
	public void startTransactionBoundary() {
		checkOpenOrWaitingForAutoClose();
		super.startTransactionBoundary();
	}

	@Override
	public void afterTransactionBegin() {
		checkOpenOrWaitingForAutoClose();
		getInterceptor().afterTransactionBegin( getTransactionIfAccessible() );
	}

	@Override
	public void flushBeforeTransactionCompletion() {
		final boolean doFlush = isTransactionFlushable()
				&& getHibernateFlushMode() != FlushMode.MANUAL;

		try {
			if ( doFlush ) {
				managedFlush();
			}
		}
		catch (RuntimeException re) {
			throw ExceptionMapperStandardImpl.INSTANCE.mapManagedFlushFailure( "error during managed flush", re, this );
		}
	}

	private boolean isTransactionFlushable() {
		if ( getCurrentTransaction() == null ) {
			// assume it is flushable - CMT, auto-commit, etc
			return true;
		}
		final TransactionStatus status = getCurrentTransaction().getStatus();
		return status == TransactionStatus.ACTIVE || status == TransactionStatus.COMMITTING;
	}

	@Override
	public boolean isFlushBeforeCompletionEnabled() {
		return getHibernateFlushMode() != FlushMode.MANUAL;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// HibernateEntityManager impl

	@Override
	public SessionImplementor getSession() {
		return this;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// HibernateEntityManagerImplementor impl


//	@Override
//	public LockOptions getLockRequest(LockModeType lockModeType, Map<String, Object> properties) {
//		LockOptions lockOptions = new LockOptions();
//		if ( this.lockOptions != null ) { //otherwise the default LockOptions constructor is the same as DEFAULT_LOCK_OPTIONS
//			LockOptions.copy( this.lockOptions, lockOptions );
//		}
//		lockOptions.setLockMode( LockModeTypeHelper.getLockMode( lockModeType ) );
//		if ( properties != null ) {
//			LockOptionsHelper.applyPropertiesToLockOptions( properties, () -> lockOptions );
//		}
//		return lockOptions;
//	}




	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// EntityManager impl

	@Override
	public void remove(Object entity) {
		checkOpen();

		try {
			delete( entity );
		}
		catch (MappingException e) {
			throw getExceptionConverter().convert( new IllegalArgumentException( e.getMessage(), e ) );
		}
		catch ( RuntimeException e ) {
			//including HibernateException
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public <T> T find(Class<T> entityClass, Object primaryKey) {
		return find( entityClass, primaryKey, null, null );
	}

	@Override
	public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
		return find( entityClass, primaryKey, null, properties );
	}

	@Override
	public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockModeType) {
		return find( entityClass, primaryKey, lockModeType, null );
	}

	@Override
	public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockModeType, Map<String, Object> properties) {
		checkOpen();

		LockOptions lockOptions = null;

		try {
			getLoadQueryInfluencers().getEffectiveEntityGraph().applyConfiguredGraph( properties );
			Boolean readOnly = properties == null ? null : (Boolean) properties.get( QueryHints.HINT_READONLY );
			getLoadQueryInfluencers().setReadOnly( readOnly );
			final IdentifierLoadAccess<T> loadAccess = byId( entityClass );
			loadAccess.with( determineAppropriateLocalCacheMode( properties ) );

			if ( lockModeType != null ) {
				if ( !LockModeType.NONE.equals( lockModeType) ) {
					checkTransactionNeededForUpdateOperation();
				}
				lockOptions = buildLockOptions( lockModeType, properties );
				loadAccess.with( lockOptions );
			}

			if ( getLoadQueryInfluencers().getEffectiveEntityGraph().getSemantic() == GraphSemantic.FETCH ) {
				setEnforcingFetchGraph( true );
			}

			return loadAccess.load( primaryKey );
		}
		catch ( EntityNotFoundException ignored ) {
			// DefaultLoadEventListener#returnNarrowedProxy() may throw ENFE (see HHH-7861 for details),
			// which find() should not throw.  Find() should return null if the entity was not found.
			if ( log.isDebugEnabled() ) {
				String entityName = entityClass != null ? entityClass.getName(): null;
				String identifierValue = primaryKey != null ? primaryKey.toString() : null ;
				log.ignoringEntityNotFound( entityName, identifierValue );
			}
			return null;
		}
		catch ( ObjectDeletedException e ) {
			//the spec is silent about people doing remove() find() on the same PC
			return null;
		}
		catch ( ObjectNotFoundException e ) {
			//should not happen on the entity itself with get
			throw new IllegalArgumentException( e.getMessage(), e );
		}
		catch ( MappingException | TypeMismatchException | ClassCastException e ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( e.getMessage(), e ) );
		}
		catch ( JDBCException e ) {
			if ( accessTransaction().isActive() && accessTransaction().getRollbackOnly() ) {
				// Assume this is similar to the WildFly / IronJacamar "feature" described under HHH-12472.
				// Just log the exception and return null.
				if ( log.isDebugEnabled() ) {
					log.debug( "JDBCException was thrown for a transaction marked for rollback; " +
									"this is probably due to an operation failing fast due to the " +
									"transaction marked for rollback.", e );
				}
				return null;
			}
			else {
				throw getExceptionConverter().convert( e, lockOptions );
			}
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e, lockOptions );
		}
		finally {
			getLoadQueryInfluencers().getEffectiveEntityGraph().clear();
			getLoadQueryInfluencers().setReadOnly( null );
			setEnforcingFetchGraph( false );
		}
	}

	protected CacheMode determineAppropriateLocalCacheMode(Map<String, Object> localProperties) {
		CacheRetrieveMode retrieveMode = null;
		CacheStoreMode storeMode = null;
		if ( localProperties != null ) {
			retrieveMode = determineCacheRetrieveMode( localProperties );
			storeMode = determineCacheStoreMode( localProperties );
		}
		if ( retrieveMode == null ) {
			// use the EM setting
			retrieveMode = fastSessionServices.getCacheRetrieveMode( this.properties );
		}
		if ( storeMode == null ) {
			// use the EM setting
			storeMode = fastSessionServices.getCacheStoreMode( this.properties );
		}
		return CacheModeHelper.interpretCacheMode( storeMode, retrieveMode );
	}

	private static CacheRetrieveMode determineCacheRetrieveMode(Map<String, Object> settings) {
		final CacheRetrieveMode cacheRetrieveMode = (CacheRetrieveMode) settings.get( JPA_SHARED_CACHE_RETRIEVE_MODE );
		if ( cacheRetrieveMode == null ) {
			return (CacheRetrieveMode) settings.get( JAKARTA_SHARED_CACHE_RETRIEVE_MODE );
		}
		return cacheRetrieveMode;
	}

	private static CacheStoreMode determineCacheStoreMode(Map<String, Object> settings) {
		final CacheStoreMode cacheStoreMode = (CacheStoreMode) settings.get( JPA_SHARED_CACHE_STORE_MODE );
		if ( cacheStoreMode == null ) {
			return ( CacheStoreMode ) settings.get( JAKARTA_SHARED_CACHE_STORE_MODE );
		}
		return cacheStoreMode;
	}

	private void checkTransactionNeededForUpdateOperation() {
		checkTransactionNeededForUpdateOperation( "no transaction is in progress" );
	}

	@Override
	public <T> T getReference(Class<T> entityClass, Object primaryKey) {
		checkOpen();

		try {
			return byId( entityClass ).getReference( primaryKey );
		}
		catch ( MappingException | TypeMismatchException | ClassCastException e ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( e.getMessage(), e ) );
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public void lock(Object entity, LockModeType lockModeType) {
		lock( entity, lockModeType, null );
	}

	@Override
	public void lock(Object entity, LockModeType lockModeType, Map<String, Object> properties) {
		checkOpen();
		checkTransactionNeededForUpdateOperation();

		if ( !contains( entity ) ) {
			throw new IllegalArgumentException( "entity not in the persistence context" );
		}

		final LockOptions lockOptions = buildLockOptions( lockModeType, properties );
		try {
			buildLockRequest( lockOptions ).lock( entity );
		}
		catch (RuntimeException e) {
			throw getExceptionConverter().convert( e, lockOptions );
		}
	}

	@Override
	public void refresh(Object entity, Map<String, Object> properties) {
		refresh( entity, null, properties );
	}

	@Override
	public void refresh(Object entity, LockModeType lockModeType) {
		refresh( entity, lockModeType, null );
	}

	@Override
	public void refresh(Object entity, LockModeType lockModeType, Map<String, Object> properties) {
		checkOpen();

		final CacheMode previousCacheMode = getCacheMode();
		final CacheMode refreshCacheMode = determineAppropriateLocalCacheMode( properties );

		LockOptions lockOptions = null;
		try {
			setCacheMode( refreshCacheMode );

			if ( !contains( entity ) ) {
				throw getExceptionConverter().convert( new IllegalArgumentException( "Entity not managed" ) );
			}

			if ( lockModeType != null ) {
				if ( !LockModeType.NONE.equals( lockModeType) ) {
					checkTransactionNeededForUpdateOperation();
				}

				lockOptions = buildLockOptions( lockModeType, properties );
				refresh( entity, lockOptions );
			}
			else {
				refresh( entity );
			}
		}
		catch (MappingException e) {
			throw getExceptionConverter().convert( new IllegalArgumentException( e.getMessage(), e ) );
		}
		catch (RuntimeException e) {
			throw getExceptionConverter().convert( e, lockOptions );
		}
		finally {
			setCacheMode( previousCacheMode );
		}
	}

	private LockOptions buildLockOptions(LockModeType lockModeType, Map<String, Object> properties) {
		LockOptions lockOptions = new LockOptions();
		if ( this.lockOptions != null ) { //otherwise the default LockOptions constructor is the same as DEFAULT_LOCK_OPTIONS
			LockOptions.copy( this.lockOptions, lockOptions );
		}
		lockOptions.setLockMode( LockModeTypeHelper.getLockMode( lockModeType ) );
		if ( properties != null ) {
			LockOptionsHelper.applyPropertiesToLockOptions( properties, () -> lockOptions );
		}
		return lockOptions;
	}

	@Override
	public void detach(Object entity) {
		checkOpen();
		try {
			evict( entity );
		}
		catch (RuntimeException e) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public LockModeType getLockMode(Object entity) {
		checkOpen();

		if ( !isTransactionInProgress() ) {
			throw new TransactionRequiredException( "Call to EntityManager#getLockMode should occur within transaction according to spec" );
		}

		if ( !contains( entity ) ) {
			throw getExceptionConverter().convert( new IllegalArgumentException( "entity not in the persistence context" ) );
		}

		return LockModeTypeHelper.getLockModeType( getCurrentLockMode( entity ) );

	}

	@Override
	public void setProperty(String propertyName, Object value) {
		checkOpen();

		if ( !( value instanceof Serializable ) ) {
			log.warnf( "Property '%s' is not serializable, value won't be set.", propertyName );
			return;
		}

		if ( propertyName == null ) {
			log.warn( "Property having key null is illegal; value won't be set." );
			return;
		}

		//Store property for future reference:

		if ( properties == null ) {
			properties = computeCurrentSessionProperties();
		}
		properties.put( propertyName, value );

		//now actually update settings, if it's any of these which have a direct impact on this Session state:

		if ( AvailableSettings.FLUSH_MODE.equals( propertyName ) ) {
			setHibernateFlushMode( ConfigurationHelper.getFlushMode( value, FlushMode.AUTO ) );
		}
		else if ( JPA_LOCK_SCOPE.equals( propertyName ) || JPA_LOCK_TIMEOUT.equals( propertyName )
				|| JAKARTA_LOCK_SCOPE.equals( propertyName ) || JAKARTA_LOCK_TIMEOUT.equals( propertyName ) ) {
			LockOptionsHelper.applyPropertiesToLockOptions( properties, this::getLockOptionsForWrite );
		}
		else if ( JPA_SHARED_CACHE_RETRIEVE_MODE.equals( propertyName )
				|| JPA_SHARED_CACHE_STORE_MODE.equals( propertyName )
				|| JAKARTA_SHARED_CACHE_RETRIEVE_MODE.equals( propertyName )
				|| JAKARTA_SHARED_CACHE_STORE_MODE.equals( propertyName ) ) {
			setCacheMode(
					CacheModeHelper.interpretCacheMode(
							determineCacheStoreMode( properties ),
							determineCacheRetrieveMode( properties )
					)
			);
		}
	}

	private Map<String, Object> computeCurrentSessionProperties() {
		final HashMap<String, Object> map = new HashMap<>( fastSessionServices.defaultSessionProperties );
		//The FLUSH_MODE is always set at Session creation time, so it needs special treatment to not eagerly initialize this Map:
		map.put( AvailableSettings.FLUSH_MODE, getHibernateFlushMode().name() );
		return map;
	}

	@Override
	public Map<String, Object> getProperties() {
		if ( properties == null ) {
			properties = computeCurrentSessionProperties();
		}
		return Collections.unmodifiableMap( properties );
	}

	@Override
	public ProcedureCall createNamedStoredProcedureQuery(String name) {
		checkOpen();
		try {
			final NamedCallableQueryMemento memento = getFactory().getQueryEngine()
					.getNamedObjectRepository()
					.getCallableQueryMemento( name );
			if ( memento == null ) {
				throw new IllegalArgumentException( "No @NamedStoredProcedureQuery was found with that name : " + name );
			}
			return memento.makeProcedureCall( this );
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public ProcedureCall createStoredProcedureQuery(String procedureName) {
		try {
			return createStoredProcedureCall( procedureName );
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public ProcedureCall createStoredProcedureQuery(String procedureName, Class... resultClasses) {
		try {
			return createStoredProcedureCall( procedureName, resultClasses );
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public ProcedureCall createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
		checkOpen();
		try {
			try {
				return createStoredProcedureCall( procedureName, resultSetMappings );
			}
			catch (UnknownSqlResultSetMappingException unknownResultSetMapping) {
				throw new IllegalArgumentException( unknownResultSetMapping.getMessage(), unknownResultSetMapping );
			}
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public void joinTransaction() {
		checkOpen();
		joinTransaction( true );
	}

	private void joinTransaction(boolean explicitRequest) {
		if ( !getTransactionCoordinator().getTransactionCoordinatorBuilder().isJta() ) {
			if ( explicitRequest ) {
				log.callingJoinTransactionOnNonJtaEntityManager();
			}
			return;
		}

		try {
			getTransactionCoordinator().explicitJoin();
		}
		catch (TransactionRequiredForJoinException e) {
			throw new TransactionRequiredException( e.getMessage() );
		}
		catch (HibernateException he) {
			throw getExceptionConverter().convert( he );
		}
	}

	@Override
	public boolean isJoinedToTransaction() {
		checkOpen();
		return getTransactionCoordinator().isJoined();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T unwrap(Class<T> clazz) {
		checkOpen();

		if ( Session.class.isAssignableFrom( clazz ) ) {
			return (T) this;
		}
		if ( SessionImplementor.class.isAssignableFrom( clazz ) ) {
			return (T) this;
		}
		if ( SharedSessionContractImplementor.class.isAssignableFrom( clazz ) ) {
			return (T) this;
		}
		if ( EntityManager.class.isAssignableFrom( clazz ) ) {
			return (T) this;
		}
		if ( PersistenceContext.class.isAssignableFrom( clazz ) ) {
			return (T) this;
		}

		throw new PersistenceException( "Hibernate cannot unwrap " + clazz );
	}

	@Override
	public Object getDelegate() {
		checkOpen();
		return this;
	}

	@Override
	public SessionFactoryImplementor getEntityManagerFactory() {
		checkOpen();
		return getFactory();
	}

	@Override
	public MetamodelImplementor getMetamodel() {
		checkOpen();
		return getFactory().getMetamodel();
	}

	@Override
	public <T> RootGraphImplementor<T> createEntityGraph(Class<T> rootType) {
		checkOpen();
		return new RootGraphImpl<>( null, getMetamodel().entity( rootType ), getEntityManagerFactory().getJpaMetamodel() );
	}

	@Override
	public RootGraphImplementor<?> createEntityGraph(String graphName) {
		checkOpen();
		final RootGraphImplementor named = getEntityManagerFactory().findEntityGraphByName( graphName );
		if ( named == null ) {
			return null;
		}
		return named.makeRootGraph( graphName, true );
	}

	@Override
	public RootGraphImplementor<?> getEntityGraph(String graphName) {
		checkOpen();
		final RootGraphImplementor named = getEntityManagerFactory().findEntityGraphByName( graphName );
		if ( named == null ) {
			throw new IllegalArgumentException( "Could not locate EntityGraph with given name : " + graphName );
		}
		return named;
	}

	@Override
	public <T> List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
		checkOpen();
		return getEntityManagerFactory().findEntityGraphsByType( entityClass );
	}

	/**
	 * Used by JDK serialization...
	 *
	 * @param oos The output stream to which we are being written...
	 *
	 * @throws IOException Indicates a general IO stream exception
	 */
	private void writeObject(ObjectOutputStream oos) throws IOException {
		if ( log.isTraceEnabled() ) {
			log.tracef( "Serializing Session [%s]", getSessionIdentifier() );
		}

		oos.defaultWriteObject();

		persistenceContext.serialize( oos );
		actionQueue.serialize( oos );

		oos.writeObject( loadQueryInfluencers );
	}

	/**
	 * Used by JDK serialization...
	 *
	 * @param ois The input stream from which we are being read...
	 *
	 * @throws IOException Indicates a general IO stream exception
	 * @throws ClassNotFoundException Indicates a class resolution issue
	 */
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException, SQLException {
		if ( log.isTraceEnabled() ) {
			log.tracef( "Deserializing Session [%s]", getSessionIdentifier() );
		}

		ois.defaultReadObject();

		persistenceContext = StatefulPersistenceContext.deserialize( ois, this );
		actionQueue = ActionQueue.deserialize( ois, this );

		loadQueryInfluencers = (LoadQueryInfluencers) ois.readObject();

		// LoadQueryInfluencers#getEnabledFilters() tries to validate each enabled
		// filter, which will fail when called before FilterImpl#afterDeserialize( factory );
		// Instead lookup the filter by name and then call FilterImpl#afterDeserialize( factory ).
		for ( String filterName : loadQueryInfluencers.getEnabledFilterNames() ) {
			( (FilterImpl) loadQueryInfluencers.getEnabledFilter( filterName ) ).afterDeserialize( getFactory() );
		}
	}

	private Boolean getReadOnlyFromLoadQueryInfluencers() {
		Boolean readOnly = null;
		if ( loadQueryInfluencers != null ) {
			readOnly = loadQueryInfluencers.getReadOnly();
		}
		return readOnly;
	}

	@Override
	public boolean isEnforcingFetchGraph() {
		return this.isEnforcingFetchGraph;
	}

	@Override
	public void setEnforcingFetchGraph(boolean isEnforcingFetchGraph) {
		this.isEnforcingFetchGraph = isEnforcingFetchGraph;
	}

}
