/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate;

import java.util.Locale;
import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;

/**
 * Controls how the session interacts with the second-level cache and query cache.
 *
 * @author Gavin King
 * @author Strong Liu
 * @see Session#setCacheMode(CacheMode)
 */
public enum CacheMode {
	/**
	 * The session may read items from the cache, and add items to the cache.
	 */
	NORMAL( CacheStoreMode.USE, CacheRetrieveMode.USE ),
	/**
	 * The session will never interact with the cache, except to invalidate
	 * cache items when updates occur.
	 */
	IGNORE( CacheStoreMode.BYPASS, CacheRetrieveMode.BYPASS ),
	/**
	 * The session may read items from the cache, but will not add items,
	 * except to invalidate items when updates occur.
	 */
	GET( CacheStoreMode.BYPASS, CacheRetrieveMode.USE ),
	/**
	 * The session will never read items from the cache, but will add items
	 * to the cache as it reads them from the database.
	 */
	PUT( CacheStoreMode.USE, CacheRetrieveMode.BYPASS ),
	/**
	 * The session will never read items from the cache, but will add items
	 * to the cache as it reads them from the database.  In this mode, the
	 * effect of <tt>hibernate.cache.use_minimal_puts</tt> is bypassed, in
	 * order to <em>force</em> a cache refresh.
	 */
	REFRESH( CacheStoreMode.REFRESH, CacheRetrieveMode.BYPASS );

	private final CacheStoreMode storeMode;
	private final CacheRetrieveMode retrieveMode;

	CacheMode(CacheStoreMode storeMode, CacheRetrieveMode retrieveMode) {
		this.storeMode = storeMode;
		this.retrieveMode = retrieveMode;
	}

	public CacheStoreMode getJpaStoreMode() {
		return storeMode;
	}

	public CacheRetrieveMode getJpaRetrieveMode() {
		return retrieveMode;
	}

	/**
	 * Does this cache mode indicate that reads are allowed?
	 *
	 * @return {@code true} if cache reads are allowed; {@code false} otherwise.
	 */
	public boolean isGetEnabled() {
		return retrieveMode == CacheRetrieveMode.USE;
	}

	/**
	 * Does this cache mode indicate that writes are allowed?
	 *
	 * @return {@code true} if cache writes are allowed; {@code false} otherwise.
	 */
	public boolean isPutEnabled() {
		return storeMode == CacheStoreMode.USE || storeMode == CacheStoreMode.REFRESH;
	}

	/**
	 * Used to interpret externalized forms of this enum.
	 *
	 * @param setting The externalized form.
	 *
	 * @return The matching enum value.
	 *
	 * @throws MappingException Indicates the external form was not recognized as a valid enum value.
	 */
	public static CacheMode interpretExternalSetting(String setting) {
		if (setting == null) {
			return null;
		}

		try {
			return CacheMode.valueOf( setting.toUpperCase(Locale.ROOT) );
		}
		catch ( IllegalArgumentException e ) {
			throw new MappingException( "Unknown Cache Mode: " + setting );
		}
	}

	public static CacheMode fromJpaModes(CacheRetrieveMode retrieveMode, CacheStoreMode storeMode) {
		if ( retrieveMode == null && storeMode == null ) {
			return null;
		}

		if ( storeMode == null ) {
			storeMode = CacheStoreMode.BYPASS;
		}

		if ( retrieveMode == null ) {
			retrieveMode = CacheRetrieveMode.BYPASS;
		}

		switch ( storeMode ) {
			case BYPASS: {
				return retrieveMode == CacheRetrieveMode.USE
						? GET
						: IGNORE;
			}
			case REFRESH: {
				// technically should combo `CacheStoreMode#REFRESH` and `CacheRetrieveMode#USE` be illegal?
				return REFRESH;
			}
			case USE: {
				return retrieveMode == CacheRetrieveMode.USE
						? NORMAL
						: PUT;
			}
			default: {
				throw new UnsupportedOperationException( "Unrecognized CacheStoreMode : " + storeMode );
			}
		}
	}
}
