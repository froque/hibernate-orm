/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate;

/**
 * A command-oriented API for performing bulk operations against a database.
 * <p/>
 * A stateless session does not implement a first-level cache nor interact
 * with any second-level cache, nor does it implement transactional
 * write-behind or automatic dirty checking, nor do operations cascade to
 * associated instances. Collections are ignored by a stateless session.
 * Operations performed via a stateless session bypass Hibernate's event model
 * and interceptors.  Stateless sessions are vulnerable to data aliasing
 * effects, due to the lack of a first-level cache.
 * <p/>
 * For certain kinds of transactions, a stateless session may perform slightly
 * faster than a stateful session.
 *
 * @author Gavin King
 */
public interface StatelessSession extends SharedSessionContract {
	/**
	 * Close the stateless session and release the JDBC connection.
	 */
	void close();

	/**
	 * Insert a row.
	 *
	 * @param entity a new transient instance
	 *
	 * @return The identifier of the inserted entity
	 */
	Object insert(Object entity);

	/**
	 * Insert a row.
	 *
	 * @param entityName The entityName for the entity to be inserted
	 * @param entity a new transient instance
	 *
	 * @return the identifier of the instance
	 */
	Object insert(String entityName, Object entity);

	/**
	 * Update a row.
	 *
	 * @param entity a detached entity instance
	 */
	void update(Object entity);

	/**
	 * Update a row.
	 *
	 * @param entityName The entityName for the entity to be updated
	 * @param entity a detached entity instance
	 */
	void update(String entityName, Object entity);

	/**
	 * Delete a row.
	 *
	 * @param entity a detached entity instance
	 */
	void delete(Object entity);

	/**
	 * Delete a row.
	 *
	 * @param entityName The entityName for the entity to be deleted
	 * @param entity a detached entity instance
	 */
	void delete(String entityName, Object entity);

	/**
	 * Retrieve a row.
	 *
	 * @param entityName The name of the entity to retrieve
	 * @param id The id of the entity to retrieve
	 *
	 * @return a detached entity instance
	 */
	Object get(String entityName, Object id);

	/**
	 * Retrieve a row.
	 *
	 * @param entityClass The class of the entity to retrieve
	 * @param id The id of the entity to retrieve
	 *
	 * @return a detached entity instance
	 */
	<T> T get(Class<T> entityClass, Object id);

	/**
	 * Retrieve a row, obtaining the specified lock mode.
	 *
	 * @param entityName The name of the entity to retrieve
	 * @param id The id of the entity to retrieve
	 * @param lockMode The lock mode to apply to the entity
	 *
	 * @return a detached entity instance
	 */
	Object get(String entityName, Object id, LockMode lockMode);

	/**
	 * Retrieve a row, obtaining the specified lock mode.
	 *
	 * @param entityClass The class of the entity to retrieve
	 * @param id The id of the entity to retrieve
	 * @param lockMode The lock mode to apply to the entity
	 *
	 * @return a detached entity instance
	 */
	<T> T get(Class<T> entityClass, Object id, LockMode lockMode);

	/**
	 * Refresh the entity instance state from the database.
	 *
	 * @param entity The entity to be refreshed.
	 */
	void refresh(Object entity);

	/**
	 * Refresh the entity instance state from the database.
	 *
	 * @param entityName The entityName for the entity to be refreshed.
	 * @param entity The entity to be refreshed.
	 */
	void refresh(String entityName, Object entity);

	/**
	 * Refresh the entity instance state from the database.
	 *
	 * @param entity The entity to be refreshed.
	 * @param lockMode The LockMode to be applied.
	 */
	void refresh(Object entity, LockMode lockMode);

	/**
	 * Refresh the entity instance state from the database.
	 *
	 * @param entityName The entityName for the entity to be refreshed.
	 * @param entity The entity to be refreshed.
	 * @param lockMode The LockMode to be applied.
	 */
	void refresh(String entityName, Object entity, LockMode lockMode);
}
