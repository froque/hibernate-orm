/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.metamodel.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Iterator;

import org.hibernate.AssertionFailure;
import org.hibernate.PropertyNotFoundException;
import org.hibernate.internal.EntityManagerMessageLogger;
import org.hibernate.internal.HEMLogging;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.List;
import org.hibernate.mapping.Map;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;
import org.hibernate.metamodel.AttributeClassification;
import org.hibernate.metamodel.UnsupportedMappingException;
import org.hibernate.metamodel.RepresentationMode;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.model.domain.AbstractIdentifiableType;
import org.hibernate.metamodel.model.domain.EmbeddableDomainType;
import org.hibernate.metamodel.model.domain.IdentifiableDomainType;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.metamodel.model.domain.PersistentAttribute;
import org.hibernate.metamodel.model.domain.SimpleDomainType;
import org.hibernate.metamodel.model.domain.SingularPersistentAttribute;
import org.hibernate.metamodel.model.domain.internal.AnyMappingDomainTypeImpl;
import org.hibernate.metamodel.model.domain.internal.EmbeddableTypeImpl;
import org.hibernate.metamodel.model.domain.internal.MapMember;
import org.hibernate.metamodel.model.domain.internal.MappedSuperclassTypeImpl;
import org.hibernate.metamodel.model.domain.internal.PluralAttributeBuilder;
import org.hibernate.metamodel.model.domain.internal.SingularAttributeImpl;
import org.hibernate.metamodel.EmbeddableRepresentationStrategy;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.property.access.internal.PropertyAccessMapImpl;
import org.hibernate.property.access.spi.Getter;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.hibernate.type.AnyType;
import org.hibernate.type.EmbeddedComponentType;
import org.hibernate.type.EntityType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.hibernate.type.spi.CompositeTypeImplementor;

import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Type;

/**
 * A factory for building {@link Attribute} instances.  Exposes 3 main services for building<ol>
 * <li>{@link #buildAttribute normal attributes}</li>
 * <li>{@link #buildIdAttribute id attributes}</li>
 * <li>{@link #buildVersionAttribute version attributes}</li>
 * </ol>
 *
 * @author Steve Ebersole
 * @author Emmanuel Bernard
 */
public class AttributeFactory {
	private static final EntityManagerMessageLogger LOG = HEMLogging.messageLogger( AttributeFactory.class );

	private final MetadataContext context;

	public AttributeFactory(MetadataContext context) {
		this.context = context;
	}

	/**
	 * Build a normal attribute.
	 *
	 * @param ownerType The descriptor of the attribute owner (aka declarer).
	 * @param property The Hibernate property descriptor for the attribute
	 * @param <X> The type of the owner
	 * @param <Y> The attribute type
	 *
	 * @return The built attribute descriptor or null if the attribute is not part of the JPA 2 model (eg backrefs)
	 */
	public <X, Y> PersistentAttribute<X, Y> buildAttribute(ManagedDomainType<X> ownerType, Property property) {
		return buildAttribute( ownerType, property, context );
	}

	@SuppressWarnings({"unchecked"})
	public static <X, Y> PersistentAttribute<X, Y> buildAttribute(
			ManagedDomainType<X> ownerType,
			Property property,
			MetadataContext metadataContext) {
		if ( property.isSynthetic() ) {
			// hide synthetic/virtual properties (fabricated by Hibernate) from the JPA metamodel.
			LOG.tracef( "Skipping synthetic property %s(%s)", ownerType.getTypeName(), property.getName() );
			return null;
		}
		LOG.tracef( "Building attribute [%s.%s]", ownerType.getTypeName(), property.getName() );
		final AttributeContext<X> attributeContext = wrap( ownerType, property );
		final AttributeMetadata<X, Y> attributeMetadata = determineAttributeMetadata(
				attributeContext,
				normalMemberResolver,
				metadataContext
		);
		if ( attributeMetadata == null ) {
			return null;
		}
		if ( attributeMetadata.isPlural() ) {
			return PluralAttributeBuilder.build( (PluralAttributeMetadata) attributeMetadata, metadataContext );
		}
		final SingularAttributeMetadata<X, Y> singularAttributeMetadata = (SingularAttributeMetadata<X, Y>) attributeMetadata;
		final SimpleDomainType<Y> metaModelType = determineSimpleType( singularAttributeMetadata.getValueContext(), metadataContext );
		return new SingularAttributeImpl(
				ownerType,
				attributeMetadata.getName(),
				attributeMetadata.getAttributeClassification(),
				metaModelType,
				attributeMetadata.getMember(),
				false,
				false,
				property.isOptional(),
				metadataContext
		);
	}

