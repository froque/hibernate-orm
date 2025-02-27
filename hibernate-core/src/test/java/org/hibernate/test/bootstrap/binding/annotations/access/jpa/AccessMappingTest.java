/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.bootstrap.binding.annotations.access.jpa;

import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.internal.BasicEntityIdentifierMappingImpl;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Animal;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Being;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Course1;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Course2;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Course3;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Course4;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Course5;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Course6;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Course7;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Course8;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Horse;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Person;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.Student;
import org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa.User;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.property.access.spi.GetterFieldImpl;
import org.hibernate.property.access.spi.GetterMethodImpl;
import org.hibernate.service.ServiceRegistry;

import org.hibernate.testing.ServiceRegistryBuilder;
import org.hibernate.testing.TestForIssue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Tests verifying the correct behaviour for the usage of {@code @jakarta.persistence.Access}.
 *
 * @author Hardy Ferentschik
 */
@SuppressWarnings({ "deprecation" })
public class AccessMappingTest {
	private ServiceRegistry serviceRegistry;

	@Before
	public void setUp() {
		serviceRegistry = ServiceRegistryBuilder.buildServiceRegistry( Environment.getProperties() );
	}

	@After
	public void tearDown() {
		if ( serviceRegistry != null ) {
			ServiceRegistryBuilder.destroy( serviceRegistry );
		}
	}

    @Test
    public void testInconsistentAnnotationPlacement() {
        Configuration cfg = new Configuration();
        cfg.addAnnotatedClass( Course1.class );
        cfg.addAnnotatedClass( Student.class );
		SessionFactory sf = null;
		try {
			sf = cfg.buildSessionFactory( serviceRegistry );
			fail( "@Id and @OneToMany are not placed consistently in test entities. SessionFactory creation should fail." );
		}
		catch (MappingException e) {
			// success
		}
		finally {
			if ( sf != null ) {
				sf.close();
			}
		}
	}

	@Test
	public void testFieldAnnotationPlacement() {
		Configuration cfg = new Configuration();
		Class<?> classUnderTest = Course6.class;
		cfg.addAnnotatedClass( classUnderTest );
		cfg.addAnnotatedClass( Student.class );
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory( serviceRegistry );
		try {
			final EntityPersister entityPersister = factory.getEntityPersister( classUnderTest.getName() );
			final BasicEntityIdentifierMappingImpl identifierMapping = (BasicEntityIdentifierMappingImpl) entityPersister.getIdentifierMapping();

			assertTrue(
					"Field access should be used.",
					identifierMapping.getPropertyAccess().getGetter() instanceof GetterFieldImpl
			);
		}
		finally {
			factory.close();
		}
	}

	@Test
	public void testPropertyAnnotationPlacement() {
		Configuration cfg = new Configuration();
		Class<?> classUnderTest = Course7.class;
		cfg.addAnnotatedClass( classUnderTest );
		cfg.addAnnotatedClass( Student.class );
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory( serviceRegistry );
		try {
			final EntityPersister entityPersister = factory.getEntityPersister( classUnderTest.getName() );
			final BasicEntityIdentifierMappingImpl identifierMapping = (BasicEntityIdentifierMappingImpl) entityPersister.getIdentifierMapping();

			assertTrue(
					"Property access should be used.",
					identifierMapping.getPropertyAccess().getGetter() instanceof GetterMethodImpl
			);
		}
		finally {
			factory.close();
		}
	}

	@Test
	public void testExplicitPropertyAccessAnnotationsOnProperty() throws Exception {
		Configuration cfg = new Configuration();
		Class<?> classUnderTest = Course2.class;
		cfg.addAnnotatedClass( classUnderTest );
		cfg.addAnnotatedClass( Student.class );
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory( serviceRegistry );
		try {
			final EntityPersister entityPersister = factory.getEntityPersister( classUnderTest.getName() );
			final BasicEntityIdentifierMappingImpl identifierMapping = (BasicEntityIdentifierMappingImpl) entityPersister.getIdentifierMapping();

			assertTrue(
					"Property access should be used.",
					identifierMapping.getPropertyAccess().getGetter() instanceof GetterMethodImpl
			);
		}
		finally {
			factory.close();
		}
	}

