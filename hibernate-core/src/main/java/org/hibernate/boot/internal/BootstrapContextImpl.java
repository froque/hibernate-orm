/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.AssertionFailure;
import org.hibernate.annotations.common.reflection.ReflectionManager;
import org.hibernate.annotations.common.reflection.java.JavaReflectionManager;
import org.hibernate.boot.CacheRegionDefinition;
import org.hibernate.boot.archive.scan.internal.StandardScanOptions;
import org.hibernate.boot.archive.scan.spi.ScanEnvironment;
import org.hibernate.boot.archive.scan.spi.ScanOptions;
import org.hibernate.boot.archive.scan.spi.Scanner;
import org.hibernate.boot.archive.spi.ArchiveDescriptorFactory;
import org.hibernate.boot.model.TypeBeanInstanceProducer;
import org.hibernate.boot.model.convert.spi.ConverterDescriptor;
import org.hibernate.boot.model.relational.AuxiliaryDatabaseObject;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.ClassLoaderAccess;
import org.hibernate.boot.spi.MetadataBuildingOptions;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.annotations.reflection.internal.JPAXMLOverriddenMetadataProvider;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.jpa.internal.MutableJpaComplianceImpl;
import org.hibernate.jpa.spi.MutableJpaCompliance;
import org.hibernate.metamodel.internal.ManagedTypeRepresentationResolverStandard;
import org.hibernate.metamodel.spi.ManagedTypeRepresentationResolver;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.resource.beans.spi.BeanInstanceProducer;
import org.hibernate.type.internal.BasicTypeImpl;
import org.hibernate.type.spi.TypeConfiguration;

import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import static org.hibernate.internal.log.DeprecationLogger.DEPRECATION_LOGGER;

/**
 * @author Andrea Boriero
 */
public class BootstrapContextImpl implements BootstrapContext {
	private static final Logger log = Logger.getLogger( BootstrapContextImpl.class );

	private final StandardServiceRegistry serviceRegistry;
	private final MetadataBuildingOptions metadataBuildingOptions;

	private final TypeConfiguration typeConfiguration;
	private final MutableJpaCompliance jpaCompliance;

	private final ClassLoaderAccessImpl classLoaderAccess;
	private final BeanInstanceProducer beanInstanceProducer;

	private boolean isJpaBootstrap;

	private final JavaReflectionManager hcannReflectionManager;
	private final ClassmateContext classmateContext;

	private ScanOptions scanOptions;
	private ScanEnvironment scanEnvironment;
	private Object scannerSetting;
	private ArchiveDescriptorFactory archiveDescriptorFactory;

	private IndexView jandexView;

	private HashMap<String,SqmFunctionDescriptor> sqlFunctionMap;
	private ArrayList<AuxiliaryDatabaseObject> auxiliaryDatabaseObjectList;
	private HashMap<Class<?>, ConverterDescriptor> attributeConverterDescriptorMap;
	private ArrayList<CacheRegionDefinition> cacheRegionDefinitions;
	private ManagedTypeRepresentationResolver representationStrategySelector;

	public BootstrapContextImpl(
			StandardServiceRegistry serviceRegistry,
			MetadataBuildingOptions metadataBuildingOptions) {
		this.serviceRegistry = serviceRegistry;
		this.classmateContext = new ClassmateContext();
		this.metadataBuildingOptions = metadataBuildingOptions;

		this.classLoaderAccess = new ClassLoaderAccessImpl( serviceRegistry.getService( ClassLoaderService.class ) );
		this.hcannReflectionManager = generateHcannReflectionManager();

		final StrategySelector strategySelector = serviceRegistry.getService( StrategySelector.class );
		final ConfigurationService configService = serviceRegistry.getService( ConfigurationService.class );

		this.jpaCompliance = new MutableJpaComplianceImpl( configService.getSettings() );
		this.scanOptions = new StandardScanOptions(
				(String) configService.getSettings().get( AvailableSettings.SCANNER_DISCOVERY ),
				false
		);

		// ScanEnvironment must be set explicitly
		this.scannerSetting = configService.getSettings().get( AvailableSettings.SCANNER );
		if ( this.scannerSetting == null ) {
			this.scannerSetting = configService.getSettings().get( AvailableSettings.SCANNER_DEPRECATED );
			if ( this.scannerSetting != null ) {
				DEPRECATION_LOGGER.logDeprecatedScannerSetting();
			}
		}
		this.archiveDescriptorFactory = strategySelector.resolveStrategy(
				ArchiveDescriptorFactory.class,
				configService.getSettings().get( AvailableSettings.SCANNER_ARCHIVE_INTERPRETER )
		);

		this.representationStrategySelector = ManagedTypeRepresentationResolverStandard.INSTANCE;

		this.typeConfiguration = new TypeConfiguration();
		this.beanInstanceProducer = new TypeBeanInstanceProducer( typeConfiguration );
	}

