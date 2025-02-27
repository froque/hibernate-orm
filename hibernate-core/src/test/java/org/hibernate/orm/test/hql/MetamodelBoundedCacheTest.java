/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.hql;

import java.lang.reflect.Field;
import java.util.Map;

import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.model.domain.JpaMetamodel;
import org.hibernate.metamodel.model.domain.internal.JpaMetamodelImpl;
import org.hibernate.metamodel.model.domain.internal.MappingMetamodelImpl;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.junit4.BaseNonConfigCoreFunctionalTestCase;
import org.junit.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import static org.junit.Assert.assertEquals;

public class MetamodelBoundedCacheTest extends BaseNonConfigCoreFunctionalTestCase {

	@Test
	@TestForIssue(jiraKey = "HHH-14948")
	public void testMemoryConsumptionOfFailedImportsCache() throws NoSuchFieldException, IllegalAccessException {
		MappingMetamodel mappingMetamodel = sessionFactory().getMetamodel();

		MappingMetamodelImpl mImpl = (MappingMetamodelImpl) mappingMetamodel;
		final JpaMetamodel jpaMetamodel = mImpl.getJpaMetamodel();

		for ( int i = 0; i < 1001; i++ ) {
			jpaMetamodel.qualifyImportableName( "nonexistend" + i );
		}

		Field field = JpaMetamodelImpl.class.getDeclaredField( "nameToImportMap" );
		field.setAccessible( true );

		//noinspection unchecked
		Map<String, String> imports = (Map<String, String>) field.get( jpaMetamodel );

		// VERY hard-coded, but considering the possibility of a regression of a memory-related issue,
		// it should be worth it
		assertEquals( 1000, imports.size() );
	}

	@Override
	protected Class[] getAnnotatedClasses() {
		return new Class[] { Employee.class };
	}

	@Entity( name = "Employee" )
	@Table( name= "tabEmployees" )
	public class Employee {
		@Id
		private long id;
		private String name;

		public Employee() {

		}

		public Employee(long id, String strName) {
			this();
			this.name = strName;
		}

		public long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public void setName(String strName) {
			this.name = strName;
		}

	}
}