	private static <X> AttributeContext<X> wrap(final ManagedDomainType<X> ownerType, final Property property) {
		return new AttributeContext<X>() {
			public ManagedDomainType<X> getOwnerType() {
				return ownerType;
			}

			public Property getPropertyMapping() {
				return property;
			}
		};
	}

	/**
	 * Build the identifier attribute descriptor
	 *
	 * @param ownerType The descriptor of the attribute owner (aka declarer).
	 * @param property The Hibernate property descriptor for the identifier attribute
	 * @param <X> The type of the owner
	 * @param <Y> The attribute type
	 *
	 * @return The built attribute descriptor
	 */
	@SuppressWarnings({"unchecked"})
	public <X, Y> SingularPersistentAttribute<X, Y> buildIdAttribute(
			IdentifiableDomainType<X> ownerType,
			Property property) {
		LOG.tracef( "Building identifier attribute [%s.%s]", ownerType.getTypeName(), property.getName() );

		// ownerType = Entity(Person)
		// MetadataContext#containerRoleStack -> Person

		// id-attribute = "id"

		final SingularAttributeMetadata<X, Y> attributeMetadata = (SingularAttributeMetadata) determineAttributeMetadata(
				wrap( ownerType, property ),
				identifierMemberResolver
		);

		return new SingularAttributeImpl.Identifier(
				ownerType,
				property.getName(),
				determineSimpleType( attributeMetadata.getValueContext() ),
				attributeMetadata.getMember(),
				attributeMetadata.getAttributeClassification(),
				context
		);
	}

	/**
	 * Build the version attribute descriptor
	 *
	 * @param ownerType The descriptor of the attribute owner (aka declarer).
	 * @param property The Hibernate property descriptor for the version attribute
	 * @param <X> The type of the owner
	 * @param <Y> The attribute type
	 *
	 * @return The built attribute descriptor
	 */
	@SuppressWarnings({"unchecked"})
	public <X, Y> SingularAttributeImpl<X, Y> buildVersionAttribute(
			IdentifiableDomainType<X> ownerType,
			Property property) {
		LOG.tracef( "Building version attribute [%s.%s]", ownerType.getTypeName(), property.getName() );

		final SingularAttributeMetadata<X, Y> attributeMetadata = (SingularAttributeMetadata<X, Y>) determineAttributeMetadata(
				wrap( ownerType, property ),
				versionMemberResolver
		);

		return new SingularAttributeImpl.Version(
				ownerType,
				property.getName(),
				attributeMetadata.getAttributeClassification(),
				determineSimpleType( attributeMetadata.getValueContext() ),
				attributeMetadata.getMember(),
				context
		);
	}

	private <Y> SimpleDomainType<Y> determineSimpleType(ValueContext typeContext) {
		return determineSimpleType( typeContext, context );
	}

