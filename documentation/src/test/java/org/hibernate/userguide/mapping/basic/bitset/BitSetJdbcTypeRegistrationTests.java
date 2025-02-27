/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.userguide.mapping.basic.bitset;

import java.sql.Types;
import java.util.BitSet;

import org.hibernate.annotations.JdbcTypeRegistration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.internal.BasicAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.userguide.mapping.basic.CustomBinaryJdbcType;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = BitSetJdbcTypeRegistrationTests.Product.class )
@SessionFactory
public class BitSetJdbcTypeRegistrationTests {

	@Test
	public void verifyMappings(SessionFactoryScope scope) {
		final SessionFactoryImplementor sessionFactory = scope.getSessionFactory();
		final MappingMetamodel domainModel = sessionFactory.getDomainModel();

		final EntityPersister entityDescriptor = domainModel.findEntityDescriptor( Product.class );
		final BasicAttributeMapping attributeMapping = (BasicAttributeMapping) entityDescriptor.findAttributeMapping( "bitSet" );

		assertThat( attributeMapping.getJavaTypeDescriptor().getJavaTypeClass(), equalTo( BitSet.class ) );

		assertThat( attributeMapping.getValueConverter(), nullValue() );

		assertThat(
				attributeMapping.getJdbcMapping().getJdbcTypeDescriptor().getJdbcTypeCode(),
				is( Types.VARBINARY )
		);

		assertThat( attributeMapping.getJdbcMapping().getJavaTypeDescriptor().getJavaTypeClass(), equalTo( BitSet.class ) );

		scope.inTransaction(
				(session) -> {
					session.persist( new Product( 1, BitSet.valueOf( BitSetHelper.BYTES ) ) );
				}
		);

		scope.inSession(
				(session) -> {
					final Product product = session.get( Product.class, 1 );
					assertThat( product.getBitSet(), equalTo( BitSet.valueOf( BitSetHelper.BYTES ) ) );
				}
		);
	}

	@AfterEach
	public void dropData(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> session.createQuery( "delete Product" ).executeUpdate()
		);
	}


	@Table(name = "Product")
	//tag::basic-bitset-example-jdbc-type-global[]
	@Entity(name = "Product")
	@JdbcTypeRegistration( CustomBinaryJdbcType.class )
	public static class Product {
		@Id
		private Integer id;

		private BitSet bitSet;

		//Constructors, getters, and setters are omitted for brevity
		//end::basic-bitset-example-jdbc-type-global[]
		public Product() {
		}

		public Product(Number id, BitSet bitSet) {
			this.id = id.intValue();
			this.bitSet = bitSet;
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public BitSet getBitSet() {
			return bitSet;
		}

		public void setBitSet(BitSet bitSet) {
			this.bitSet = bitSet;
		}
		//tag::basic-bitset-example-jdbc-type-global[]
	}
	//end::basic-bitset-example-jdbc-type-global[]
}
