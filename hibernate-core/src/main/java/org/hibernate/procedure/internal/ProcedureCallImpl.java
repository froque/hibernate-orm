/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.procedure.internal;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.ScrollMode;
import org.hibernate.annotations.QueryHints;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.graph.RootGraph;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.model.domain.AllowableParameterType;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.procedure.NoSuchParameterException;
import org.hibernate.procedure.ParameterStrategyException;
import org.hibernate.procedure.ProcedureCall;
import org.hibernate.procedure.ProcedureOutputs;
import org.hibernate.procedure.spi.CallableStatementSupport;
import org.hibernate.procedure.spi.FunctionReturnImplementor;
import org.hibernate.procedure.spi.NamedCallableQueryMemento;
import org.hibernate.procedure.spi.ParameterStrategy;
import org.hibernate.procedure.spi.ProcedureCallImplementor;
import org.hibernate.procedure.spi.ProcedureParameterImplementor;
import org.hibernate.query.Query;
import org.hibernate.query.QueryParameter;
import org.hibernate.query.internal.QueryOptionsImpl;
import org.hibernate.query.named.NamedQueryMemento;
import org.hibernate.query.procedure.ProcedureParameter;
import org.hibernate.query.results.ResultSetMapping;
import org.hibernate.query.results.ResultSetMappingImpl;
import org.hibernate.query.spi.AbstractQuery;
import org.hibernate.query.spi.MutableQueryOptions;
import org.hibernate.query.spi.QueryImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.spi.QueryOptionsAdapter;
import org.hibernate.query.spi.QueryParameterBinding;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.query.spi.ScrollableResultsImplementor;
import org.hibernate.result.NoMoreReturnsException;
import org.hibernate.result.Output;
import org.hibernate.result.ResultSetOutput;
import org.hibernate.result.UpdateCountOutput;
import org.hibernate.result.spi.ResultContext;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.exec.internal.CallbackImpl;
import org.hibernate.sql.exec.internal.JdbcCallRefCursorExtractorImpl;
import org.hibernate.sql.exec.internal.JdbcParameterBindingImpl;
import org.hibernate.sql.exec.internal.JdbcParameterBindingsImpl;
import org.hibernate.sql.exec.spi.Callback;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcCall;
import org.hibernate.sql.exec.spi.JdbcCallParameterRegistration;
import org.hibernate.sql.exec.spi.JdbcCallRefCursorExtractor;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.NoMoreOutputsException;
import org.hibernate.type.BasicType;
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.spi.TypeConfiguration;

import org.jboss.logging.Logger;

import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.Parameter;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TemporalType;
import jakarta.persistence.TransactionRequiredException;

/**
 * Standard implementation of {@link ProcedureCall}
 *
 * @author Steve Ebersole
 */