	@SuppressWarnings("unchecked")
	public static <Y> SimpleDomainType<Y> determineSimpleType(ValueContext typeContext, MetadataContext context) {
		switch ( typeContext.getValueClassification() ) {
			case BASIC: {
				return context.resolveBasicType( typeContext.getJpaBindableType() );
			}
			case ENTITY: {
				final org.hibernate.type.Type type = typeContext.getHibernateValue().getType();
				if ( type instanceof EntityType ) {
					final EntityType entityType = (EntityType) type;
					return context.locateEntityType( entityType.getAssociatedEntityName() );
				}

				assert type instanceof AnyType;
				final AnyType anyType = (AnyType) type;
				final JavaType<Object> baseJtd = context.getTypeConfiguration()
						.getJavaTypeDescriptorRegistry()
						.resolveDescriptor( anyType.getReturnedClass() );
				return new AnyMappingDomainTypeImpl<>( anyType, baseJtd );
			}
			case EMBEDDABLE: {
				final Component component = (Component) typeContext.getHibernateValue();
				final EmbeddableTypeImpl<Y> embeddableType;

				if ( component.isDynamic() ) {
					final JavaType javaTypeDescriptor = context.getJavaTypeDescriptorRegistry().getDescriptor( java.util.Map.class );

					embeddableType = new EmbeddableTypeImpl<>(
							javaTypeDescriptor,
							true,
							context.getJpaMetamodel()
					);

					context.registerComponentByEmbeddable( embeddableType, component );
				}
				else {
					// we should have a non-dynamic embeddable
					assert component.getComponentClassName() != null;
					final Class<Y> embeddableClass = component.getComponentClass();

					final EmbeddableDomainType<Y> cached = context.locateEmbeddable( embeddableClass, component );
					if ( cached != null ) {
						return cached;
					}

					final JavaTypeRegistry registry = context.getTypeConfiguration()
							.getJavaTypeDescriptorRegistry();
					final JavaType<Y> javaTypeDescriptor = registry.resolveManagedTypeDescriptor( embeddableClass );

					embeddableType = new EmbeddableTypeImpl<>(
							javaTypeDescriptor,
							false,
							context.getJpaMetamodel()
					);

					context.registerEmbeddableType( embeddableType, component );

					return embeddableType;
				}

				final EmbeddableTypeImpl.InFlightAccess<Y> inFlightAccess = embeddableType.getInFlightAccess();
				final Iterator<Property> subProperties = component.getPropertyIterator();
				while ( subProperties.hasNext() ) {
					final Property property = subProperties.next();
					final PersistentAttribute<Y, Y> attribute = buildAttribute( embeddableType, property, context );
					if ( attribute != null ) {
						inFlightAccess.addAttribute( attribute );
					}
				}
				inFlightAccess.finishUp();

				return embeddableType;
			}
			default: {
				throw new AssertionFailure( "Unknown type : " + typeContext.getValueClassification() );
			}
		}
	}

	private EntityMetamodel getDeclarerEntityMetamodel(AbstractIdentifiableType<?> ownerType) {
		return getDeclarerEntityMetamodel( ownerType, context );
	}

	private static EntityPersister getDeclaringEntity(
			AbstractIdentifiableType<?> ownerType,
			MetadataContext metadataContext) {
		final Type.PersistenceType persistenceType = ownerType.getPersistenceType();
		if ( persistenceType == Type.PersistenceType.ENTITY ) {
			return metadataContext.getMetamodel()
					.getEntityDescriptor( ownerType.getTypeName() );
		}
		else if ( persistenceType == Type.PersistenceType.MAPPED_SUPERCLASS ) {
			PersistentClass persistentClass =
					metadataContext.getPersistentClassHostingProperties( (MappedSuperclassTypeImpl<?>) ownerType );
			return metadataContext.getMetamodel()
					.findEntityDescriptor( persistentClass.getClassName() );
		}
		else {
			throw new AssertionFailure( "Cannot get the metamodel for PersistenceType: " + persistenceType );
		}
	}
	private static EntityMetamodel getDeclarerEntityMetamodel(
			AbstractIdentifiableType<?> ownerType,
			MetadataContext metadataContext) {
		final Type.PersistenceType persistenceType = ownerType.getPersistenceType();
		if ( persistenceType == Type.PersistenceType.ENTITY ) {
			return metadataContext.getMetamodel()
					.getEntityDescriptor( ownerType.getTypeName() )
					.getEntityMetamodel();
		}
		else if ( persistenceType == Type.PersistenceType.MAPPED_SUPERCLASS ) {
			PersistentClass persistentClass =
					metadataContext.getPersistentClassHostingProperties( (MappedSuperclassTypeImpl<?>) ownerType );
			return metadataContext.getMetamodel()
					.findEntityDescriptor( persistentClass.getClassName() )
					.getEntityMetamodel();
		}
		else {
			throw new AssertionFailure( "Cannot get the metamodel for PersistenceType: " + persistenceType );
		}
	}