	@Test
	public void testExplicitPropertyAccessAnnotationsOnField() {
		Configuration cfg = new Configuration();
		cfg.addAnnotatedClass( Course4.class );
		cfg.addAnnotatedClass( Student.class );
		SessionFactory sf = null;
		try {
			sf = cfg.buildSessionFactory( serviceRegistry );
			fail( "@Id and @OneToMany are not placed consistently in test entities. SessionFactory creation should fail." );
		}
		catch (MappingException e) {
			// success
		}
		finally {
			if ( sf != null ) {
				sf.close();
			}
		}
	}

	@Test
	public void testExplicitPropertyAccessAnnotationsWithHibernateStyleOverride() {
		Configuration cfg = new Configuration();
		Class<?> classUnderTest = Course3.class;
		cfg.addAnnotatedClass( classUnderTest );
		cfg.addAnnotatedClass( Student.class );
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory( serviceRegistry );
		try {
			final EntityPersister entityPersister = factory.getEntityPersister( classUnderTest.getName() );
			final BasicEntityIdentifierMappingImpl identifierMapping = (BasicEntityIdentifierMappingImpl) entityPersister.getIdentifierMapping();

			assertTrue(
					"Field access should be used.",
					identifierMapping.getPropertyAccess().getGetter() instanceof GetterFieldImpl
			);

			assertTrue(
					"Property access should be used.",
					entityPersister.getAttributeMapping( 0 ).getPropertyAccess().getGetter() instanceof GetterMethodImpl
			);
		}
		finally {
			factory.close();
		}
	}

	@Test
	public void testExplicitPropertyAccessAnnotationsWithJpaStyleOverride() {
		Configuration cfg = new Configuration();
		Class<?> classUnderTest = Course5.class;
		cfg.addAnnotatedClass( classUnderTest );
		cfg.addAnnotatedClass( Student.class );
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory( serviceRegistry );
		try {
			final EntityPersister entityPersister = factory.getEntityPersister( classUnderTest.getName() );
			final BasicEntityIdentifierMappingImpl identifierMapping = (BasicEntityIdentifierMappingImpl) entityPersister.getIdentifierMapping();

			assertTrue(
					"Field access should be used.",
					identifierMapping.getPropertyAccess().getGetter() instanceof GetterFieldImpl
			);

			assertTrue(
					"Property access should be used.",
					entityPersister.getAttributeMapping( 0 ).getPropertyAccess().getGetter() instanceof GetterMethodImpl
			);
		}
		finally {
			factory.close();
		}
	}

	@Test
	public void testDefaultFieldAccessIsInherited() {
		Configuration cfg = new Configuration();
		Class<?> classUnderTest = User.class;
		cfg.addAnnotatedClass( classUnderTest );
		cfg.addAnnotatedClass( Person.class );
		cfg.addAnnotatedClass( Being.class );
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory( serviceRegistry );
		try {
			final EntityPersister entityPersister = factory.getEntityPersister( classUnderTest.getName() );
			final BasicEntityIdentifierMappingImpl identifierMapping = (BasicEntityIdentifierMappingImpl) entityPersister.getIdentifierMapping();

			assertTrue(
					"Field access should be used since the default access mode gets inherited",
					identifierMapping.getPropertyAccess().getGetter() instanceof GetterFieldImpl
			);
		}
		finally {
			factory.close();
		}
	}

	@Test
	public void testDefaultPropertyAccessIsInherited() {
		Configuration cfg = new Configuration();
		cfg.addAnnotatedClass( Horse.class );
		cfg.addAnnotatedClass( Animal.class );

		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory( serviceRegistry );
		try {
			EntityPersister entityPersister = factory.getEntityPersister( Animal.class.getName() );
			final BasicEntityIdentifierMappingImpl identifierMapping = (BasicEntityIdentifierMappingImpl) entityPersister.getIdentifierMapping();

			assertTrue(
					"Property access should be used since explicity configured via @Access",
					identifierMapping.getPropertyAccess().getGetter() instanceof GetterMethodImpl
			);

			entityPersister = factory.getEntityPersister( Horse.class.getName() );

			assertTrue(
					"Field access should be used since the default access mode gets inherited",
					entityPersister.getAttributeMapping( 0 ).getPropertyAccess().getGetter() instanceof GetterFieldImpl
			);
		}
		finally {
			factory.close();
		}
	}

	@TestForIssue(jiraKey = "HHH-5004")
	@Test
	public void testAccessOnClassAndId() {
		Configuration cfg = new Configuration();
		cfg.addAnnotatedClass( Course8.class );
		cfg.addAnnotatedClass( Student.class );
		cfg.buildSessionFactory( serviceRegistry ).close();
	}
}