	@Override
	public StandardServiceRegistry getServiceRegistry() {
		return serviceRegistry;
	}

	@Override
	public MutableJpaCompliance getJpaCompliance() {
		return jpaCompliance;
	}

	@Override
	public TypeConfiguration getTypeConfiguration() {
		return typeConfiguration;
	}

	@Override
	public BeanInstanceProducer getBeanInstanceProducer() {
		return beanInstanceProducer;
	}

	@Override
	public MetadataBuildingOptions getMetadataBuildingOptions() {
		return metadataBuildingOptions;
	}

	@Override
	public boolean isJpaBootstrap() {
		return isJpaBootstrap;
	}

	@Override
	public void markAsJpaBootstrap() {
		isJpaBootstrap = true;
	}

	@Override
	public ClassLoader getJpaTempClassLoader() {
		return classLoaderAccess.getJpaTempClassLoader();
	}

	@Override
	public ClassLoaderAccess getClassLoaderAccess() {
		return classLoaderAccess;
	}

	@Override
	public ClassmateContext getClassmateContext() {
		return classmateContext;
	}

	@Override
	public ArchiveDescriptorFactory getArchiveDescriptorFactory() {
		return archiveDescriptorFactory;
	}

	@Override
	public ScanOptions getScanOptions() {
		return scanOptions;
	}

	@Override
	public ScanEnvironment getScanEnvironment() {
		return scanEnvironment;
	}

	@Override
	public Object getScanner() {
		return scannerSetting;
	}

	@Override
	public ReflectionManager getReflectionManager() {
		return hcannReflectionManager;
	}

	@Override
	public IndexView getJandexView() {
		return jandexView;
	}

	@Override
	public Map<String, SqmFunctionDescriptor> getSqlFunctions() {
		return sqlFunctionMap == null ? Collections.emptyMap() : sqlFunctionMap;
	}

	@Override
	public Collection<AuxiliaryDatabaseObject> getAuxiliaryDatabaseObjectList() {
		return auxiliaryDatabaseObjectList == null ? Collections.emptyList() : auxiliaryDatabaseObjectList;
	}

	@Override
	public Collection<ConverterDescriptor> getAttributeConverters() {
		return attributeConverterDescriptorMap != null
				? attributeConverterDescriptorMap.values()
				: Collections.emptyList();
	}

	@Override
	public Collection<CacheRegionDefinition> getCacheRegionDefinitions() {
		return cacheRegionDefinitions == null ? Collections.emptyList() : cacheRegionDefinitions;
	}

	private final Map<String,BasicTypeImpl<?>> adHocBasicTypeRegistrations = new HashMap<>();

	@Override
	public void registerAdHocBasicType(BasicTypeImpl<?> basicType) {
		adHocBasicTypeRegistrations.put( basicType.getName(), basicType );
	}

	@Override
	public <T> BasicTypeImpl<T> resolveAdHocBasicType(String key) {
		//noinspection unchecked
		return (BasicTypeImpl<T>) adHocBasicTypeRegistrations.get( key );
	}

