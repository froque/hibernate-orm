/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.dialect;

import org.hibernate.dialect.pagination.Oracle12LimitHandler;
import org.hibernate.query.Limit;
import org.hibernate.query.spi.QueryOptions;

import org.hibernate.testing.TestForIssue;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

@TestForIssue( jiraKey = "HHH-14649")
public class Oracle12LimitHandlerTest {

	@Test
	public void testSqlWithSpace() {
		final String sql = "select  p.name from Person p where p.id = 1 for update";
		final String expected = "select * from (select  p.name from Person p where p.id = 1) where rownum<=? for update";

		final String processedSql = Oracle12LimitHandler.INSTANCE.processSql( sql, new Limit( 0, 5 ), QueryOptions.NONE );

		assertEquals( expected, processedSql );
	}

	@Test
	public void testSqlWithSpaceInsideQuotedString() {
		final String sql = "select p.name from Person p where p.name =  ' this is a  string with spaces  ' for update";
		final String expected = "select * from (select p.name from Person p where p.name =  ' this is a  string with spaces  ') where rownum<=? for update";

		final String processedSql = Oracle12LimitHandler.INSTANCE.processSql( sql, new Limit( 0, 5 ), QueryOptions.NONE );

		assertEquals( expected, processedSql );
	}

	@Test
	public void testSqlWithForUpdateInsideQuotedString() {
		final String sql = "select a.prop from A a where a.name =  'this is for update '";
		final String expected = "select a.prop from A a where a.name =  'this is for update ' fetch first ? rows only";

		final String processedSql = Oracle12LimitHandler.INSTANCE.processSql( sql, new Limit( 0, 5 ), QueryOptions.NONE );

		assertEquals( expected, processedSql );
	}

	@Test
	public void testSqlWithForUpdateInsideAndOutsideQuotedStringA() {
		final String sql = "select a.prop from A a where a.name =  'this is for update ' for update";
		final String expected = "select * from (select a.prop from A a where a.name =  'this is for update ') where rownum<=? for update";

		final String processedSql = Oracle12LimitHandler.INSTANCE.processSql( sql, new Limit( 0, 5 ), QueryOptions.NONE );

		assertEquals( expected, processedSql );
	}

}
