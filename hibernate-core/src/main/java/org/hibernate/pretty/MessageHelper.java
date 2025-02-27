/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.pretty;

import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;

/**
 * MessageHelper methods for rendering log messages relating to managed
 * entities and collections typically used in log statements and exception
 * messages.
 *
 * @author Max Andersen, Gavin King
 */
public final class MessageHelper {

	private MessageHelper() {
	}


	// entities ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Generate an info message string relating to a particular entity,
	 * based on the given entityName and id.
	 *
	 * @param entityName The defined entity name.
	 * @param id The entity id value.
	 * @return An info string, in the form [FooBar#1].
	 */
	public static String infoString(String entityName, Object id) {
		StringBuilder s = new StringBuilder();
		s.append( '[' );
		if( entityName == null ) {
			s.append( "<null entity name>" );
		}
		else {
			s.append( entityName );
		}
		s.append( '#' );

		if ( id == null ) {
			s.append( "<null>" );
		}
		else {
			s.append( id );
		}
		s.append( ']' );

		return s.toString();
	}

	/**
	 * Generate an info message string relating to a particular entity.
	 *
	 * @param persister The persister for the entity
	 * @param id The entity id value
	 * @param factory The session factory - Could be null!
	 * @return An info string, in the form [FooBar#1]
	 */
	public static String infoString(
			EntityPersister persister,
			Object id, 
			SessionFactoryImplementor factory) {
		StringBuilder s = new StringBuilder();
		s.append( '[' );
		Type idType;
		if( persister == null ) {
			s.append( "<null EntityPersister>" );
			idType = null;
		}
		else {
			s.append( persister.getEntityName() );
			idType = persister.getIdentifierType();
		}
		s.append( '#' );

		if ( id == null ) {
			s.append( "<null>" );
		}
		else {
			if ( idType == null ) {
				s.append( id );
			}
			else {
				if ( factory != null ) {
					s.append( idType.toLoggableString( id, factory ) );
				}
				else {
					s.append( "<not loggable>" );
				}
			}
		}
		s.append( ']' );

		return s.toString();

	}

	/**
	 * Generate an info message string relating to a particular entity,.
	 *
	 * @param persister The persister for the entity
	 * @param id The entity id value
	 * @param identifierType The entity identifier type mapping
	 * @param factory The session factory
	 * @return An info string, in the form [FooBar#1]
	 */
	public static String infoString(
			EntityPersister persister, 
			Object id, 
			Type identifierType,
			SessionFactoryImplementor factory) {
		StringBuilder s = new StringBuilder();
		s.append( '[' );
		if( persister == null ) {
			s.append( "<null EntityPersister>" );
		}
		else {
			s.append( persister.getEntityName() );
		}
		s.append( '#' );

		if ( id == null ) {
			s.append( "<null>" );
		}
		else {
			s.append( identifierType.toLoggableString( id, factory ) );
		}
		s.append( ']' );

		return s.toString();
	}

	/**
	 * Generate an info message string relating to a series of entities.
	 *
	 * @param persister The persister for the entities
	 * @param ids The entity id values
	 * @param factory The session factory
	 * @return An info string, in the form [FooBar#<1,2,3>]
	 */
	public static String infoString(
			EntityPersister persister,
			Object[] ids,
			SessionFactoryImplementor factory) {
		StringBuilder s = new StringBuilder();
		s.append( '[' );
		if( persister == null ) {
			s.append( "<null EntityPersister>" );
		}
		else {
			s.append( persister.getEntityName() );
			s.append( "#<" );
			for ( int i=0; i<ids.length; i++ ) {
				s.append( persister.getIdentifierType().toLoggableString( ids[i], factory ) );
				if ( i < ids.length-1 ) {
					s.append( ", " );
				}
			}
			s.append( '>' );
		}
		s.append( ']' );

		return s.toString();

	}

	/**
	 * Generate an info message string relating to given entity persister.
	 *
	 * @param persister The persister.
	 * @return An info string, in the form [FooBar]
	 */
	public static String infoString(EntityPersister persister) {
		StringBuilder s = new StringBuilder();
		s.append( '[' );
		if ( persister == null ) {
			s.append( "<null EntityPersister>" );
		}
		else {
			s.append( persister.getEntityName() );
		}
		s.append( ']' );
		return s.toString();
	}

	/**
	 * Generate an info message string relating to a given property value
	 * for an entity.
	 *
	 * @param entityName The entity name
	 * @param propertyName The name of the property
	 * @param key The property value.
	 * @return An info string, in the form [Foo.bars#1]
	 */
	public static String infoString(String entityName, String propertyName, Object key) {
		StringBuilder s = new StringBuilder()
				.append( '[' )
				.append( entityName )
				.append( '.' )
				.append( propertyName )
				.append( '#' );

		if ( key == null ) {
			s.append( "<null>" );
		}
		else {
			s.append( key );
		}
		s.append( ']' );
		return s.toString();
	}


	// collections ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	
	/**
	 * Generate an info message string relating to a particular managed
	 * collection.  Attempts to intelligently handle property-refs issues
	 * where the collection key is not the same as the owner key.
	 *
	 * @param persister The persister for the collection
	 * @param collection The collection itself
	 * @param collectionKey The collection key
	 * @param session The session
	 * @return An info string, in the form [Foo.bars#1]
	 */
	public static String collectionInfoString(
			CollectionPersister persister,
			PersistentCollection collection,
			Object collectionKey,
			SharedSessionContractImplementor session ) {
		
		StringBuilder s = new StringBuilder();
		s.append( '[' );
		if ( persister == null ) {
			s.append( "<unreferenced>" );
		}
		else {
			s.append( persister.getRole() );
			s.append( '#' );
			
			Type ownerIdentifierType = persister.getOwnerEntityPersister()
					.getIdentifierType();
			Object ownerKey;
			// TODO: Is it redundant to attempt to use the collectionKey,
			// or is always using the owner id sufficient?
			if ( collectionKey.getClass().isAssignableFrom( 
					ownerIdentifierType.getReturnedClass() ) ) {
				ownerKey = collectionKey;
			}
			else {
				Object collectionOwner = collection == null ? null : collection.getOwner();
				EntityEntry entry = collectionOwner == null ? null : session.getPersistenceContextInternal().getEntry(collectionOwner);
				ownerKey = entry == null ? null : entry.getId();
			}
			s.append( ownerIdentifierType.toLoggableString( 
					ownerKey, session.getFactory() ) );
		}
		s.append( ']' );

		return s.toString();
	}

	/**
	 * Generate an info message string relating to a series of managed
	 * collections.
	 *
	 * @param persister The persister for the collections
	 * @param ids The id values of the owners
	 * @param factory The session factory
	 * @return An info string, in the form [Foo.bars#<1,2,3>]
	 */
	public static String collectionInfoString(
			CollectionPersister persister,
			Object[] ids,
			SessionFactoryImplementor factory) {
		StringBuilder s = new StringBuilder();
		s.append( '[' );
		if ( persister == null ) {
			s.append( "<unreferenced>" );
		}
		else {
			s.append( persister.getRole() );
			s.append( "#<" );
			for ( int i = 0; i < ids.length; i++ ) {
				addIdToCollectionInfoString( persister, ids[i], factory, s );
				if ( i < ids.length-1 ) {
					s.append( ", " );
				}
			}
			s.append( '>' );
		}
		s.append( ']' );
		return s.toString();
	}

	/**
	 * Generate an info message string relating to a particular managed
	 * collection.
	 *
	 * @param persister The persister for the collection
	 * @param id The id value of the owner
	 * @param factory The session factory
	 * @return An info string, in the form [Foo.bars#1]
	 */
	public static String collectionInfoString(
			CollectionPersister persister,
			Object id,
			SessionFactoryImplementor factory) {
		StringBuilder s = new StringBuilder();
		s.append( '[' );
		if ( persister == null ) {
			s.append( "<unreferenced>" );
		}
		else {
			s.append( persister.getRole() );
			s.append( '#' );

			if ( id == null ) {
				s.append( "<null>" );
			}
			else {
				addIdToCollectionInfoString( persister, id, factory, s );
			}
		}
		s.append( ']' );

		return s.toString();
	}
	
	private static void addIdToCollectionInfoString(
			CollectionPersister persister,
			Object id,
			SessionFactoryImplementor factory,
			StringBuilder s ) {
		// Need to use the identifier type of the collection owner
		// since the incoming value is actually the owner's id.
		// Using the collection's key type causes problems with
		// property-ref keys.
		// Also need to check that the expected identifier type matches
		// the given ID.  Due to property-ref keys, the collection key
		// may not be the owner key.
		Type ownerIdentifierType = persister.getOwnerEntityPersister()
				.getIdentifierType();
		if ( id.getClass().isAssignableFrom( 
				ownerIdentifierType.getReturnedClass() ) ) {
			s.append( ownerIdentifierType.toLoggableString( id, factory ) );
		}
		else {
			// TODO: This is a crappy backup if a property-ref is used.
			// If the reference is an object w/o toString(), this isn't going to work.
			s.append( id.toString() );
		}
	}

	/**
	 * Generate an info message string relating to a particular managed
	 * collection.
	 *
	 * @param role The role-name of the collection
	 * @param id The id value of the owner
	 * @return An info string, in the form [Foo.bars#1]
	 */
	public static String collectionInfoString(String role, Object id) {
		StringBuilder s = new StringBuilder();
		s.append( '[' );
		if( role == null ) {
			s.append( "<unreferenced>" );
		}
		else {
			s.append( role );
			s.append( '#' );

			if ( id == null ) {
				s.append( "<null>" );
			}
			else {
				s.append( id );
			}
		}
		s.append( ']' );
		return s.toString();
	}

}
