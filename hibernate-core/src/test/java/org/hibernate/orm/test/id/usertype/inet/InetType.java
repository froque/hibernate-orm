/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.id.usertype.inet;

import org.hibernate.type.AbstractSingleColumnStandardBasicType;

public class InetType extends AbstractSingleColumnStandardBasicType<Inet> {

	public static final InetType INSTANCE = new InetType();

	public InetType() {
		super( InetJdbcType.INSTANCE, InetJavaTypeDescriptor.INSTANCE );
	}

	@Override
	public String getName() {
		return "inet";
	}
}