/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.query;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import org.hibernate.cfg.AvailableSettings;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.hibernate.testing.orm.junit.Setting;

import org.junit.jupiter.api.Test;

/**
 * @author Andrea Boriero
 */
@TestForIssue(jiraKey = "HHH-11579")
@Jpa(
		annotatedClasses = {
				QueryParametersWithDisabledValidationTest.TestEntity.class
		},
		integrationSettings = { @Setting(name = AvailableSettings.VALIDATE_QUERY_PARAMETERS, value = "false") }
)
public class QueryParametersWithDisabledValidationTest {

	@Test
	public void setParameterWithWrongTypeShouldNotThrowIllegalArgumentException(EntityManagerFactoryScope scope) {
		scope.inEntityManager(
				entityManager -> entityManager.createQuery( "select e from TestEntity e where e.id = :id" ).setParameter( "id", 1 )
		);
	}

	@Test
	public void setParameterWithCorrectTypeShouldNotThrowIllegalArgumentException(EntityManagerFactoryScope scope) {
		scope.inEntityManager(
				entityManager -> entityManager.createQuery( "select e from TestEntity e where e.id = :id" ).setParameter( "id", 1L )
		);
	}

	@Entity(name = "TestEntity")
	public class TestEntity {

		@Id
		@GeneratedValue(strategy = GenerationType.AUTO)
		private Long id;
	}
}
