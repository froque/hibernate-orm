/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.envers.performance;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import jakarta.persistence.EntityManager;

import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.Dialect;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.configuration.EnversSettings;
import org.hibernate.envers.boot.internal.EnversIntegrator;
import org.hibernate.orm.test.envers.AbstractEnversTest;
import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.spi.Bootstrap;

import org.hibernate.testing.AfterClassOnce;
import org.hibernate.testing.BeforeClassOnce;
import org.hibernate.testing.orm.jpa.PersistenceUnitDescriptorAdapter;
import org.hibernate.testing.orm.junit.DialectContext;
import org.junit.Before;

/**
 * @author Adam Warski (adam at warski dot org)
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 */
public abstract class AbstractEntityManagerTest extends AbstractEnversTest {
	public static final Dialect DIALECT = DialectContext.getDialect();

	private EntityManagerFactoryBuilderImpl entityManagerFactoryBuilder;
	private StandardServiceRegistryImpl serviceRegistry;
	private HibernateEntityManagerFactory emf;
	private EntityManager entityManager;
	private AuditReader auditReader;
	private boolean audited;

	public void addConfigurationProperties(Properties configuration) {
	}

	protected static Dialect getDialect() {
		return DIALECT;
	}

	private void closeEntityManager() {
		if ( entityManager != null ) {
			entityManager.close();
			entityManager = null;
		}
	}

	@Before
	public void newEntityManager() {
		closeEntityManager();

		entityManager = emf.createEntityManager();

		if ( audited ) {
			auditReader = AuditReaderFactory.get( entityManager );
		}
	}

	@BeforeClassOnce
	public void init() throws IOException {
		init( true, getAuditStrategy() );
	}

	protected void init(boolean audited, String auditStrategy) throws IOException {
		this.audited = audited;

		Properties configurationProperties = new Properties();
		configurationProperties.putAll( Environment.getProperties() );
		if ( !audited ) {
			configurationProperties.setProperty( EnversIntegrator.AUTO_REGISTER, "false" );
		}
		if ( createSchema() ) {
			configurationProperties.setProperty( AvailableSettings.HBM2DDL_AUTO, "create-drop" );
			configurationProperties.setProperty( EnversSettings.USE_REVISION_ENTITY_WITH_NATIVE_ID, "false" );
		}
		if ( auditStrategy != null && !"".equals( auditStrategy ) ) {
			configurationProperties.setProperty( "org.hibernate.envers.audit_strategy", auditStrategy );
		}

		addConfigurationProperties( configurationProperties );

		configurationProperties.put( AvailableSettings.LOADED_CLASSES, Arrays.asList( getAnnotatedClasses() ) );

		entityManagerFactoryBuilder = (EntityManagerFactoryBuilderImpl) Bootstrap.getEntityManagerFactoryBuilder(
				new PersistenceUnitDescriptorAdapter(),
				configurationProperties
		);

		emf = entityManagerFactoryBuilder.build().unwrap( HibernateEntityManagerFactory.class );

		serviceRegistry = (StandardServiceRegistryImpl) emf.getSessionFactory()
				.getServiceRegistry()
				.getParentServiceRegistry();

		newEntityManager();
	}

	protected Class[] getAnnotatedClasses() {
		return new Class[0];
	}

	protected boolean createSchema() {
		return true;
	}

	private BootstrapServiceRegistryBuilder createBootstrapRegistryBuilder() {
		return new BootstrapServiceRegistryBuilder();
	}

	@AfterClassOnce
	public void close() {
		closeEntityManager();
		emf.close();
		//NOTE we don't build the service registry so we don't destroy it
	}

	public EntityManager getEntityManager() {
		return entityManager;
	}

	public AuditReader getAuditReader() {
		return auditReader;
	}
}
