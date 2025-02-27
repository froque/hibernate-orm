/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2014, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.orm.test.collection.multisession;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.collection.internal.AbstractPersistentCollection;
import org.hibernate.collection.internal.PersistentSet;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.CollectionEntry;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.Logger;
import org.hibernate.testing.orm.junit.LoggingInspections;
import org.hibernate.testing.orm.junit.LoggingInspectionsScope;
import org.hibernate.testing.orm.junit.MessageKeyWatcher;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Gail Badner
 */
@DomainModel(
		annotatedClasses = {
				MultipleSessionCollectionWarningTest.Parent.class,
				MultipleSessionCollectionWarningTest.Child.class
		}
)
@SessionFactory
@LoggingInspections(
		messages = {
				@LoggingInspections.Message(
						messageKey = "HHH000470",
						loggers = @Logger( loggerNameClass = AbstractPersistentCollection.class )
				),
				@LoggingInspections.Message(
						messageKey = "HHH000471",
						loggers = @Logger( loggerNameClass = AbstractPersistentCollection.class )
				)
		}
)
public class MultipleSessionCollectionWarningTest {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( AbstractPersistentCollection.class );

	@Test
	@TestForIssue(jiraKey = "HHH-9518")
	public void testSetCurrentSessionOverwritesNonConnectedSession(
			SessionFactoryScope scope,
			LoggingInspectionsScope loggingScope) {
		Parent p = new Parent();
		Child c = new Child();
		p.children.add( c );

		scope.inSession(
				s1 -> {
					s1.getTransaction().begin();
					try {
						s1.saveOrUpdate( p );

						// Now remove the collection from the PersistenceContext without unsetting its session
						// This should never be done in practice; it is done here only to test that the warning
						// gets logged. s1 will not function properly so the transaction will ultimately need
						// to be rolled-back.

						CollectionEntry ce = s1.getPersistenceContext()
								.removeCollectionEntry( (PersistentSet) p.children );
						assertNotNull( ce );

						// the collection session should still be s1; the collection is no longer "connected" because its
						// CollectionEntry has been removed.
						assertSame( s1, ( (AbstractPersistentCollection) p.children ).getSession() );

						scope.inSession(
								s2 -> {
									s2.getTransaction().begin();
									try {
										final MessageKeyWatcher watcher = loggingScope.getWatcher( "HHH000470", AbstractPersistentCollection.class );
										assertFalse( watcher.wasTriggered() );

										// The following should trigger warning because we're setting a new session when the collection already
										// has a non-null session (and the collection is not "connected" to that session);
										// Since s1 was not flushed, the collection role will not be known (no way to test that other than inspection).
										s2.saveOrUpdate( p );

										assertTrue( watcher.wasTriggered() );

										// collection's session should be overwritten with s2
										assertSame( s2, ( (AbstractPersistentCollection) p.children ).getSession() );
									}
									finally {
										s2.getTransaction().rollback();
									}
								}
						);
					}
					finally {
						s1.getTransaction().rollback();
					}
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-9518")
	public void testSetCurrentSessionOverwritesNonConnectedSessionFlushed(
			SessionFactoryScope scope,
			LoggingInspectionsScope loggingScope) {
		Parent p = new Parent();
		Child c = new Child();
		p.children.add( c );

		scope.inSession(
				s1 -> {
					s1.getTransaction().begin();
					try {
						s1.saveOrUpdate( p );

						// flush the session so that p.children will contain its role
						s1.flush();

						// Now remove the collection from the PersistenceContext without unsetting its session
						// This should never be done in practice; it is done here only to test that the warning
						// gets logged. s1 will not function properly so the transaction will ultimately need
						// to be rolled-back.

						CollectionEntry ce = s1.getPersistenceContext()
								.removeCollectionEntry( (PersistentSet) p.children );
						assertNotNull( ce );

						// the collection session should still be s1; the collection is no longer "connected" because its
						// CollectionEntry has been removed.
						assertSame( s1, ( (AbstractPersistentCollection) p.children ).getSession() );

						scope.inSession(
								s2 -> {
									s2.getTransaction().begin();
									try {
										final MessageKeyWatcher watcher = loggingScope.getWatcher( "HHH000470", AbstractPersistentCollection.class );
										assertFalse( watcher.wasTriggered() );

										// The following should trigger warning because we're setting a new session when the collection already
										// has a non-null session (and the collection is not "connected" to that session);
										// The collection role and key should be included in the message (no way to test that other than inspection).
										s2.saveOrUpdate( p );

										assertTrue( watcher.wasTriggered() );

										// collection's session should be overwritten with s2
										assertSame( s2, ( (AbstractPersistentCollection) p.children ).getSession() );
									}
									finally {
										s2.getTransaction().rollback();
									}
								}
						);
					}
					finally {
						s1.getTransaction().rollback();
					}
				}

		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-9518")
	public void testUnsetSessionCannotOverwriteNonConnectedSession(
			SessionFactoryScope scope,
			LoggingInspectionsScope loggingScope) {
		Parent p = new Parent();
		Child c = new Child();
		p.children.add( c );

		scope.inSession(
				s1 -> {
					s1.getTransaction().begin();
					try {
						s1.saveOrUpdate( p );

						// Now remove the collection from the PersistenceContext without unsetting its session
						// This should never be done in practice; it is done here only to test that the warning
						// gets logged. s1 will not function properly so the transaction will ultimately need
						// to be rolled-back.

						CollectionEntry ce = s1.getPersistenceContext()
								.removeCollectionEntry( (PersistentSet) p.children );
						assertNotNull( ce );

						// the collection session should still be s1; the collection is no longer "connected" because its
						// CollectionEntry has been removed.
						assertSame( s1, ( (AbstractPersistentCollection) p.children ).getSession() );

						scope.inSession(
								s2 -> {
									s2.getTransaction().begin();
									try {
										final MessageKeyWatcher watcher = loggingScope.getWatcher( "HHH000471", AbstractPersistentCollection.class );
										assertFalse( watcher.wasTriggered() );

										// The following should trigger warning because we're unsetting a different session.
										// We should not do this in practice; it is done here only to force the warning.
										// Since s1 was not flushed, the collection role will not be known (no way to test that).
										assertFalse( ( (PersistentCollection) p.children ).unsetSession( s2 ) );

										assertTrue( watcher.wasTriggered() );

										// collection's session should still be s1
										assertSame( s1, ( (AbstractPersistentCollection) p.children ).getSession() );
									}
									finally {
										s2.getTransaction().rollback();
									}

								}

						);
					}
					finally {
						s1.getTransaction().rollback();
					}
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-9518")
	public void testUnsetSessionCannotOverwriteConnectedSession(
			SessionFactoryScope scope,
			LoggingInspectionsScope loggingScope) {
		Parent p = new Parent();
		Child c = new Child();
		p.children.add( c );

		scope.inSession(
				s1 -> {
					s1.getTransaction().begin();
					try {
						s1.saveOrUpdate( p );

						// The collection is "connected" to s1 because it contains the CollectionEntry
						CollectionEntry ce = s1.getPersistenceContext()
								.getCollectionEntry( (PersistentCollection) p.children );
						assertNotNull( ce );

						// the collection session should be s1
						assertSame( s1, ( (AbstractPersistentCollection) p.children ).getSession() );

						scope.inSession(
								s2 -> {
									s2.getTransaction().begin();
									try {
										final MessageKeyWatcher watcher = loggingScope.getWatcher( "HHH000471", AbstractPersistentCollection.class );
										assertFalse( watcher.wasTriggered() );

										// The following should trigger warning because we're unsetting a different session
										// We should not do this in practice; it is done here only to force the warning.
										// Since s1 was not flushed, the collection role will not be known (no way to test that).
										assertFalse( ( (PersistentCollection) p.children ).unsetSession( s2 ) );

										assertTrue( watcher.wasTriggered() );

										// collection's session should still be s1
										assertSame( s1, ( (AbstractPersistentCollection) p.children ).getSession() );
									}
									finally {
										s2.getTransaction().rollback();
									}
								}
						);
					}
					finally {
						s1.getTransaction().rollback();
					}
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-9518")
	public void testUnsetSessionCannotOverwriteConnectedSessionFlushed(
			SessionFactoryScope scope,
			LoggingInspectionsScope loggingScope) {
		Parent p = new Parent();
		Child c = new Child();
		p.children.add( c );

		scope.inSession(
				s1 -> {
					s1.getTransaction().begin();
					try {
						s1.saveOrUpdate( p );

						// flush the session so that p.children will contain its role
						s1.flush();

						// The collection is "connected" to s1 because it contains the CollectionEntry
						CollectionEntry ce = s1.getPersistenceContext()
								.getCollectionEntry( (PersistentCollection) p.children );
						assertNotNull( ce );

						// the collection session should be s1
						assertSame( s1, ( (AbstractPersistentCollection) p.children ).getSession() );

						scope.inSession(
								s2 -> {
									s2.getTransaction().begin();
									try {
										final MessageKeyWatcher watcher = loggingScope.getWatcher( "HHH000471", AbstractPersistentCollection.class );
										assertFalse( watcher.wasTriggered() );

										// The following should trigger warning because we're unsetting a different session
										// We should not do this in practice; it is done here only to force the warning.
										// The collection role and key should be included in the message (no way to test that other than inspection).
										assertFalse( ( (PersistentCollection) p.children ).unsetSession( s2 ) );

										assertTrue( watcher.wasTriggered() );

										// collection's session should still be s1
										assertSame( s1, ( (AbstractPersistentCollection) p.children ).getSession() );

									}
									finally {
										s2.getTransaction().rollback();
									}
								}
						);
					}
					finally {
						s1.getTransaction().rollback();
					}
				}
		);
	}

	@Entity(name = "Parent")
	@Table(name = "Parent")
	public static class Parent {
		@Id
		@GeneratedValue
		private Long id;

		@OneToMany(cascade = CascadeType.ALL)
		@JoinColumn
		private Set<Child> children = new HashSet<>();
	}

	@Entity(name = "Child")
	@Table(name = "Child")
	public static class Child {
		@Id
		@GeneratedValue
		private Long id;

	}
}