	/**
	 * Here is most of the nuts and bolts of this factory, where we interpret the known JPA metadata
	 * against the known Hibernate metadata and build a descriptor for the attribute.
	 *
	 * @param attributeContext The attribute to be described
	 * @param memberResolver Strategy for how to resolve the member defining the attribute.
	 * @param <X> The owner type
	 * @param <Y> The attribute type
	 *
	 * @return The attribute description
	 */
	@SuppressWarnings({"unchecked"})
	private <X, Y> AttributeMetadata<X, Y> determineAttributeMetadata(
			AttributeContext<X> attributeContext,
			MemberResolver memberResolver) {
		return determineAttributeMetadata( attributeContext, memberResolver, context );
	}

	private static <X, Y> AttributeMetadata<X, Y> determineAttributeMetadata(
			AttributeContext<X> attributeContext,
			MemberResolver memberResolver,
			MetadataContext context) {
		final Property propertyMapping = attributeContext.getPropertyMapping();
		final String propertyName = propertyMapping.getName();

		LOG.tracef( "Starting attribute metadata determination [%s]", propertyName );

		final Member member = memberResolver.resolveMember( attributeContext, context );
		LOG.tracef( "    Determined member [%s]", member );

		final Value value = propertyMapping.getValue();
		final org.hibernate.type.Type type = value.getType();
		LOG.tracef( "    Determined type [name=%s, class=%s]", type.getName(), type.getClass().getName() );

		if ( type.isAnyType() ) {
			return new SingularAttributeMetadataImpl<>(
					propertyMapping,
					attributeContext.getOwnerType(),
					member,
					AttributeClassification.ANY,
					context
			);
		}
		else if ( type.isAssociationType() ) {
			// collection or entity
			if ( type.isEntityType() ) {
				// entity
				return new SingularAttributeMetadataImpl<>(
						propertyMapping,
						attributeContext.getOwnerType(),
						member,
						determineSingularAssociationClassification( member ),
						context
				);
			}
			// collection
			if ( value instanceof Collection ) {
				final Collection collValue = (Collection) value;
				final Value elementValue = collValue.getElement();
				final org.hibernate.type.Type elementType = elementValue.getType();
				final boolean isManyToMany = isManyToMany( member );

				// First, determine the type of the elements and use that to help determine the
				// collection type
				final AttributeClassification elementClassification;
				final AttributeClassification attributeClassification;
				if ( elementType.isAnyType() ) {
					attributeClassification = AttributeClassification.ELEMENT_COLLECTION;
					elementClassification = AttributeClassification.ANY;
				}
				else if ( elementValue instanceof Component ) {
					elementClassification = AttributeClassification.EMBEDDED;
					attributeClassification = AttributeClassification.ELEMENT_COLLECTION;
				}
				else if ( elementType.isAssociationType() ) {
					elementClassification = isManyToMany ?
							AttributeClassification.MANY_TO_MANY :
							AttributeClassification.ONE_TO_MANY;
					attributeClassification = elementClassification;
				}
				else {
					elementClassification = AttributeClassification.BASIC;
					attributeClassification = AttributeClassification.ELEMENT_COLLECTION;
				}

				final AttributeClassification indexClassification;

				// Finally, we determine the type of the map key (if needed)
				if ( value instanceof Map ) {
					final Value keyValue = ( (Map) value ).getIndex();
					final org.hibernate.type.Type keyType = keyValue.getType();

					if ( keyType.isAnyType() ) {
						indexClassification = AttributeClassification.ANY;
					}
					else if ( keyValue instanceof Component ) {
						indexClassification = AttributeClassification.EMBEDDED;
					}
					else if ( keyType.isAssociationType() ) {
						indexClassification = AttributeClassification.MANY_TO_ONE;
					}
					else {
						indexClassification = AttributeClassification.BASIC;
					}
				}
				else if ( value instanceof List ) {
					indexClassification = AttributeClassification.BASIC;
				}
				else {
					indexClassification = null;
				}
				return new PluralAttributeMetadataImpl(
						propertyMapping,
						attributeContext.getOwnerType(),
						member,
						attributeClassification,
						elementClassification,
						indexClassification,
						context
				);
			}
			else if ( value instanceof OneToMany ) {
				// TODO : is this even possible??? Really OneToMany should be describing the
				// element value within a o.h.mapping.Collection (see logic branch above)
				throw new IllegalArgumentException( "HUH???" );
//					final boolean isManyToMany = isManyToMany( member );
//					//one to many with FK => entity
//					return new PluralAttributeMetadataImpl(
//							attributeContext.getPropertyMapping(),
//							attributeContext.getOwnerType(),
//							member,
//							isManyToMany
//									? Attribute.PersistentAttributeType.MANY_TO_MANY
//									: Attribute.PersistentAttributeType.ONE_TO_MANY
//							value,
//							AttributeContext.TypeStatus.ENTITY,
//							Attribute.PersistentAttributeType.ONE_TO_MANY,
//							null, null, null
//					);
			}
		}
		else if ( propertyMapping.isComposite() ) {
			// component
			return new SingularAttributeMetadataImpl<>(
					propertyMapping,
					attributeContext.getOwnerType(),
					member,
					AttributeClassification.EMBEDDED,
					context
			);
		}
		else {
			// basic type
			return new SingularAttributeMetadataImpl<>(
					propertyMapping,
					attributeContext.getOwnerType(),
					member,
					AttributeClassification.BASIC,
					context
			);
		}
		throw new UnsupportedMappingException( "oops, we are missing something: " + propertyMapping );
	}

