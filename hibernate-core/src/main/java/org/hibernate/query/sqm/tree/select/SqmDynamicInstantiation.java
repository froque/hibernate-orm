/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.select;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.hibernate.query.DynamicInstantiationNature;
import org.hibernate.query.criteria.JpaCompoundSelection;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.query.sqm.tree.jpa.AbstractJpaSelection;
import org.hibernate.type.descriptor.java.JavaType;

import org.jboss.logging.Logger;

import static org.hibernate.query.DynamicInstantiationNature.CLASS;
import static org.hibernate.query.DynamicInstantiationNature.LIST;
import static org.hibernate.query.DynamicInstantiationNature.MAP;

/**
 * Represents a dynamic instantiation ({@code select new XYZ(...) ...}) as part of the SQM.
 *
 * @author Steve Ebersole
 */
public class SqmDynamicInstantiation<T>
		extends AbstractJpaSelection<T>
		implements SqmSelectableNode<T>,
		SqmAliasedExpressionContainer<SqmDynamicInstantiationArgument<?>>,
		JpaCompoundSelection<T> {

	private static final Logger log = Logger.getLogger( SqmDynamicInstantiation.class );

	public static <R> SqmDynamicInstantiation<R> forClassInstantiation(
			JavaType<R> targetJavaType,
			NodeBuilder nodeBuilder) {
		return new SqmDynamicInstantiation<>(
				new DynamicInstantiationTargetImpl<>( CLASS, targetJavaType ),
				nodeBuilder
		);
	}

	public static <R> SqmDynamicInstantiation<R> forClassInstantiation(
			Class<R> targetJavaType,
			NodeBuilder nodeBuilder) {
		return forClassInstantiation(
				nodeBuilder.getTypeConfiguration().getJavaTypeDescriptorRegistry().getDescriptor( targetJavaType ),
				nodeBuilder
		);
	}

	public static <M extends Map<?, ?>> SqmDynamicInstantiation<M> forMapInstantiation(
			JavaType<M> mapJavaTypeDescriptor,
			NodeBuilder nodeBuilder) {
		return new SqmDynamicInstantiation<>(
				new DynamicInstantiationTargetImpl<>( MAP, mapJavaTypeDescriptor ),
				nodeBuilder
		);
	}

	public static <M extends Map<?, ?>> SqmDynamicInstantiation<M> forMapInstantiation(NodeBuilder nodeBuilder) {
		//noinspection unchecked
		return forMapInstantiation(
				nodeBuilder.getTypeConfiguration().getJavaTypeDescriptorRegistry().getDescriptor( Map.class ),
				nodeBuilder
		);
	}

	public static <L extends List<?>> SqmDynamicInstantiation<L> forListInstantiation(
			JavaType<L> listJavaTypeDescriptor,
			NodeBuilder nodeBuilder) {
		return new SqmDynamicInstantiation<>(
				new DynamicInstantiationTargetImpl<>( LIST, listJavaTypeDescriptor ),
				nodeBuilder
		);
	}

	public static <L extends List<?>> SqmDynamicInstantiation<L> forListInstantiation(NodeBuilder nodeBuilder) {
		//noinspection unchecked
		return forListInstantiation(
				nodeBuilder.getTypeConfiguration().getJavaTypeDescriptorRegistry().getDescriptor( List.class ),
				nodeBuilder
		);
	}

	private final SqmDynamicInstantiationTarget <T> instantiationTarget;
	private List<SqmDynamicInstantiationArgument<?>> arguments;

	private SqmDynamicInstantiation(
			SqmDynamicInstantiationTarget<T> instantiationTarget,
			NodeBuilder nodeBuilder) {
		super( instantiationTarget, nodeBuilder );
		this.instantiationTarget = instantiationTarget;
	}

	public SqmDynamicInstantiationTarget<T> getInstantiationTarget() {
		return instantiationTarget;
	}

	public List<SqmDynamicInstantiationArgument<?>> getArguments() {
		return arguments;
	}

	@Override
	public JavaType<T> getJavaTypeDescriptor() {
		return getInstantiationTarget().getTargetTypeDescriptor();
	}

	@Override
	public String asLoggableText() {
		return "<new " + instantiationTarget.getJavaType().getName() + ">";
	}

	public void addArgument(SqmDynamicInstantiationArgument<?> argument) {
		if ( instantiationTarget.getNature() == LIST ) {
			// really should not have an alias...
			if ( argument.getAlias() != null && log.isDebugEnabled() ) {
				log.debugf(
						"Argument [%s] for dynamic List instantiation declared an 'injection alias' [%s] " +
								"but such aliases are ignored for dynamic List instantiations",
						argument.getSelectableNode().asLoggableText(),
						argument.getAlias()
				);
			}
		}
		else if ( instantiationTarget.getNature() == MAP ) {
			// must(?) have an alias...
			log.warnf(
					"Argument [%s] for dynamic Map instantiation did not declare an 'injection alias' [%s] " +
							"but such aliases are needed for dynamic Map instantiations; " +
							"will likely cause problems later translating sqm",
					argument.getSelectableNode().asLoggableText(),
					argument.getAlias()
			);
		}

		if ( arguments == null ) {
			arguments = new ArrayList<>();
		}
		arguments.add( argument );
	}

	@Override
	public SqmDynamicInstantiationArgument<?> add(SqmExpression<?> expression, String alias) {
		final SqmDynamicInstantiationArgument<?> argument = new SqmDynamicInstantiationArgument<>(
				expression,
				alias,
				nodeBuilder()
		);
		addArgument( argument );
		return argument;
	}

	@Override
	public void add(SqmDynamicInstantiationArgument<?> aliasExpression) {
		addArgument( aliasExpression );
	}

	@Override
	public <X> X accept(SemanticQueryWalker<X> walker) {
		return walker.visitDynamicInstantiation( this );
	}

	@Override
	public void appendHqlString(StringBuilder sb) {
		sb.append( "new " );
		if ( instantiationTarget.getNature() == LIST ) {
			sb.append( "list" );
		}
		else if ( instantiationTarget.getNature() == MAP ) {
			sb.append( "map" );
		}
		else {
			sb.append( instantiationTarget.getTargetTypeDescriptor().getJavaTypeClass().getTypeName() );
		}
		sb.append( '(' );
		( (SqmSelectableNode<?>) arguments.get( 0 ) ).appendHqlString( sb );
		for ( int i = 1; i < arguments.size(); i++ ) {
			sb.append(", ");
			( (SqmSelectableNode<?>) arguments.get( i ) ).appendHqlString( sb );
		}

		sb.append( ')' );
	}

	@SuppressWarnings("unused")
	public SqmDynamicInstantiation<T> makeShallowCopy() {
		return new SqmDynamicInstantiation<>( getInstantiationTarget(), nodeBuilder() );
	}

	private static class DynamicInstantiationTargetImpl<T> implements SqmDynamicInstantiationTarget<T> {
		private final DynamicInstantiationNature nature;
		private final JavaType<T> javaTypeDescriptor;


		private DynamicInstantiationTargetImpl(DynamicInstantiationNature nature, JavaType<T> javaTypeDescriptor) {
			this.nature = nature;
			this.javaTypeDescriptor = javaTypeDescriptor;
		}

		@Override
		public DynamicInstantiationNature getNature() {
			return nature;
		}

		@Override
		public JavaType<T> getTargetTypeDescriptor() {
			return javaTypeDescriptor;
		}

		@Override
		public JavaType<T> getExpressableJavaTypeDescriptor() {
			return getTargetTypeDescriptor();
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// JPA

	@Override
	public void visitSubSelectableNodes(Consumer<SqmSelectableNode<?>> consumer) {
		for ( SqmDynamicInstantiationArgument<?> argument : arguments ) {
			consumer.accept( argument.getSelectableNode() );
		}
	}

	@Override
	public List<SqmSelectableNode<?>> getSelectionItems() {
		final List<SqmSelectableNode<?>> list = new ArrayList<>();
		visitSubSelectableNodes( list::add );
		return list;
	}

	@Override
	public boolean isCompoundSelection() {
		return false;
	}
}
