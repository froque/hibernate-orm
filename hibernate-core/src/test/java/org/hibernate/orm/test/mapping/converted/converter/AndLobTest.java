/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.converted.converter;

import java.sql.Types;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.converter.AttributeConverterTypeAdapter;

import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hibernate.testing.junit4.ExtraAssertions.assertTyping;
import static org.junit.Assert.assertThat;

/**
 * Test mapping a model with an attribute combining {@code @Lob} with an AttributeConverter.
 * <p/>
 * Originally developed to diagnose HHH-9615
 *
 * @author Steve Ebersole
 */
@ServiceRegistry
public class AndLobTest {
	@Test
	public void testMappingAttributeWithLobAndAttributeConverter(ServiceRegistryScope scope) {
		final Metadata metadata = new MetadataSources( scope.getRegistry() )
				.addAnnotatedClass( EntityImpl.class )
				.buildMetadata();

		final Type type = metadata.getEntityBinding( EntityImpl.class.getName() ).getProperty( "status" ).getType();
		final AttributeConverterTypeAdapter typeAdapter = assertTyping( AttributeConverterTypeAdapter.class, type );

		assertThat( typeAdapter.getDomainJtd().getJavaTypeClass(), equalTo( String.class ) );
		assertThat( typeAdapter.getRelationalJtd().getJavaTypeClass(), equalTo( Integer.class ) );
		assertThat( typeAdapter.getJdbcTypeDescriptor().getJdbcTypeCode(), is( Types.INTEGER ) );
	}

	@Converter
	public static class ConverterImpl implements AttributeConverter<String, Integer> {
		@Override
		public Integer convertToDatabaseColumn(String attribute) {
			return attribute.length();
		}

		@Override
		public String convertToEntityAttribute(Integer dbData) {
			return "";
		}
	}

	@Entity
	public static class EntityImpl {
		@Id
		private Integer id;

		@Lob
		@Convert(converter = ConverterImpl.class)
		private String status;
	}
}
