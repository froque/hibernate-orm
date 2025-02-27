/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.userguide.mapping.basic;

import java.math.BigDecimal;
import java.sql.Types;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.internal.BasicAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Tests for mapping `BigInteger` values
 *
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = BigDecimalMappingTests.EntityOfBigDecimals.class )
@SessionFactory
public class BigDecimalMappingTests {

	@Test
	public void testMappings(SessionFactoryScope scope) {
		// first, verify the type selections...
		final MappingMetamodel domainModel = scope.getSessionFactory().getDomainModel();
		final EntityPersister entityDescriptor = domainModel.findEntityDescriptor( EntityOfBigDecimals.class );
		final JdbcTypeRegistry jdbcTypeRegistry = domainModel.getTypeConfiguration()
				.getJdbcTypeDescriptorRegistry();

		{
			final BasicAttributeMapping attribute = (BasicAttributeMapping) entityDescriptor.findAttributeMapping( "wrapper" );
			assertThat( attribute.getJavaTypeDescriptor().getJavaTypeClass(), equalTo( BigDecimal.class ) );

			final JdbcMapping jdbcMapping = attribute.getJdbcMapping();
			assertThat( jdbcMapping.getJavaTypeDescriptor().getJavaTypeClass(), equalTo( BigDecimal.class ) );
			assertThat( jdbcMapping.getJdbcTypeDescriptor(), is( jdbcTypeRegistry.getDescriptor( Types.NUMERIC ) ) );
		}


		// and try to use the mapping
		scope.inTransaction(
				(session) -> session.persist( new EntityOfBigDecimals( 1, BigDecimal.TEN ) )
		);
		scope.inTransaction(
				(session) -> session.get( EntityOfBigDecimals.class, 1 )
		);
	}

	@AfterEach
	public void dropData(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> session.createQuery( "delete EntityOfBigDecimals" ).executeUpdate()
		);
	}

	@Entity( name = "EntityOfBigDecimals" )
	@Table( name = "EntityOfBigDecimals" )
	public static class EntityOfBigDecimals {
		@Id
		Integer id;

		//tag::basic-bigdecimal-example-implicit[]
		// will be mapped using NUMERIC
		BigDecimal wrapper;
		//end::basic-bigdecimal-example-implicit[]

		public EntityOfBigDecimals() {
		}

		public EntityOfBigDecimals(Integer id, BigDecimal wrapper) {
			this.id = id;
			this.wrapper = wrapper;
		}
	}
}