	public static AttributeClassification determineSingularAssociationClassification(Member member) {
		if ( member instanceof Field ) {
			return ( (Field) member ).getAnnotation( OneToOne.class ) != null
					? AttributeClassification.ONE_TO_ONE
					: AttributeClassification.MANY_TO_ONE;
		}
		else if ( member instanceof MapMember ) {
			return AttributeClassification.MANY_TO_ONE; // curious to see how this works for non-annotated methods
		}
		else {
			return ( (Method) member ).getAnnotation( OneToOne.class ) != null
					? AttributeClassification.ONE_TO_ONE
					: AttributeClassification.MANY_TO_ONE;
		}
	}

	protected <Y> Class<Y> accountForPrimitiveTypes(Class<Y> declaredType) {
		return accountForPrimitiveTypes( declaredType, context );
	}

	public static <Y> Class<Y> accountForPrimitiveTypes(Class<Y> declaredType, MetadataContext metadataContext) {
//		if ( !declaredType.isPrimitive() ) {
//			return declaredType;
//		}
//
//		if ( Boolean.TYPE.equals( declaredType ) ) {
//			return (Class<Y>) Boolean.class;
//		}
//		if ( Character.TYPE.equals( declaredType ) ) {
//			return (Class<Y>) Character.class;
//		}
//		if( Byte.TYPE.equals( declaredType ) ) {
//			return (Class<Y>) Byte.class;
//		}
//		if ( Short.TYPE.equals( declaredType ) ) {
//			return (Class<Y>) Short.class;
//		}
//		if ( Integer.TYPE.equals( declaredType ) ) {
//			return (Class<Y>) Integer.class;
//		}
//		if ( Long.TYPE.equals( declaredType ) ) {
//			return (Class<Y>) Long.class;
//		}
//		if ( Float.TYPE.equals( declaredType ) ) {
//			return (Class<Y>) Float.class;
//		}
//		if ( Double.TYPE.equals( declaredType ) ) {
//			return (Class<Y>) Double.class;
//		}
//
//		throw new IllegalArgumentException( "Unexpected type [" + declaredType + "]" );
		// if the field is defined as int, return int not Integer...
		return declaredType;
	}