	@Override
	public void release() {
		classmateContext.release();
		classLoaderAccess.release();

		scanOptions = null;
		scanEnvironment = null;
		scannerSetting = null;
		archiveDescriptorFactory = null;
		jandexView = null;

		if ( sqlFunctionMap != null ) {
			sqlFunctionMap.clear();
		}

		if ( auxiliaryDatabaseObjectList != null ) {
			auxiliaryDatabaseObjectList.clear();
		}

		if ( attributeConverterDescriptorMap != null ) {
			attributeConverterDescriptorMap.clear();
		}

		if ( cacheRegionDefinitions != null ) {
			cacheRegionDefinitions.clear();
		}
	}

	@Override
	public ManagedTypeRepresentationResolver getRepresentationStrategySelector() {
		return representationStrategySelector;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Mutations

	public void addAttributeConverterDescriptor(ConverterDescriptor descriptor) {
		if ( this.attributeConverterDescriptorMap == null ) {
			this.attributeConverterDescriptorMap = new HashMap<>();
		}

		final Object old = this.attributeConverterDescriptorMap.put( descriptor.getAttributeConverterClass(), descriptor );

		if ( old != null ) {
			throw new AssertionFailure(
					String.format(
							"AttributeConverter class [%s] registered multiple times",
							descriptor.getAttributeConverterClass()
					)
			);
		}
	}

	void injectJpaTempClassLoader(ClassLoader jpaTempClassLoader) {
		log.debugf( "Injecting JPA temp ClassLoader [%s] into BootstrapContext; was [%s]", jpaTempClassLoader, this.getJpaTempClassLoader() );
		this.classLoaderAccess.injectTempClassLoader( jpaTempClassLoader );
	}

	void injectScanOptions(ScanOptions scanOptions) {
		log.debugf( "Injecting ScanOptions [%s] into BootstrapContext; was [%s]", scanOptions, this.scanOptions );
		this.scanOptions = scanOptions;
	}

	void injectScanEnvironment(ScanEnvironment scanEnvironment) {
		log.debugf( "Injecting ScanEnvironment [%s] into BootstrapContext; was [%s]", scanEnvironment, this.scanEnvironment );
		this.scanEnvironment = scanEnvironment;
	}

	void injectScanner(Scanner scanner) {
		log.debugf( "Injecting Scanner [%s] into BootstrapContext; was [%s]", scanner, this.scannerSetting );
		this.scannerSetting = scanner;
	}

	void injectArchiveDescriptorFactory(ArchiveDescriptorFactory factory) {
		log.debugf( "Injecting ArchiveDescriptorFactory [%s] into BootstrapContext; was [%s]", factory, this.archiveDescriptorFactory );
		this.archiveDescriptorFactory = factory;
	}

	void injectJandexView(IndexView jandexView) {
		log.debugf( "Injecting Jandex IndexView [%s] into BootstrapContext; was [%s]", jandexView, this.jandexView );
		this.jandexView = jandexView;
	}

	public void addSqlFunction(String functionName, SqmFunctionDescriptor function) {
		if ( this.sqlFunctionMap == null ) {
			this.sqlFunctionMap = new HashMap<>();
		}
		this.sqlFunctionMap.put( functionName, function );
	}

	public void addAuxiliaryDatabaseObject(AuxiliaryDatabaseObject auxiliaryDatabaseObject) {
		if ( this.auxiliaryDatabaseObjectList == null ) {
			this.auxiliaryDatabaseObjectList = new ArrayList<>();
		}
		this.auxiliaryDatabaseObjectList.add( auxiliaryDatabaseObject );
	}


	public void addCacheRegionDefinition(CacheRegionDefinition cacheRegionDefinition) {
		if ( cacheRegionDefinitions == null ) {
			cacheRegionDefinitions = new ArrayList<>();
		}
		cacheRegionDefinitions.add( cacheRegionDefinition );
	}

	private JavaReflectionManager generateHcannReflectionManager() {
		final JavaReflectionManager reflectionManager = new JavaReflectionManager();
		reflectionManager.setMetadataProvider( new JPAXMLOverriddenMetadataProvider( this ) );
		return reflectionManager;
	}

}
