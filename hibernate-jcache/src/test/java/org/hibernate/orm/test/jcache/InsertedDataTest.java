/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.jcache;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cache.spi.support.DomainDataRegionTemplate;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.testing.orm.junit.ExtraAssertions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hibernate.testing.transaction.TransactionUtil2.inTransaction;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for handling of data just inserted during a transaction being read from the database
 * and placed into cache.  Initially these cases went through putFromRead which causes problems because it
 * loses the context of that data having just been read.
 *
 * @author Steve Ebersole
 */
public class InsertedDataTest {

	private ServiceRegistry serviceRegistry;
	private SessionFactoryImplementor sessionFactory;

	@BeforeEach
	public void acquireResources() {
		serviceRegistry = TestHelper.getStandardServiceRegistryBuilder()
				.applySetting( AvailableSettings.CACHE_REGION_PREFIX, "" )
				.applySetting( AvailableSettings.GENERATE_STATISTICS, "true" )
				.build();

		final Metadata metadata = new MetadataSources( serviceRegistry )
				.addAnnotatedClass( CacheableItem.class )
				.buildMetadata();
		TestHelper.createRegions( metadata, true, false );

		sessionFactory = (SessionFactoryImplementor) metadata.buildSessionFactory();
	}

	@AfterEach
	public void releaseResources() {
		if ( sessionFactory != null ) {
			sessionFactory.close();
		}
	}

	public SessionFactoryImplementor sessionFactory() {
		return sessionFactory;
	}

	@Test
	public void testInsert() {
		sessionFactory().getCache().evictEntityRegions();
		sessionFactory().getStatistics().clear();

		inTransaction(
				sessionFactory,
				s -> {
					CacheableItem item = new CacheableItem( "data" );
					s.save( item );
				}
		);

		assertTrue( sessionFactory().getCache().containsEntity( CacheableItem.class, 1L ) );

		inTransaction(
				sessionFactory,
				s -> s.createQuery( "delete CacheableItem" ).executeUpdate()
		);
	}

	@Test
	public void testInsertWithRollback() {
		sessionFactory().getCache().evictEntityRegions();
		sessionFactory().getStatistics().clear();

		inTransaction(
				sessionFactory,
				s -> {
					CacheableItem item = new CacheableItem( "data" );
					s.save( item );
					s.flush();
					s.getTransaction().markRollbackOnly();
				}
		);

		assertFalse( sessionFactory().getCache().containsEntity( CacheableItem.class, 1L ) );
	}

	@Test
	public void testInsertThenUpdate() {
		sessionFactory().getCache().evictEntityRegions();
		sessionFactory().getStatistics().clear();

		inTransaction(
				sessionFactory,
				s -> {
					CacheableItem item = new CacheableItem( "data" );
					s.save( item );
					s.flush();
					item.setName( "new data" );
				}
		);

		assertTrue( sessionFactory().getCache().containsEntity( CacheableItem.class, 1L ) );

		inTransaction(
				sessionFactory,
				s -> s.createQuery( "delete CacheableItem" ).executeUpdate()
		);
	}

	@Test
	public void testInsertThenUpdateThenRollback() {
		sessionFactory().getCache().evictEntityRegions();
		sessionFactory().getStatistics().clear();

		inTransaction(
				sessionFactory,
				s -> {
					CacheableItem item = new CacheableItem( "data" );
					s.save( item );
					s.flush();
					item.setName( "new data" );
					s.getTransaction().markRollbackOnly();
				}
		);

		assertFalse( sessionFactory().getCache().containsEntity( CacheableItem.class, 1L ) );
	}

	@Test
	public void testInsertWithRefresh() {
		sessionFactory().getCache().evictEntityRegions();
		sessionFactory().getStatistics().clear();

		inTransaction(
				sessionFactory,
				s -> {
					CacheableItem item = new CacheableItem( "data" );
					s.save( item );
					s.flush();
					s.refresh( item );
				}
		);

		assertTrue( sessionFactory().getCache().containsEntity( CacheableItem.class, 1L ) );

		inTransaction(
				sessionFactory,
				s -> s.createQuery( "delete CacheableItem" ).executeUpdate()
		);
	}

	@Test
	public void testInsertWithRefreshThenRollback() {
		sessionFactory().getCache().evictEntityRegions();
		sessionFactory().getStatistics().clear();

		inTransaction(
				sessionFactory,
				s -> {
					CacheableItem item = new CacheableItem( "data" );
					s.save( item );
					s.flush();
					s.refresh( item );
					s.getTransaction().markRollbackOnly();
				}
		);


		inTransaction(
				sessionFactory,
				s -> {
					final DomainDataRegionTemplate region = (DomainDataRegionTemplate) sessionFactory().getCache().getRegion( "item" );
					final Object fromCache = region.getCacheStorageAccess().getFromCache(
							region.getEffectiveKeysFactory().createEntityKey(
									1L,
									sessionFactory().getMetamodel().entityPersister( CacheableItem.class ),
									sessionFactory(),
									null
							),
							s
					);
					assertNotNull( fromCache );
					ExtraAssertions.assertTyping( SoftLock.class, fromCache );
				}
		);

		inTransaction(
				sessionFactory,
				s -> {
					CacheableItem item = s.get( CacheableItem.class, 1L );
					assertNull( item, "it should be null" );
				}
		);
	}

	@Test
	public void testInsertWithClear() {
		sessionFactory().getCache().evictEntityRegions();
		sessionFactory().getStatistics().clear();

		inTransaction(
				sessionFactory,
				s -> {
					CacheableItem item = new CacheableItem( "data" );
					s.save( item );
					s.flush();
					s.clear();
				}
		);

		assertTrue( sessionFactory().getCache().containsEntity( CacheableItem.class, 1L ) );

		inTransaction(
				sessionFactory,
				s -> s.createQuery( "delete CacheableItem" ).executeUpdate()
		);
	}

	@Test
	public void testInsertWithClearThenRollback() {
		sessionFactory().getCache().evictEntityRegions();
		sessionFactory().getStatistics().clear();

		inTransaction(
				sessionFactory,
				s -> {
					CacheableItem item = new CacheableItem( "data" );
					s.save( item );
					s.flush();
					s.clear();
					item = s.get( CacheableItem.class, item.getId() );
					s.getTransaction().markRollbackOnly();
				}
		);

		assertFalse( sessionFactory().getCache().containsEntity( CacheableItem.class, 1L ) );

		inTransaction(
				sessionFactory,
				s -> {
					final CacheableItem item = s.get( CacheableItem.class, 1L );
					assertNull( item, "it should be null" );
				}
		);
	}

	@Entity(name = "CacheableItem")
	@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "item")
	public static class CacheableItem {
		private Long id;
		private String name;

		public CacheableItem() {
		}

		public CacheableItem(String name) {
			this.name = name;
		}

		@Id
		@GeneratedValue(generator = "increment")
		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