	public static ParameterizedType getSignatureType(Member member) {
		final java.lang.reflect.Type type;
		if ( member instanceof Field ) {
			type = ( (Field) member ).getGenericType();
		}
		else if ( member instanceof Method ) {
			type = ( (Method) member ).getGenericReturnType();
		}
		else {
			type = ( (MapMember) member ).getType();
		}
		//this is a raw type
		if ( type instanceof Class ) {
			return null;
		}
		return (ParameterizedType) type;
	}

	public static boolean isManyToMany(Member member) {
		if ( member instanceof Field ) {
			return ( (Field) member ).getAnnotation( ManyToMany.class ) != null;
		}
		else if ( member instanceof Method ) {
			return ( (Method) member ).getAnnotation( ManyToMany.class ) != null;
			}

		return false;
	}

	private static final MemberResolver embeddedMemberResolver = (attributeContext, metadataContext) -> {
		// the owner is an embeddable
		final EmbeddableDomainType<?> ownerType = (EmbeddableDomainType<?>) attributeContext.getOwnerType();
		final Component ownerBootDescriptor = metadataContext.getEmbeddableBootDescriptor( ownerType );

		final CompositeTypeImplementor ownerComponentType = (CompositeTypeImplementor) ownerBootDescriptor.getType();
		final EmbeddableValuedModelPart ownerMappingModelDescriptor = ownerComponentType.getMappingModelPart();
		final EmbeddableRepresentationStrategy ownerRepStrategy = ownerMappingModelDescriptor
				.getEmbeddableTypeDescriptor()
				.getRepresentationStrategy();

		if ( ownerRepStrategy.getMode() == RepresentationMode.MAP ) {
			return new MapMember(
					attributeContext.getPropertyMapping().getName(),
					attributeContext.getPropertyMapping().getType().getReturnedClass()
			);
		}
		else {
			return ownerRepStrategy
					.resolvePropertyAccess( attributeContext.getPropertyMapping() )
					.getGetter()
					.getMember();
		}
	};


	private static final MemberResolver virtualIdentifierMemberResolver = (attributeContext, metadataContext) -> {
		final AbstractIdentifiableType<?> identifiableType = (AbstractIdentifiableType<?>) attributeContext.getOwnerType();
		final EntityMetamodel entityMetamodel = getDeclarerEntityMetamodel( identifiableType, metadataContext );
		if ( !entityMetamodel.getIdentifierProperty().isVirtual() ) {
			throw new IllegalArgumentException( "expecting IdClass mapping" );
		}

		org.hibernate.type.Type type = entityMetamodel.getIdentifierProperty().getType();
		if ( !(type instanceof EmbeddedComponentType) ) {
			throw new IllegalArgumentException( "expecting IdClass mapping" );
		}

		final String attributeName = attributeContext.getPropertyMapping().getName();
		final EmbeddedComponentType componentType = (EmbeddedComponentType) type;
		final EmbeddableValuedModelPart embeddedPart = ( (CompositeTypeImplementor) componentType ).getMappingModelPart();
		assert embeddedPart != null;
		final EmbeddableMappingType embeddable = embeddedPart.getEmbeddableTypeDescriptor();
		final AttributeMapping attributeMapping = embeddable.findAttributeMapping( attributeName );
		if ( attributeMapping == null ) {
			throw new PropertyNotFoundException(
					"Unable to locate property named " + attributeName + " on " + embeddable.getJavaTypeDescriptor().getJavaTypeClass().getName()
			);
		}

		final Getter getter = attributeMapping.getPropertyAccess().getGetter();
		return getter instanceof PropertyAccessMapImpl.GetterImpl
				? new MapMember( attributeName, attributeContext.getPropertyMapping().getType().getReturnedClass() )
				: getter.getMember();
	};

