/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.java;

import java.util.Comparator;
import java.util.TimeZone;

import org.hibernate.type.descriptor.WrapperOptions;

/**
 * Descriptor for {@link TimeZone} handling.
 *
 * @author Steve Ebersole
 */
public class TimeZoneJavaTypeDescriptor extends AbstractClassJavaTypeDescriptor<TimeZone> {
	public static final TimeZoneJavaTypeDescriptor INSTANCE = new TimeZoneJavaTypeDescriptor();

	public static class TimeZoneComparator implements Comparator<TimeZone> {
		public static final TimeZoneComparator INSTANCE = new TimeZoneComparator();

		public int compare(TimeZone o1, TimeZone o2) {
			return o1.getID().compareTo( o2.getID() );
		}
	}

	public TimeZoneJavaTypeDescriptor() {
		super( TimeZone.class, ImmutableMutabilityPlan.INSTANCE, TimeZoneComparator.INSTANCE );
	}

	public String toString(TimeZone value) {
		return value.getID();
	}

	public TimeZone fromString(CharSequence string) {
		return TimeZone.getTimeZone( string.toString() );
	}

	@SuppressWarnings({ "unchecked" })
	public <X> X unwrap(TimeZone value, Class<X> type, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if ( String.class.isAssignableFrom( type ) ) {
			return (X) toString( value );
		}
		throw unknownUnwrap( type );
	}

	public <X> TimeZone wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if ( CharSequence.class.isInstance( value ) ) {
			return fromString( (CharSequence) value );
		}
		throw unknownWrap( value.getClass() );
	}
}
