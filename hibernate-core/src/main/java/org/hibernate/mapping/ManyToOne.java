/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;

/**
 * A many-to-one association mapping
 * @author Gavin King
 */
public class ManyToOne extends ToOne {
	private boolean ignoreNotFound;
	private boolean isLogicalOneToOne;

	private Type resolvedType;

	public ManyToOne(MetadataBuildingContext buildingContext, Table table) {
		super( buildingContext, table );
	}

	public Type getType() throws MappingException {
		if ( resolvedType == null ) {
			resolvedType = MappingHelper.manyToOne(
					getReferencedEntityName(),
					isReferenceToPrimaryKey(),
					getReferencedPropertyName(),
					getPropertyName(),
					isLogicalOneToOne(),
					isLazy(),
					isUnwrapProxy(),
					isIgnoreNotFound(),
					getBuildingContext()
			);
		}

		return resolvedType;
	}

	public void createForeignKey() throws MappingException {
		// Ensure properties are sorted before we create a foreign key
		sortProperties();
		// the case of a foreign key to something other than the pk is handled in createPropertyRefConstraints
		if (referencedPropertyName==null && !hasFormula() ) {
			createForeignKeyOfEntity( ( (EntityType) getType() ).getAssociatedEntityName() );
		} 
	}



	public void createPropertyRefConstraints(Map persistentClasses) {
		if (referencedPropertyName!=null) {
			// Ensure properties are sorted before we create a foreign key
			sortProperties();
			PersistentClass pc = (PersistentClass) persistentClasses.get(getReferencedEntityName() );
			
			Property property = pc.getReferencedProperty( getReferencedPropertyName() );
			
			if (property==null) {
				throw new MappingException(
						"Could not find property " + 
						getReferencedPropertyName() + 
						" on " + 
						getReferencedEntityName() 
					);
			} 
			else {
				// Make sure synthetic properties are sorted
				if ( property.getValue() instanceof Component ) {
					( (Component) property.getValue() ).sortProperties();
				}
				// todo : if "none" another option is to create the ForeignKey object still	but to set its #disableCreation flag
				if ( !hasFormula() && !"none".equals( getForeignKeyName() ) ) {
					java.util.List refColumns = new ArrayList();
					Iterator iter = property.getColumnIterator();
					while ( iter.hasNext() ) {
						Column col = (Column) iter.next();
						refColumns.add( col );
					}
					
					ForeignKey fk = getTable().createForeignKey( 
							getForeignKeyName(), 
							getConstraintColumns(), 
							( (EntityType) getType() ).getAssociatedEntityName(), 
							getForeignKeyDefinition(), 
							refColumns
					);
					fk.setCascadeDeleteEnabled(isCascadeDeleteEnabled() );
				}
			}
		}
	}
	
	public Object accept(ValueVisitor visitor) {
		return visitor.accept(this);
	}

	public boolean isIgnoreNotFound() {
		return ignoreNotFound;
	}

	public void setIgnoreNotFound(boolean ignoreNotFound) {
		this.ignoreNotFound = ignoreNotFound;
	}

	public void markAsLogicalOneToOne() {
		this.isLogicalOneToOne = true;
	}

	public boolean isLogicalOneToOne() {
		return isLogicalOneToOne;
	}
}