	/**
	 * A {@link Member} resolver for normal attributes.
	 */
	private static final MemberResolver normalMemberResolver = (attributeContext, metadataContext) -> {
		final ManagedDomainType ownerType = attributeContext.getOwnerType();
		final Property property = attributeContext.getPropertyMapping();
		final Type.PersistenceType persistenceType = ownerType.getPersistenceType();
		if ( Type.PersistenceType.EMBEDDABLE == persistenceType ) {
			return embeddedMemberResolver.resolveMember( attributeContext, metadataContext );
		}
		else if ( Type.PersistenceType.ENTITY == persistenceType
				|| Type.PersistenceType.MAPPED_SUPERCLASS == persistenceType ) {
			final AbstractIdentifiableType identifiableType = (AbstractIdentifiableType) ownerType;
			final EntityPersister declaringEntityMapping = getDeclaringEntity( identifiableType, metadataContext );
			final EntityMetamodel entityMetamodel = declaringEntityMapping.getEntityMetamodel();
			final String propertyName = property.getName();
			final Integer index = entityMetamodel.getPropertyIndexOrNull( propertyName );
			if ( index == null ) {
				// just like in #determineIdentifierJavaMember , this *should* indicate we have an IdClass mapping
				return virtualIdentifierMemberResolver.resolveMember( attributeContext, metadataContext );
			}
			else {
				final Getter getter = declaringEntityMapping.getRepresentationStrategy().resolvePropertyAccess( property ).getGetter();
				return getter instanceof PropertyAccessMapImpl.GetterImpl
						? new MapMember( propertyName, property.getType().getReturnedClass() )
						: getter.getMember();
			}
		}
		else {
			throw new IllegalArgumentException( "Unexpected owner type : " + persistenceType );
		}
	};

	private final MemberResolver identifierMemberResolver = (attributeContext, metadataContext) -> {
		final AbstractIdentifiableType identifiableType = (AbstractIdentifiableType) attributeContext.getOwnerType();
		final EntityPersister declaringEntityMapping = getDeclaringEntity( identifiableType, metadataContext );
		final EntityMetamodel entityMetamodel = declaringEntityMapping.getEntityMetamodel();

		if ( !attributeContext.getPropertyMapping().getName()
				.equals( entityMetamodel.getIdentifierProperty().getName() ) ) {
			// this *should* indicate processing part of an IdClass...
			return virtualIdentifierMemberResolver.resolveMember( attributeContext, metadataContext );
		}

		final Getter getter = declaringEntityMapping.getRepresentationStrategy().resolvePropertyAccess( attributeContext.getPropertyMapping() ).getGetter();
		if ( getter instanceof PropertyAccessMapImpl.GetterImpl ) {
			return new MapMember(
					entityMetamodel.getIdentifierProperty().getName(),
					entityMetamodel.getIdentifierProperty().getType().getReturnedClass()
			);
		}
		else {
			return getter.getMember();
		}
	};

	private final MemberResolver versionMemberResolver = new MemberResolver() {
		@Override
		public Member resolveMember(
				AttributeContext attributeContext,
				MetadataContext metadataContext) {
			final AbstractIdentifiableType identifiableType = (AbstractIdentifiableType) attributeContext.getOwnerType();
			final EntityPersister declaringEntityMapping = getDeclaringEntity( identifiableType, metadataContext );
			final EntityMetamodel entityMetamodel = declaringEntityMapping.getEntityMetamodel();
			final String versionPropertyName = attributeContext.getPropertyMapping().getName();
			if ( !versionPropertyName.equals( entityMetamodel.getVersionProperty().getName() ) ) {
				// this should never happen, but to be safe...
				throw new IllegalArgumentException( "Given property did not match declared version property" );
			}

			final Getter getter = declaringEntityMapping.getRepresentationStrategy().resolvePropertyAccess( attributeContext.getPropertyMapping() ).getGetter();
			if ( PropertyAccessMapImpl.GetterImpl.class.isInstance( getter ) ) {
				return new MapMember(
						versionPropertyName,
						attributeContext.getPropertyMapping().getType().getReturnedClass()
				);
			}
			else {
				return getter.getMember();
			}
		}
	};
}
