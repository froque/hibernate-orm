/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.batchfetch;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.engine.spi.BatchFetchQueue;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Gail Badner
 * @author Stephen Fikes
 */
@DomainModel(
		annotatedClasses = {
				BatchFetchNotFoundIgnoreDynamicStyleTest.Employee.class,
				BatchFetchNotFoundIgnoreDynamicStyleTest.Task.class
		}
)
@SessionFactory(
		statementInspectorClass = BatchFetchNotFoundIgnoreDynamicStyleTest.AStatementInspector.class
)
public class BatchFetchNotFoundIgnoreDynamicStyleTest {
	private static final int NUMBER_OF_EMPLOYEES = 8;

	private List<Task> tasks = new ArrayList<>();


	@BeforeEach
	public void createData(SessionFactoryScope scope) {
		tasks.clear();
		tasks = scope.fromTransaction(
				session -> {
					for ( int i = 0; i < NUMBER_OF_EMPLOYEES; i++ ) {
						Task task = new Task();
						task.id = i;
						tasks.add( task );
						session.persist( task );
						Employee e = new Employee( "employee0" + i );
						e.task = task;
						session.persist( e );
					}
					return tasks;
				}
		);
	}

	@AfterEach
	public void deleteData(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery( "delete from Task" ).executeUpdate();
					session.createQuery( "delete from Employee" ).executeUpdate();
				}
		);
	}

	@Test
	public void testSeveralNotFoundFromQuery(SessionFactoryScope scope) {

		scope.inTransaction(
				session -> {
					// delete 2nd and 8th Task so that the non-found Task entities will be queried
					// in 2 different batches.
					session.delete( tasks.get( 1 ) );
					session.delete( tasks.get( 7 ) );
				}
		);

		final AStatementInspector statementInspector = (AStatementInspector) scope.getStatementInspector();
		statementInspector.clear();

		final List<Employee> employees = scope.fromTransaction(
				session -> {
					List<Employee> results =
							session.createQuery( "from Employee e order by e.id", Employee.class ).getResultList();
					for ( Task task : tasks ) {
						checkInBatchFetchQueue( task.id, session, false );
					}
					return results;
				}
		);

		final List<Integer> paramterCounts = statementInspector.parameterCounts;

		// there should be 4 SQL statements executed
		assertEquals( 4, paramterCounts.size() );

		// query loading Employee entities shouldn't have any parameters
		assertEquals( 0, paramterCounts.get( 0 ).intValue() );

		// query specifically for Task with ID == 0 will result in 1st batch;
		// query should have 5 parameters for [0,1,2,3,4];
		// Task with ID == 1 won't be found; the rest will be found.
		assertEquals( 5, paramterCounts.get( 1 ).intValue() );

		// query specifically for Task with ID == 1 will result in 2nd batch;
		// query should have 4 parameters [1,5,6,7];
		// Task with IDs == [1,7] won't be found; the rest will be found.
		assertEquals( 4, paramterCounts.get( 2 ).intValue() );

		// no extra queries required to load entities with IDs [2,3,4] because they
		// were already loaded from 1st batch

		// no extra queries required to load entities with IDs [5,6] because they
		// were already loaded from 2nd batch

		// query specifically for Task with ID == 7 will result in just querying
		// Task with ID == 7 (because the batch is empty).
		// query should have 1 parameter [7];
		// Task with ID == 7 won't be found.
		assertEquals( 1, paramterCounts.get( 3 ).intValue() );

		assertEquals( NUMBER_OF_EMPLOYEES, employees.size() );
		for ( int i = 0; i < NUMBER_OF_EMPLOYEES; i++ ) {
			if ( i == 1 || i == 7 ) {
				assertNull( employees.get( i ).task );
			}
			else {
				assertEquals( tasks.get( i ).id, employees.get( i ).task.id );
			}
		}
	}

	@Test
	public void testMostNotFoundFromQuery(SessionFactoryScope scope) {

		scope.inTransaction(
				session -> {
					// delete all but last Task entity
					for ( int i = 0; i < 7; i++ ) {
						session.delete( tasks.get( i ) );
					}
				}
		);

		final AStatementInspector statementInspector = (AStatementInspector) scope.getStatementInspector();
		statementInspector.clear();

		final List<Employee> employees = scope.fromTransaction(
				session -> {
					List<Employee> results =
							session.createQuery( "from Employee e order by e.id", Employee.class ).getResultList();
					for ( Task task : tasks ) {
						checkInBatchFetchQueue( task.id, session, false );
					}
					return results;
				}
		);

		final List<Integer> paramterCounts = statementInspector.parameterCounts;

		// there should be 8 SQL statements executed
		assertEquals( 8, paramterCounts.size() );

		// query loading Employee entities shouldn't have any parameters
		assertEquals( 0, paramterCounts.get( 0 ).intValue() );

		// query specifically for Task with ID == 0 will result in 1st batch;
		// query should have 5 parameters for [0,1,2,3,4];
		// Task with IDs == [0,1,2,3,4] won't be found
		assertEquals( 5, paramterCounts.get( 1 ).intValue() );

		// query specifically for Task with ID == 1 will result in 2nd batch;
		// query should have 4 parameters [1,5,6,7];
		// Task with IDs == [1,5,6] won't be found; Task with ID == 7 will be found.
		assertEquals( 4, paramterCounts.get( 2 ).intValue() );

		// query specifically for Task with ID == 2 will result in just querying
		// Task with ID == 2 (because the batch is empty).
		// query should have 1 parameter [2];
		// Task with ID == 2 won't be found.
		assertEquals( 1, paramterCounts.get( 3 ).intValue() );

		// query specifically for Task with ID == 3 will result in just querying
		// Task with ID == 3 (because the batch is empty).
		// query should have 1 parameter [3];
		// Task with ID == 3 won't be found.
		assertEquals( 1, paramterCounts.get( 4 ).intValue() );

		// query specifically for Task with ID == 4 will result in just querying
		// Task with ID == 4 (because the batch is empty).
		// query should have 1 parameter [4];
		// Task with ID == 4 won't be found.
		assertEquals( 1, paramterCounts.get( 5 ).intValue() );

		// query specifically for Task with ID == 5 will result in just querying
		// Task with ID == 5 (because the batch is empty).
		// query should have 1 parameter [5];
		// Task with ID == 5 won't be found.
		assertEquals( 1, paramterCounts.get( 6 ).intValue() );

		// query specifically for Task with ID == 6 will result in just querying
		// Task with ID == 6 (because the batch is empty).
		// query should have 1 parameter [6];
		// Task with ID == 6 won't be found.
		assertEquals( 1, paramterCounts.get( 7 ).intValue() );

		// no extra queries required to load entity with ID == 7 because it
		// was already loaded from 2nd batch

		assertEquals( NUMBER_OF_EMPLOYEES, employees.size() );

		for ( int i = 0; i < NUMBER_OF_EMPLOYEES; i++ ) {
			if ( i == 7 ) {
				assertEquals( tasks.get( i ).id, employees.get( i ).task.id );
			}
			else {
				assertNull( employees.get( i ).task );
			}
		}
	}

	@Test
	public void testNotFoundFromGet(SessionFactoryScope scope) {

		scope.inTransaction(
				session -> {
					// delete task so it is not found later when getting the Employee.
					session.delete( tasks.get( 0 ) );
				}
		);

		final AStatementInspector statementInspector = (AStatementInspector) scope.getStatementInspector();
		statementInspector.clear();

		scope.inTransaction(
				session -> {
					Employee employee = session.get( Employee.class, "employee00" );
					checkInBatchFetchQueue( tasks.get( 0 ).id, session, false );
					assertNotNull( employee );
					assertNull( employee.task );
				}
		);

		final List<Integer> paramterCounts = statementInspector.parameterCounts;

		// there should be 1 SQL statements executed, we select the tasks with a join
		// 1) query to load Employee entity by ID  and fetch the Tasks
		assertEquals( 1, paramterCounts.size() );

		// query loading Employee entities shouldn't have any parameters
		assertEquals( 1, paramterCounts.get( 0 ).intValue() );

//		// Will result in just querying a single Task (because the batch is empty).
//		// query should have 1 parameter;
//		// Task won't be found.
//		assertEquals( 1, paramterCounts.get( 1 ).intValue() );
	}

	private static void checkInBatchFetchQueue(long id, Session session, boolean expected) {
		final SessionImplementor sessionImplementor = (SessionImplementor) session;
		final EntityPersister persister =
				sessionImplementor.getFactory().getMetamodel().entityPersister( Task.class );
		final BatchFetchQueue batchFetchQueue =
				sessionImplementor.getPersistenceContextInternal().getBatchFetchQueue();
		assertThat(
				"Checking BatchFetchQueue for entry for Task#" + id,
				batchFetchQueue.containsEntityKey( new EntityKey( id, persister ) ),
				is( expected )
		);
	}

	@Entity(name = "Employee")
	public static class Employee {
		@Id
		private String name;

		@OneToOne
		@JoinColumn(foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
		@NotFound(action = NotFoundAction.IGNORE)
		private Task task;

		private Employee() {
		}

		private Employee(String name) {
			this.name = name;
		}
	}

	@Entity(name = "Task")
	@BatchSize(size = 5)
	public static class Task {
		@Id
		private long id;

		public Task() {
		}
	}

	public static class AStatementInspector implements StatementInspector {
		private List<Integer> parameterCounts = new ArrayList<>();

		public AStatementInspector() {
		}

		public String inspect(String sql) {
			parameterCounts.add( countParameters( sql ) );
			return sql;
		}

		private void clear() {
			parameterCounts.clear();
		}

		private int countParameters(String sql) {
			int count = 0;
			int parameterIndex = sql.indexOf( '?' );
			while ( parameterIndex >= 0 ) {
				count++;
				parameterIndex = sql.indexOf( '?', parameterIndex + 1 );
			}
			return count;
		}
	}

}
