/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.namingstrategy.components;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Bag;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.BaseUnitTest;
import org.junit.jupiter.api.Test;

import static org.hibernate.testing.orm.junit.ExtraAssertions.assertTyping;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Steve Ebersole
 */
@BaseUnitTest
public class ComponentNamingStrategyTest {

	@Test
	public void testDefaultNamingStrategy() {
		final StandardServiceRegistry ssr = new StandardServiceRegistryBuilder().build();

		try {
			final MetadataSources ms = new MetadataSources( ssr );
			ms.addAnnotatedClass( Container.class ).addAnnotatedClass( Item.class );

			final Metadata metadata = ms.getMetadataBuilder()
					.applyImplicitNamingStrategy( ImplicitNamingStrategyJpaCompliantImpl.INSTANCE )
					.build();

			final PersistentClass pc = metadata.getEntityBinding( Container.class.getName() );
			Property p = pc.getProperty( "items" );
			Bag value = assertTyping( Bag.class, p.getValue() );
			SimpleValue elementValue = assertTyping( SimpleValue.class, value.getElement() );
			assertEquals( 1, elementValue.getColumnSpan() );
			Column column = assertTyping( Column.class, elementValue.getColumnIterator().next() );
			assertEquals( column.getName(), "name" );
		}
		finally {
			StandardServiceRegistryBuilder.destroy( ssr );
		}
	}

	@Test
	@TestForIssue( jiraKey = "HHH-6005" )
	public void testComponentSafeNamingStrategy() {
		final StandardServiceRegistry ssr = new StandardServiceRegistryBuilder().build();

		try {
			final MetadataSources ms = new MetadataSources( ssr );
			ms.addAnnotatedClass( Container.class ).addAnnotatedClass( Item.class );

			final Metadata metadata = ms.getMetadataBuilder()
					.applyImplicitNamingStrategy( ImplicitNamingStrategyComponentPathImpl.INSTANCE )
					.build();

			final PersistentClass pc = metadata.getEntityBinding( Container.class.getName() );
			Property p = pc.getProperty( "items" );
			Bag value = assertTyping( Bag.class, p.getValue() );
			SimpleValue elementValue = assertTyping(  SimpleValue.class, value.getElement() );
			assertEquals( 1, elementValue.getColumnSpan() );
			Column column = assertTyping( Column.class, elementValue.getColumnIterator().next() );
			assertEquals( "items_name", column.getName() );
		}
		finally {
			StandardServiceRegistryBuilder.destroy( ssr );
		}
	}
}
