/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.loader.ast.internal;

import java.util.Iterator;
import java.util.List;

import org.hibernate.LockOptions;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.BatchFetchQueue;
import org.hibernate.engine.spi.CollectionKey;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.SubselectFetch;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.loader.ast.spi.CollectionLoader;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.Callback;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcSelect;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.entity.LoadingEntityEntry;
import org.hibernate.sql.results.internal.ResultsHelper;
import org.hibernate.sql.results.internal.RowTransformerPassThruImpl;
import org.hibernate.sql.results.spi.ListResultsConsumer;

/**
 * A one-time use CollectionLoader for applying a sub-select fetch
 *
 * @author Steve Ebersole
 */
public class CollectionLoaderSubSelectFetch implements CollectionLoader {
	private final PluralAttributeMapping attributeMapping;
	private final SubselectFetch subselect;

	private final SelectStatement sqlAst;

	public CollectionLoaderSubSelectFetch(
			PluralAttributeMapping attributeMapping,
			DomainResult cachedDomainResult,
			SubselectFetch subselect,
			SharedSessionContractImplementor session) {
		this.attributeMapping = attributeMapping;
		this.subselect = subselect;

		sqlAst = LoaderSelectBuilder.createSubSelectFetchSelect(
				attributeMapping,
				subselect,
				cachedDomainResult,
				session.getLoadQueryInfluencers(),
				LockOptions.NONE,
				jdbcParameter -> {},
				session.getFactory()
		);
	}

	@Override
	public PluralAttributeMapping getLoadable() {
		return attributeMapping;
	}

	@Override
	public PersistentCollection<?> load(Object triggerKey, SharedSessionContractImplementor session) {
		final CollectionKey collectionKey = new CollectionKey( attributeMapping.getCollectionDescriptor(), triggerKey );

		final SessionFactoryImplementor sessionFactory = session.getFactory();
		final JdbcServices jdbcServices = sessionFactory.getJdbcServices();
		final JdbcEnvironment jdbcEnvironment = jdbcServices.getJdbcEnvironment();
		final SqlAstTranslatorFactory sqlAstTranslatorFactory = jdbcEnvironment.getSqlAstTranslatorFactory();
		final PersistenceContext persistenceContext = session.getPersistenceContext();

		// try to find a registered SubselectFetch
		final PersistentCollection<?> collection = persistenceContext.getCollection( collectionKey );
		attributeMapping.getCollectionDescriptor().getCollectionType().getKeyOfOwner( collection.getOwner(), session );

		final EntityEntry ownerEntry = persistenceContext.getEntry( collection.getOwner() );
		final BatchFetchQueue batchFetchQueue = persistenceContext.getBatchFetchQueue();
		final EntityKey triggerKeyOwnerKey = ownerEntry.getEntityKey();
		final SubselectFetch registeredFetch = batchFetchQueue.getSubselect( triggerKeyOwnerKey );
		List<PersistentCollection<?>> subSelectFetchedCollections = null;
		if ( registeredFetch != null ) {
			subSelectFetchedCollections = CollectionHelper.arrayList( registeredFetch.getResultingEntityKeys().size() );

			// there was one, so we want to make sure to prepare the corresponding collection
			// reference for reading
			final Iterator<EntityKey> itr = registeredFetch.getResultingEntityKeys().iterator();
			while ( itr.hasNext() ) {
				final EntityKey key = itr.next();

				final PersistentCollection<?> containedCollection = persistenceContext.getCollection(
						new CollectionKey( attributeMapping.getCollectionDescriptor(), key.getIdentifier() )
				);

				if ( containedCollection != collection ) {
					containedCollection.beginRead();
					containedCollection.beforeInitialize( getLoadable().getCollectionDescriptor(), -1 );

					subSelectFetchedCollections.add( containedCollection );
				}
			}
		}

		final JdbcSelect jdbcSelect = sqlAstTranslatorFactory
				.buildSelectTranslator( sessionFactory, sqlAst )
				.translate( this.subselect.getLoadingJdbcParameterBindings(), QueryOptions.NONE );

		final SubselectFetch.RegistrationHandler subSelectFetchableKeysHandler = SubselectFetch.createRegistrationHandler(
				batchFetchQueue,
				sqlAst,
				this.subselect.getLoadingJdbcParameters(),
				this.subselect.getLoadingJdbcParameterBindings()
		);

		jdbcServices.getJdbcSelectExecutor().list(
				jdbcSelect,
				this.subselect.getLoadingJdbcParameterBindings(),
				new ExecutionContext() {
					@Override
					public SharedSessionContractImplementor getSession() {
						return session;
					}

					@Override
					public QueryOptions getQueryOptions() {
						return QueryOptions.NONE;
					}

					@Override
					public String getQueryIdentifier(String sql) {
						return sql;
					}

					@Override
					public void registerLoadingEntityEntry(EntityKey entityKey, LoadingEntityEntry entry) {
						subSelectFetchableKeysHandler.addKey( entityKey );
					}

					@Override
					public QueryParameterBindings getQueryParameterBindings() {
						return QueryParameterBindings.NO_PARAM_BINDINGS;
					}

					@Override
					public Callback getCallback() {
						return null;
					}

				},
				RowTransformerPassThruImpl.instance(),
				ListResultsConsumer.UniqueSemantic.FILTER
		);

		if ( subSelectFetchedCollections != null && ! subSelectFetchedCollections.isEmpty() ) {
			subSelectFetchedCollections.forEach(
					c -> {
						if ( c.wasInitialized() ) {
							return;
						}

						c.initializeEmptyCollection( getLoadable().getCollectionDescriptor() );
						ResultsHelper.finalizeCollectionLoading(
								persistenceContext,
								getLoadable().getCollectionDescriptor(),
								c,
								c.getKey(),
								true
						);
					}
			);

			subSelectFetchedCollections.clear();
		}

		return collection;
	}
}
