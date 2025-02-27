/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

//$Id$

package org.hibernate.orm.test.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.jpa.HibernateEntityManagerFactory;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test various unwrap scenarios for {@code EntityManagerFactory}.
 */
@TestForIssue(jiraKey = "HHH-9665")
@Jpa
public class EntityManagerFactoryUnwrapTest {

	@Test
	public void testEntityManagerCanBeUnwrappedToSessionFactory(EntityManagerFactoryScope scope) {
		SessionFactory sessionFactory = scope.getEntityManagerFactory().unwrap( SessionFactory.class );
		assertNotNull( sessionFactory, "Unwrapping to API class SessionFactory should be ok" );
	}

	@Test
	public void testEntityManagerCanBeUnwrappedToSessionFactoryImplementor(EntityManagerFactoryScope scope) {
		SessionFactoryImplementor sessionFactoryImplementor = scope.getEntityManagerFactory().unwrap( SessionFactoryImplementor.class );
		assertNotNull( sessionFactoryImplementor, "Unwrapping to SPI class SessionFactoryImplementor should be ok" );
	}

	@Test
	public void testEntityManagerCanBeUnwrappedToDeprecatedHibernateEntityManagerFactory(EntityManagerFactoryScope scope) {
		HibernateEntityManagerFactory hibernateEntityManagerFactory = scope.getEntityManagerFactory().unwrap(
				HibernateEntityManagerFactory.class
		);
		assertNotNull( hibernateEntityManagerFactory, "Unwrapping to SPI class HibernateEntityManagerFactory should be ok" );
	}

	@Test
	public void testEntityManagerCanBeUnwrappedToHibernateEntityManagerFactory(EntityManagerFactoryScope scope) {
		HibernateEntityManagerFactory hibernateEntityManagerFactory = scope.getEntityManagerFactory().unwrap(
				HibernateEntityManagerFactory.class );
		assertNotNull( hibernateEntityManagerFactory, "Unwrapping to SPI class HibernateEntityManagerFactory should be ok"	);
	}

	@Test
	public void testEntityManagerCanBeUnwrappedToObject(EntityManagerFactoryScope scope) {
		Object object = scope.getEntityManagerFactory().unwrap( Object.class );
		assertNotNull( object, "Unwrapping to public super type Object should work" );
	}

	@Test
	public void testEntityManagerCanBeUnwrappedToSessionFactoryImpl(EntityManagerFactoryScope scope) {
		SessionFactoryImpl sessionFactory = scope.getEntityManagerFactory().unwrap( SessionFactoryImpl.class );
		assertNotNull( sessionFactory, "Unwrapping to SessionFactoryImpl should be ok" );
	}

	@Test
	public void testEntityManagerCannotBeUnwrappedToUnrelatedType(EntityManagerFactoryScope scope) {
		try {
			scope.getEntityManagerFactory().unwrap( EntityManager.class );
			fail( "It should not be possible to unwrap to unrelated type." );
		}
		catch ( PersistenceException e ) {
			// ignore
		}
	}
}
