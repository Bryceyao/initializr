/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.initializr

import javax.annotation.PostConstruct

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.ToString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The metadata using by the initializr, that is:
 *
 * <ul>
 * <li>Known dependencies gathered in group</li>
 * <li>The build types supported by the service</li>
 * <li>Supported Java versions</li>
 * <li>Supported language</li>
 * <li>Supported Spring Boot versions</li>
 * <li>Default settings used to generate the project</li>
 * <li>Environment related settings</li>
 * </ul>
 *
 * @author Stephane Nicoll
 * @since 1.0
 */
@ConfigurationProperties(prefix = 'initializr', ignoreUnknownFields = false)
class InitializrMetadata {

	private static final Logger logger = LoggerFactory.getLogger(InitializrMetadata)

	final List<DependencyGroup> dependencies = new ArrayList<DependencyGroup>()

	final List<Type> types = new ArrayList<Type>()

	final List<Packaging> packagings = new ArrayList<Packaging>()

	final List<JavaVersion> javaVersions = new ArrayList<JavaVersion>()

	final List<Language> languages = new ArrayList<Language>()

	final List<BootVersion> bootVersions = new ArrayList<BootVersion>()

	final Defaults defaults = new Defaults()

	@JsonIgnore
	final Env env = new Env()

	@JsonIgnore
	final Map<String, Dependency> indexedDependencies = new HashMap<String, Dependency>()

	/**
	 * Return the {@link Dependency} with the specified id or {@code null} if
	 * no such dependency exists.
	 */
	Dependency getDependency(String id) {
		return indexedDependencies.get(id)
	}

	/**
	 * Initializes a {@link ProjectRequest} instance with the defaults
	 * defined in this instance.
	 */
	void initializeProjectRequest(ProjectRequest request) {
		defaults.properties.each { key, value ->
			if (request.hasProperty(key) && !(key in ['class', 'metaClass'])) {
				request[key] = value
			}
		}
		request
	}

	/**
	 * Initialize and validate the configuration.
	 */
	@PostConstruct
	void validate() {
		for (DependencyGroup group : dependencies) {
			for (Dependency dependency : group.getContent()) {
				validateDependency(dependency)
				indexDependency(dependency.id, dependency)
				for (String alias : dependency.aliases) {
					indexDependency(alias, dependency)
				}
			}
		}
		env.validate()

		defaults.type = getDefault(types)
		defaults.packaging = getDefault(packagings)
		defaults.javaVersion = getDefault(javaVersions)
		defaults.language = getDefault(languages)
		defaults.bootVersion = getDefault(bootVersions)
	}

	private void indexDependency(String id, Dependency dependency) {
		Dependency existing = indexedDependencies.get(id)
		if (existing != null) {
			throw new IllegalArgumentException('Could not register ' + dependency +
					': another dependency has also the "' + id + '" id ' + existing)
		}
		indexedDependencies.put(id, dependency)
	}

	static void validateDependency(Dependency dependency) {
		String id = dependency.getId()
		if (id == null) {
			if (!dependency.hasCoordinates()) {
				throw new InvalidInitializrMetadataException('Invalid dependency, ' +
						'should have at least an id or a groupId/artifactId pair.')
			}
			dependency.generateId()
		} else if (!dependency.hasCoordinates()) {
			// Let's build the coordinates from the id
			StringTokenizer st = new StringTokenizer(id, ':')
			if (st.countTokens() == 1) { // assume spring-boot-starter
				dependency.asSpringBootStarter(id)
			} else if (st.countTokens() == 2 || st.countTokens() == 3) {
				dependency.groupId = st.nextToken()
				dependency.artifactId = st.nextToken()
				if (st.hasMoreTokens()) {
					dependency.version = st.nextToken()
				}
			} else {
				throw new InvalidInitializrMetadataException('Invalid dependency, id should ' +
						'have the form groupId:artifactId[:version] but got ' + id)
			}
		}
	}

	static def getDefault(List elements) {
		for (DefaultIdentifiableElement element : elements) {
			if (element.default) {
				return element.id
			}
		}
		logger.warn('No default found amongst' + elements)
		return (elements.isEmpty() ? null : elements.get(0).id)
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	static class DependencyGroup {

		String name

		final List<Dependency> content = new ArrayList<Dependency>()

	}

	@ToString(ignoreNulls = true, includePackage = false)
	static class Dependency extends IdentifiableElement {

		@JsonIgnore
		List<String> aliases = []

		@JsonIgnore
		List<String> facets = []

		@JsonIgnore
		String groupId

		@JsonIgnore
		String artifactId

		@JsonIgnore
		String version

		/**
		 * Specify if the dependency has its coordinates set, i.e. {@code groupId}
		 * and {@code artifactId}.
		 */
		boolean hasCoordinates() {
			return groupId != null && artifactId != null
		}

		/**
		 * Define this dependency as a standard spring boot starter with the specified name
		 */
		def asSpringBootStarter(String name) {
			groupId = 'org.springframework.boot'
			artifactId = 'spring-boot-starter-' + name
		}

		/**
		 * Generate an id using the groupId and artifactId
		 */
		def generateId() {
			if (groupId == null || artifactId == null) {
				throw new IllegalArgumentException('Could not generate id for ' + this
						+ ': at least groupId and artifactId must be set.')
			}
			StringBuilder sb = new StringBuilder()
			sb.append(groupId).append(':').append(artifactId)
			id = sb.toString()
		}
	}

	static class Type extends DefaultIdentifiableElement {

		String action
	}

	static class Packaging extends DefaultIdentifiableElement {
	}

	static class JavaVersion extends DefaultIdentifiableElement {
	}

	static class Language extends DefaultIdentifiableElement {
	}

	static class BootVersion extends DefaultIdentifiableElement {
	}

	static class Defaults {
		String groupId = 'org.test'
		String artifactId
		String version = '0.0.1-SNAPSHOT'
		String name = 'demo'
		String description = 'Demo project for Spring Boot'
		String packageName
		String type
		String packaging
		String javaVersion
		String language
		String bootVersion

		/**
		 * Return the artifactId or the name of the project if none is set.
		 */
		String getArtifactId() {
			artifactId == null ? name : artifactId
		}

		/**
		 * Return the package name or the name of the project if none is set
		 */
		String getPackageName() {
			packageName == null ? name.replace('-', '.') : packageName
		}

	}

	/**
	 * Defines additional environment settings
	 */
	static class Env {

		String artifactRepository = 'https://repo.spring.io/release/'

		/**
		 * Create an URL suitable to download Spring Boot cli for the specified version and extension.
		 */
		String createCliDistributionURl(String version, String extension) {
			artifactRepository + "org/springframework/boot/spring-boot-cli/$version/spring-boot-cli-$version-bin.$extension"
		}

		void validate() {
			if (!artifactRepository.endsWith('/')) {
				artifactRepository = artifactRepository + '/'
			}
		}

	}

	static class DefaultIdentifiableElement extends IdentifiableElement {

		@JsonIgnore
		private boolean defaultValue

		void setDefault(boolean defaultValue) {
			this.defaultValue = defaultValue
		}

		boolean isDefault() {
			return this.defaultValue
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	static class IdentifiableElement {

		String name

		String id

		String getName() {
			(name != null ? name : id)
		}
	}
}
