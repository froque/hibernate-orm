/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.java;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptorIndicators;

/**
 * Descriptor for {@link java.net.InetAddress} handling.
 *
 * @author Christian Beikov
 */
public class InetAddressJavaTypeDescriptor extends AbstractClassJavaTypeDescriptor<InetAddress> {

	public static final InetAddressJavaTypeDescriptor INSTANCE = new InetAddressJavaTypeDescriptor();

	public InetAddressJavaTypeDescriptor() {
		super( InetAddress.class );
	}

	@Override
	public String toString(InetAddress value) {
		return value == null ? null : value.toString();
	}

	@Override
	public InetAddress fromString(CharSequence string) {
		try {
			return string == null ? null : InetAddress.getByName( string.toString() );
		}
		catch (UnknownHostException e) {
			throw new IllegalArgumentException( e );
		}
	}

	@Override
	public JdbcType getRecommendedJdbcType(JdbcTypeDescriptorIndicators indicators) {
		return indicators.getTypeConfiguration().getJdbcTypeDescriptorRegistry().getDescriptor( SqlTypes.INET );
	}

	@SuppressWarnings({ "unchecked" })
	@Override
	public <X> X unwrap(InetAddress value, Class<X> type, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if ( InetAddress.class.isAssignableFrom( type ) ) {
			return (X) value;
		}
		if ( byte[].class.isAssignableFrom( type ) ) {
			return (X) value.getAddress();
		}
		if ( String.class.isAssignableFrom( type ) ) {
			return (X) value.toString();
		}
		throw unknownUnwrap( type );
	}

	@Override
	public <X> InetAddress wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if ( InetAddress.class.isInstance( value ) ) {
			return (InetAddress) value;
		}
		if ( byte[].class.isInstance( value ) ) {
			try {
				return InetAddress.getByAddress( (byte[]) value );
			}
			catch (UnknownHostException e) {
				throw new IllegalArgumentException( e );
			}
		}
		if ( String.class.isInstance( value ) ) {
			try {
				return InetAddress.getByName( (String) value );
			}
			catch (UnknownHostException e) {
				throw new IllegalArgumentException( e );
			}
		}
		throw unknownWrap( value.getClass() );
	}

	@Override
	public long getDefaultSqlLength(Dialect dialect, JdbcType jdbcType) {
		return 19;
	}

}