public class ProcedureCallImpl<R>
		extends AbstractQuery<R>
		implements ProcedureCallImplementor<R>, ResultContext {
	private static final Logger LOG = Logger.getLogger( ProcedureCallImpl.class );

	private final String procedureName;

	private FunctionReturnImpl<R> functionReturn;

	private final ProcedureParameterMetadataImpl parameterMetadata;
	private final ProcedureParamBindings paramBindings;

	private final ResultSetMapping resultSetMapping;

	private Set<String> synchronizedQuerySpaces;

	private final QueryOptionsImpl queryOptions = new QueryOptionsImpl();

	private JdbcCall call;
	private ProcedureOutputsImpl outputs;


	/**
	 * The no-returns form.
	 *
	 * @param session The session
	 * @param procedureName The name of the procedure to call
	 */
	public ProcedureCallImpl(SharedSessionContractImplementor session, String procedureName) {
		super( session );
		this.procedureName = procedureName;

		this.parameterMetadata = new ProcedureParameterMetadataImpl();
		this.paramBindings = new ProcedureParamBindings( parameterMetadata, getSessionFactory() );

		this.resultSetMapping = new ResultSetMappingImpl( procedureName );

		this.synchronizedQuerySpaces = null;
	}

	/**
	 * The result Class(es) return form
	 *
	 * @param session The session
	 * @param procedureName The name of the procedure to call
	 * @param resultClasses The classes making up the result
	 */
	public ProcedureCallImpl(SharedSessionContractImplementor session, String procedureName, Class... resultClasses) {
		super( session );

		assert resultClasses != null && resultClasses.length > 0;

		this.procedureName = procedureName;

		this.parameterMetadata = new ProcedureParameterMetadataImpl();
		this.paramBindings = new ProcedureParamBindings( parameterMetadata, getSessionFactory() );

		this.synchronizedQuerySpaces = new HashSet<>();

		final String mappingId = procedureName + ":" + StringHelper.join( ",", resultClasses );

		this.resultSetMapping = new ResultSetMappingImpl( mappingId );

		Util.resolveResultSetMappingClasses(
				resultClasses,
				resultSetMapping,
				synchronizedQuerySpaces::add,
				() -> getSession().getFactory()
		);
	}

	/**
	 * The result-set-mapping(s) return form
	 *
	 * @param session The session
	 * @param procedureName The name of the procedure to call
	 * @param resultSetMappingNames The names of the result set mappings making up the result
	 */
	public ProcedureCallImpl(
			final SharedSessionContractImplementor session,
			String procedureName,
			String... resultSetMappingNames) {
		super( session );

		assert resultSetMappingNames != null && resultSetMappingNames.length > 0;

		this.procedureName = procedureName;

		this.parameterMetadata = new ProcedureParameterMetadataImpl();
		this.paramBindings = new ProcedureParamBindings( parameterMetadata, getSessionFactory() );

		this.synchronizedQuerySpaces = new HashSet<>();

		final String mappingId = procedureName + ":" + StringHelper.join( ",", resultSetMappingNames );
		this.resultSetMapping = new ResultSetMappingImpl( mappingId );

		Util.resolveResultSetMappingNames(
				resultSetMappingNames,
				resultSetMapping,
				synchronizedQuerySpaces::add,
				() -> getSession().getFactory()
		);
	}

	/**
	 * The named/stored copy constructor
	 *
	 * @param session The session
	 * @param memento The named/stored memento
	 */
	ProcedureCallImpl(SharedSessionContractImplementor session, NamedCallableQueryMemento memento) {
		super( session );

		this.procedureName = memento.getCallableName();

		this.parameterMetadata = new ProcedureParameterMetadataImpl( memento, session );
		this.paramBindings = new ProcedureParamBindings( parameterMetadata, getSessionFactory() );

		this.synchronizedQuerySpaces = CollectionHelper.makeCopy( memento.getQuerySpaces() );

		this.resultSetMapping = new ResultSetMappingImpl( memento.getRegistrationName() );

		Util.resolveResultSetMappings(
				memento.getResultSetMappingNames(),
				memento.getResultSetMappingClasses(),
				resultSetMapping,
				synchronizedQuerySpaces::add,
				() -> getSession().getFactory()
		);

		applyOptions( memento );
	}

	/**
	 * The named/stored copy constructor
	 *
	 * @param session The session
	 * @param memento The named/stored memento
	 */
	ProcedureCallImpl(
			SharedSessionContractImplementor session,
			NamedCallableQueryMemento memento,
			Class<?>... resultTypes) {
		super( session );

		this.procedureName = memento.getCallableName();

		this.parameterMetadata = new ProcedureParameterMetadataImpl( memento, session );
		this.paramBindings = new ProcedureParamBindings( parameterMetadata, getSessionFactory() );

		this.synchronizedQuerySpaces = CollectionHelper.makeCopy( memento.getQuerySpaces() );

		final String mappingId = procedureName + ":" + StringHelper.join( ",", resultTypes );
		this.resultSetMapping = new ResultSetMappingImpl( mappingId );

		Util.resolveResultSetMappings(
				null,
				resultTypes,
				resultSetMapping,
				synchronizedQuerySpaces::add,
				() -> getSession().getFactory()
		);

		applyOptions( memento );
	}

	public ProcedureCallImpl(
			SharedSessionContractImplementor session,
			NamedCallableQueryMementoImpl memento,
			String... resultSetMappingNames) {
		super( session );

		this.procedureName = memento.getCallableName();

		this.parameterMetadata = new ProcedureParameterMetadataImpl( memento, session );
		this.paramBindings = new ProcedureParamBindings( parameterMetadata, getSessionFactory() );

		this.synchronizedQuerySpaces = CollectionHelper.makeCopy( memento.getQuerySpaces() );

		final String mappingId = procedureName + ":" + StringHelper.join( ",", resultSetMappingNames );
		this.resultSetMapping = new ResultSetMappingImpl( mappingId );

		Util.resolveResultSetMappings(
				resultSetMappingNames,
				null,
				resultSetMapping,
				synchronizedQuerySpaces::add,
				() -> getSession().getFactory()
		);

		applyOptions( memento );
	}

	protected void applyOptions(NamedCallableQueryMemento memento) {
		applyOptions( (NamedQueryMemento) memento );

		if ( memento.getHints() != null ) {
			final Object callableFunction = memento.getHints().get( QueryHints.CALLABLE_FUNCTION );
			if ( callableFunction != null && Boolean.parseBoolean( callableFunction.toString() ) ) {
				final List<Class<?>> resultTypes = new ArrayList<>();
				resultSetMapping.visitResultBuilders(
						(index, resultBuilder) -> resultTypes.add( resultBuilder.getJavaType() )
				);
				final TypeConfiguration typeConfiguration = getSessionFactory().getTypeConfiguration();
				final BasicType<?> type;
				if ( resultTypes.size() != 1 || ( type = typeConfiguration.getBasicTypeForJavaType( resultTypes.get( 0 ) ) ) == null ) {
					markAsFunctionCall( Types.REF_CURSOR );
				}
				else {
					markAsFunctionCall( type.getJdbcTypeDescriptor().getJdbcTypeCode() );
				}
			}
		}
	}

	@Override
	public String getProcedureName() {
		return procedureName;
	}

	@Override
	public MutableQueryOptions getQueryOptions() {
		return queryOptions;
	}

	@Override
	public QueryImplementor<R> setLockMode(String alias, LockMode lockMode) {
		throw new IllegalStateException( "Cannot set LockMode on a procedure-call" );
	}

	@Override
	public ProcedureParameterMetadataImpl getParameterMetadata() {
		return parameterMetadata;
	}

	@Override
	public QueryParameterBindings getQueryParameterBindings() {
		return paramBindings;
	}

	public ParameterStrategy getParameterStrategy() {
		return getParameterMetadata().getParameterStrategy();
	}

	@Override
	public boolean isFunctionCall() {
		return functionReturn != null;
	}

	@Override
	public FunctionReturnImplementor<R> getFunctionReturn() {
		return functionReturn;
	}

	@Override
	public ProcedureCall markAsFunctionCall(int sqlType) {
		functionReturn = new FunctionReturnImpl<>( this, sqlType );
		return this;
	}

	@Override
	public QueryParameterBindings getParameterBindings() {
		return paramBindings;
	}

	public SessionFactoryImplementor getSessionFactory() {
		return getSession().getFactory();
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Parameter registrations

	@Override
	@SuppressWarnings("unchecked")
	public ProcedureCallImplementor<R> registerStoredProcedureParameter(int position, Class type, ParameterMode mode) {
		getSession().checkOpen( true );

		try {
			registerParameter( position, type, mode );
		}
		catch (HibernateException he) {
			throw getSession().getExceptionConverter().convert( he );
		}
		catch (RuntimeException e) {
			getSession().markForRollbackOnly();
			throw e;
		}

		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public ProcedureCallImplementor<R> registerStoredProcedureParameter(
			String parameterName,
			Class type,
			ParameterMode mode) {
		getSession().checkOpen( true );
		try {
			registerParameter( parameterName, type, mode );
		}
		catch (HibernateException he) {
			throw getSession().getExceptionConverter().convert( he );
		}
		catch (RuntimeException e) {
			getSession().markForRollbackOnly();
			throw e;
		}

		return this;
	}

	@Override
	public ProcedureCallImplementor<R> registerStoredProcedureParameter(
			int position,
			BasicTypeReference<?> type,
			ParameterMode mode) {
		getSession().checkOpen( true );

		try {
			registerParameter( position, type, mode );
		}
		catch (HibernateException he) {
			throw getSession().getExceptionConverter().convert( he );
		}
		catch (RuntimeException e) {
			getSession().markForRollbackOnly();
			throw e;
		}

		return this;
	}

	@Override
	public ProcedureCallImplementor<R> registerStoredProcedureParameter(
			String parameterName,
			BasicTypeReference<?> type,
			ParameterMode mode) {
		getSession().checkOpen( true );
		try {
			registerParameter( parameterName, type, mode );
		}
		catch (HibernateException he) {
			throw getSession().getExceptionConverter().convert( he );
		}
		catch (RuntimeException e) {
			getSession().markForRollbackOnly();
			throw e;
		}

		return this;
	}

	@Override
	public <T> ProcedureParameter<T> registerParameter(int position, Class<T> javaType, ParameterMode mode) {
		final AllowableParameterType<T> parameterType = getSessionFactory().getDomainModel().resolveQueryParameterType(
				javaType
		);
		final ProcedureParameterImpl<T> procedureParameter = new ProcedureParameterImpl<>(
				position,
				mode,
				parameterType == null ? javaType : parameterType.getJavaType(),
				parameterType
		);
		registerParameter( procedureParameter );
		return procedureParameter;
	}

	@Override
	public <T> ProcedureParameter<T> registerParameter(
			int position,
			BasicTypeReference<T> typeReference,
			ParameterMode mode) {
		final BasicType<T> basicType = getSessionFactory().getTypeConfiguration()
				.getBasicTypeRegistry()
				.resolve( typeReference );
		final ProcedureParameterImpl<T> procedureParameter = new ProcedureParameterImpl<>(
				position,
				mode,
				basicType.getJavaType(),
				basicType
		);
		registerParameter( procedureParameter );
		return procedureParameter;
	}

	private void registerParameter(ProcedureParameterImplementor parameter) {
		getParameterMetadata().registerParameter( parameter );
	}

	@Override
	public ProcedureParameterImplementor getParameterRegistration(int position) {
		return getParameterMetadata().getQueryParameter( position );
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> ProcedureParameterImplementor<T> registerParameter(String name, Class<T> javaType, ParameterMode mode) {
		final AllowableParameterType<T> parameterType = getSessionFactory().getDomainModel().resolveQueryParameterType(
				javaType
		);
		final ProcedureParameterImpl parameter = new ProcedureParameterImpl(
				name,
				mode,
				parameterType.getJavaType(),
				parameterType
		);

		registerParameter( parameter );

		return parameter;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> ProcedureParameterImplementor<T> registerParameter(
			String name,
			BasicTypeReference<T> typeReference,
			ParameterMode mode) {
		final BasicType<T> basicType = getSessionFactory().getTypeConfiguration()
				.getBasicTypeRegistry()
				.resolve( typeReference );
		final ProcedureParameterImpl parameter = new ProcedureParameterImpl(
				name,
				mode,
				basicType.getJavaType(),
				basicType
		);

		registerParameter( parameter );

		return parameter;
	}

	@Override
	public ProcedureParameterImplementor getParameterRegistration(String name) {
		return getParameterMetadata().getQueryParameter( name );
	}

	@Override
	@SuppressWarnings("unchecked")
	public List getRegisteredParameters() {
		return getParameterMetadata().getRegistrationsAsList() ;
	}

	@Override
	public ProcedureOutputs getOutputs() {
		if ( outputs == null ) {
			outputs = buildOutputs();
		}

		return outputs;
	}

	private ProcedureOutputsImpl buildOutputs() {
		// todo : going to need a very specialized Loader for this.
		// or, might be a good time to look at splitting Loader up into:
		//		1) building statement objects
		//		2) executing statement objects
		//		3) processing result sets

		// for now assume there are no resultClasses nor mappings defined..
		// 	TOTAL PROOF-OF-CONCEPT!!!!!!

		// todo : how to identify calls which should be in the form `{? = call procName...}` ??? (note leading param marker)
		// 		more than likely this will need to be a method on the native API.  I can see this as a trigger to
		//		both: (1) add the `? = ` part and also (2) register a REFCURSOR parameter for DBs (Oracle, PGSQL) that
		//		need it.

		final CallableStatementSupport callableStatementSupport = getSession().getJdbcServices()
				.getJdbcEnvironment()
				.getDialect()
				.getCallableStatementSupport();
		this.call = callableStatementSupport.interpretCall( this );

		final Map<ProcedureParameter<?>, JdbcCallParameterRegistration> parameterRegistrations = new IdentityHashMap<>();
		final List<JdbcCallRefCursorExtractor> refCursorExtractors = new ArrayList<>();
		if ( functionReturn != null ) {
			parameterRegistrations.put( functionReturn, call.getFunctionReturn() );
			final JdbcCallRefCursorExtractorImpl refCursorExtractor = call.getFunctionReturn().getRefCursorExtractor();
			if ( refCursorExtractor != null ) {
				refCursorExtractors.add( refCursorExtractor );
			}
		}
		final List<? extends ProcedureParameterImplementor<?>> registrations = getParameterMetadata().getRegistrationsAsList();
		final List<JdbcCallParameterRegistration> jdbcParameters = call.getParameterRegistrations();
		for ( int i = 0; i < registrations.size(); i++ ) {
			final JdbcCallParameterRegistration jdbcCallParameterRegistration = jdbcParameters.get( i );
			parameterRegistrations.put( registrations.get( i ), jdbcCallParameterRegistration );
			final JdbcCallRefCursorExtractorImpl refCursorExtractor = jdbcCallParameterRegistration.getRefCursorExtractor();
			if ( refCursorExtractor != null ) {
				refCursorExtractors.add( refCursorExtractor );
			}
		}

		LOG.debugf( "Preparing procedure call : %s", call );
		final CallableStatement statement = (CallableStatement) getSession()
				.getJdbcCoordinator()
				.getStatementPreparer()
				.prepareStatement( call.getSql(), true );

		// Register the parameter mode and type
		callableStatementSupport.registerParameters(
				procedureName,
				call,
				statement,
				parameterMetadata.getParameterStrategy(),
				parameterMetadata,
				getSession()
		);

		// Apply the parameter bindings
		final JdbcParameterBindings jdbcParameterBindings = new JdbcParameterBindingsImpl( parameterRegistrations.size() );
		for ( Map.Entry<ProcedureParameter<?>, JdbcCallParameterRegistration> entry : parameterRegistrations.entrySet() ) {
			final JdbcCallParameterRegistration registration = entry.getValue();
			if ( registration.getParameterBinder() != null ) {
				final ProcedureParameter<?> parameter = entry.getKey();
				final QueryParameterBinding<?> binding = getParameterBindings().getBinding( parameter );
				if ( !binding.isBound() ) {
					if ( parameter.getPosition() == null ) {
						throw new IllegalArgumentException( "The parameter named [" + parameter + "] was not set! You need to call the setParameter method." );
					}
					else {
						throw new IllegalArgumentException( "The parameter at position [" + parameter + "] was not set! You need to call the setParameter method." );
					}
				}
				jdbcParameterBindings.addBinding(
						(JdbcParameter) registration.getParameterBinder(),
						new JdbcParameterBindingImpl(
								(JdbcMapping) registration.getParameterType(),
								binding.getBindValue()
						)
				);
			}
		}

		final JdbcCallRefCursorExtractor[] extractors = refCursorExtractors.toArray( new JdbcCallRefCursorExtractor[0] );

		final ExecutionContext executionContext = new ExecutionContext() {
			private final Callback callback = new CallbackImpl();

			@Override
			public SharedSessionContractImplementor getSession() {
				return ProcedureCallImpl.this.getSession();
			}

			@Override
			public QueryOptions getQueryOptions() {
				return new QueryOptionsAdapter() {
					@Override
					public Boolean isReadOnly() {
						return false;
					}
				};
			}

			@Override
			public String getQueryIdentifier(String sql) {
				return sql;
			}

			@Override
			public QueryParameterBindings getQueryParameterBindings() {
				return QueryParameterBindings.NO_PARAM_BINDINGS;
			}

			@Override
			public Callback getCallback() {
				return callback;
			}

		};

		// Note that this should actually happen in an executor

		try {
			int paramBindingPosition = functionReturn == null ? 1 : 2;
			for ( JdbcParameterBinder parameterBinder : call.getParameterBinders() ) {
				parameterBinder.bindParameterValue(
						statement,
						paramBindingPosition,
						jdbcParameterBindings,
						executionContext
				);
				paramBindingPosition++;
			}
		}
		catch (SQLException e) {
			throw getSession().getJdbcServices().getSqlExceptionHelper().convert(
					e,
					"Error registering CallableStatement parameters",
					procedureName
			);
		}
		return new ProcedureOutputsImpl( this, parameterRegistrations, extractors, statement );
	}

	@Override
	public String getQueryString() {
		return null;
	}

	/**
	 * Use this form instead of {@link #getSynchronizedQuerySpaces()} when you want to make sure the
	 * underlying Set is instantiated (aka, on add)
	 *
	 * @return The spaces
	 */
	@SuppressWarnings("WeakerAccess")
	protected Set<String> synchronizedQuerySpaces() {
		if ( synchronizedQuerySpaces == null ) {
			synchronizedQuerySpaces = new HashSet<>();
		}
		return synchronizedQuerySpaces;
	}

	@Override
	public Set<String> getSynchronizedQuerySpaces() {
		if ( synchronizedQuerySpaces == null ) {
			return Collections.emptySet();
		}
		else {
			return Collections.unmodifiableSet( synchronizedQuerySpaces );
		}
	}

	@Override
	public ProcedureCallImplementor<R> addSynchronizedQuerySpace(String querySpace) {
		synchronizedQuerySpaces().add( querySpace );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> addSynchronizedEntityName(String entityName) {
		addSynchronizedQuerySpaces( getSession().getFactory().getMetamodel().entityPersister( entityName ) );
		return this;
	}

	@SuppressWarnings("WeakerAccess")
	protected void addSynchronizedQuerySpaces(EntityPersister persister) {
		synchronizedQuerySpaces().addAll( Arrays.asList( (String[]) persister.getQuerySpaces() ) );
	}

	@Override
	public ProcedureCallImplementor<R> addSynchronizedEntityClass(Class entityClass) {
		addSynchronizedQuerySpaces( getSession().getFactory().getMetamodel().entityPersister( entityClass.getName() ) );
		return this;
	}

	@Override
	public NamedCallableQueryMemento toMemento(String name) {
		return new NamedCallableQueryMementoImpl(
				name,
				procedureName,
				getParameterStrategy(),
				toParameterMementos( parameterMetadata ),
				// todo (6.0) : result-set-mapping names
				null,
				// todo (6.0) : result-set-mapping class names
				null,
				getSynchronizedQuerySpaces(),
				isCacheable(),
				getCacheRegion(),
				getCacheMode(),
				getHibernateFlushMode(),
				isReadOnly(),
				getTimeout(),
				getFetchSize(),
				getComment(),
				getHints()
		);
	}

	private static List<NamedCallableQueryMemento.ParameterMemento> toParameterMementos(
			ProcedureParameterMetadataImpl parameterMetadata) {
		if ( parameterMetadata.getParameterStrategy() == ParameterStrategy.UNKNOWN ) {
			// none...
			return Collections.emptyList();
		}

		final List<NamedCallableQueryMemento.ParameterMemento> mementos = new ArrayList<>();

		parameterMetadata.visitRegistrations(
				queryParameter -> {
					final ProcedureParameterImplementor procedureParameter = (ProcedureParameterImplementor) queryParameter;
					mementos.add(
							new NamedCallableQueryMementoImpl.ParameterMementoImpl(
									procedureParameter.getPosition(),
									procedureParameter.getName(),
									procedureParameter.getMode(),
									procedureParameter.getParameterType(),
									procedureParameter.getHibernateType()
							)
					);
				}
		);

		return mementos;
	}

	@Override
	protected void applyEntityGraphQueryHint(String hintName, RootGraphImplementor entityGraph) {
		throw new IllegalStateException( "EntityGraph hints are not supported for ProcedureCall/StoredProcedureQuery" );
	}

	@Override
	public Query<R> applyGraph(RootGraph<?> graph, GraphSemantic semantic) {
		throw new IllegalStateException( "EntityGraph hints are not supported for ProcedureCall/StoredProcedureQuery" );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// outputs

	private ProcedureOutputs procedureResult;

	@Override
	public boolean execute() {
		try {
			final Output rtn = outputs().getCurrent();
			return ResultSetOutput.class.isInstance( rtn );
		}
		catch (NoMoreOutputsException e) {
			return false;
		}
		catch (HibernateException he) {
			throw getSession().getExceptionConverter().convert( he );
		}
		catch (RuntimeException e) {
			getSession().markForRollbackOnly();
			throw e;
		}
	}

	protected ProcedureOutputs outputs() {
		if ( procedureResult == null ) {
			procedureResult = getOutputs();
		}
		return procedureResult;
	}

	@Override
	protected int doExecuteUpdate() {
		if ( !getSession().isTransactionInProgress() ) {
			throw new TransactionRequiredException( "jakarta.persistence.Query.executeUpdate requires active transaction" );
		}

		// the expectation is that there is just one Output, of type UpdateCountOutput
		try {
			execute();
			return getUpdateCount();
		}
		finally {
			outputs().release();
		}
	}

	@Override
	public Object getOutputParameterValue(int position) {
		// NOTE : according to spec (specifically), an exception thrown from this method should not mark for rollback.
		try {
			return outputs().getOutputParameterValue( position );
		}
		catch (ParameterStrategyException e) {
			throw new IllegalArgumentException( "Invalid mix of named and positional parameters", e );
		}
		catch (NoSuchParameterException e) {
			throw new IllegalArgumentException( e.getMessage(), e );
		}
	}

	@Override
	public Object getOutputParameterValue(String parameterName) {
		// NOTE : according to spec (specifically), an exception thrown from this method should not mark for rollback.
		try {
			return outputs().getOutputParameterValue( parameterName );
		}
		catch (ParameterStrategyException e) {
			throw new IllegalArgumentException( "Invalid mix of named and positional parameters", e );
		}
		catch (NoSuchParameterException e) {
			throw new IllegalArgumentException( e.getMessage(), e );
		}
	}

	@Override
	public boolean hasMoreResults() {
		return outputs().goToNext() && ResultSetOutput.class.isInstance( outputs().getCurrent() );
	}

	@Override
	public int getUpdateCount() {
		try {
			final Output rtn = outputs().getCurrent();
			if ( rtn == null ) {
				return -1;
			}
			else if ( UpdateCountOutput.class.isInstance( rtn ) ) {
				return ( (UpdateCountOutput) rtn ).getUpdateCount();
			}
			else {
				return -1;
			}
		}
		catch (NoMoreOutputsException e) {
			return -1;
		}
		catch (HibernateException he) {
			throw getSession().getExceptionConverter().convert( he );
		}
		catch (RuntimeException e) {
			getSession().markForRollbackOnly();
			throw e;
		}
	}

	@Override
	protected List<R> doList() {
		if ( getMaxResults() == 0 ) {
			return Collections.emptyList();
		}
		try {
			final Output rtn = outputs().getCurrent();
			if ( !ResultSetOutput.class.isInstance( rtn ) ) {
				throw new IllegalStateException( "Current CallableStatement ou was not a ResultSet, but getResultList was called" );
			}

			//noinspection unchecked
			return ( (ResultSetOutput) rtn ).getResultList();
		}
		catch (NoMoreOutputsException e) {
			// todo : the spec is completely silent on these type of edge-case scenarios.
			// Essentially here we'd have a case where there are no more results (ResultSets nor updateCount) but
			// getResultList was called.
			return null;
		}
		catch (HibernateException he) {
			throw getSession().getExceptionConverter().convert( he );
		}
		catch (RuntimeException e) {
			getSession().markForRollbackOnly();
			throw e;
		}
	}

	@Override
	public ScrollableResultsImplementor<R> scroll(ScrollMode scrollMode) {
		throw new UnsupportedOperationException( "Query#scroll is not valid for ProcedureCall/StoredProcedureQuery" );
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<R> getResultList() {
		if ( getMaxResults() == 0 ) {
			return Collections.EMPTY_LIST;
		}
		try {
			final Output rtn = outputs().getCurrent();
			if ( !( rtn instanceof ResultSetOutput ) ) {
				throw new IllegalStateException( "Current CallableStatement ou was not a ResultSet, but getResultList was called" );
			}

			return ( (ResultSetOutput) rtn ).getResultList();
		}
		catch (NoMoreReturnsException e) {
			// todo : the spec is completely silent on these type of edge-case scenarios.
			// Essentially here we'd have a case where there are no more results (ResultSets nor updateCount) but
			// getResultList was called.
			return null;
		}
		catch (HibernateException he) {
			throw getSession().getExceptionConverter().convert( he );
		}
		catch (RuntimeException e) {
			getSession().markForRollbackOnly();
			throw e;
		}
	}

	@Override
	public R getSingleResult() {
		final List<R> resultList = getResultList();
		if ( resultList == null || resultList.isEmpty() ) {
			throw new NoResultException(
					String.format(
							"Call to stored procedure [%s] returned no results",
							getProcedureName()
					)
			);
		}
		else if ( resultList.size() > 1 ) {
			throw new NonUniqueResultException(
					String.format(
							"Call to stored procedure [%s] returned multiple results",
							getProcedureName()
					)
			);
		}

		return resultList.get( 0 );
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T unwrap(Class<T> cls) {
		if ( cls.isInstance( this ) ) {
			return (T) this;
		}

		if ( cls.isInstance( parameterMetadata ) ) {
			return (T) parameterMetadata;
		}

		if ( cls.isInstance( paramBindings ) ) {
			return (T) paramBindings;
		}

		if ( cls.isInstance( queryOptions ) ) {
			return (T) queryOptions;
		}

		if ( cls.isInstance( getSession() ) ) {
			return (T) getSession();
		}

		if ( ProcedureOutputs.class.isAssignableFrom( cls ) ) {
			return (T) getOutputs();
		}

		throw new PersistenceException( "Unrecognized unwrap type : " + cls.getName() );
	}

	@Override
	public ProcedureCallImplementor<R> setLockMode(LockModeType lockMode) {
		throw new IllegalStateException("jakarta.persistence.Query.setLockMode not valid on jakarta.persistence.StoredProcedureQuery" );
	}

	@Override
	public LockModeType getLockMode() {
		throw new IllegalStateException( "jakarta.persistence.Query.getHibernateFlushMode not valid on jakarta.persistence.StoredProcedureQuery" );
	}

	@Override
	public ProcedureCallImplementor<R> setHint(String hintName, Object value) {
		super.setHint( hintName, value );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setFlushMode(FlushModeType flushModeType) {
		super.setFlushMode( flushModeType );
		return this;
	}

	// todo (5.3) : all of the parameter stuff here can be done in AbstractQuery
	//		using #getParameterMetadata and #getQueryParameterBindings for abstraction.
	//		this "win" is to define these in one place

	@Override
	public <P> ProcedureCallImplementor<R> setParameter(QueryParameter<P> parameter, P value) {
		super.setParameter( parameter, value );
		return this;
	}

	@Override
	public <P> ProcedureCallImplementor<R> setParameter(Parameter<P> parameter, P value) {
		super.setParameter( parameter, value );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(String name, Object value) {
		super.setParameter( name, value );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(int position, Object value) {
		super.setParameter( position, value );
		return this;
	}

	@Override
	public <P> ProcedureCallImplementor<R> setParameter(
			QueryParameter<P> parameter,
			P value,
			AllowableParameterType<P> type) {
		super.setParameter( parameter, value, type );
		return this;
	}

//	@Override
//	public <P> ProcedureCallImplementor<R> setParameter(QueryParameter<P> parameter, P value, Type type) {
//		super.setParameter( parameter, value, type );
//		return this;
//	}

	@Override
	public <P> ProcedureCallImplementor<R> setParameter(String name, P value, AllowableParameterType<P> type) {
		super.setParameter( name, value, type );
		return this;
	}

//	@Override
//	public ProcedureCallImplementor<R> setParameter(String name, Object value, Type type) {
//		super.setParameter( name, value, type );
//		return this;
//	}

	@Override
	public <P> ProcedureCallImplementor<R> setParameter(int position, P value, AllowableParameterType<P> type) {
		super.setParameter( position, value, type );
		return this;
	}

	@Override
	public <P> ProcedureCallImplementor<R> setParameter(String name, P value, BasicTypeReference<P> type) {
		super.setParameter( name, value, type );
		return this;
	}

	@Override
	public <P> ProcedureCallImplementor<R> setParameter(int position, P value, BasicTypeReference<P> type) {
		super.setParameter( position, value, type );
		return this;
	}

	@Override
	public <P> ProcedureCallImplementor<R> setParameter(QueryParameter<P> parameter, P val, BasicTypeReference<P> type) {
		super.setParameter( parameter, val, type );
		return this;
	}
//	@Override
//	public ProcedureCallImplementor<R> setParameter(int position, Object value, Type type) {
//		super.setParameter( position, value, type );
//		return this;
//	}

	@Override
	public <P> ProcedureCallImplementor<R> setParameter(
			QueryParameter<P> parameter,
			P value,
			TemporalType temporalPrecision) {
		super.setParameter( parameter, value, temporalPrecision );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(String name, Object value, TemporalType temporalPrecision) {
		super.setParameter( name, value, temporalPrecision );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(int position, Object value, TemporalType temporalPrecision) {
		super.setParameter( position, value, temporalPrecision );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(
			Parameter<Calendar> parameter,
			Calendar value,
			TemporalType temporalPrecision) {
		super.setParameter( parameter, value, temporalPrecision );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(Parameter<Date> parameter, Date value, TemporalType temporalPrecision) {
		super.setParameter( parameter, value, temporalPrecision );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(String name, Calendar value, TemporalType temporalPrecision) {
		super.setParameter( name, value, temporalPrecision );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(String name, Date value, TemporalType temporalPrecision) {
		super.setParameter( name, value, temporalPrecision );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(int position, Calendar value, TemporalType temporalPrecision) {
		super.setParameter( position, value, temporalPrecision );
		return this;
	}

	@Override
	public ProcedureCallImplementor<R> setParameter(int position, Date value, TemporalType temporalPrecision) {
		super.setParameter( position, value, temporalPrecision );
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Stream getResultStream() {
		return getResultList().stream();
	}

	public ResultSetMapping getResultSetMapping() {
		return resultSetMapping;
	}
}
