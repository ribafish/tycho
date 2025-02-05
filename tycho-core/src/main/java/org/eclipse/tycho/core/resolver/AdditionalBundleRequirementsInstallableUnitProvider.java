/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.core.resolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.tycho.BuildProperties;
import org.eclipse.tycho.BuildPropertiesParser;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.TychoProjectManager;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.resolver.InstallableUnitProvider;

import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Processor;

/**
 * This provides P2 visible meta-data for bundles that are not expressed in the manifest (e.g.
 * build.properties derived)
 *
 */
@Component(role = InstallableUnitProvider.class, hint = "bundle-requirement")
public class AdditionalBundleRequirementsInstallableUnitProvider implements InstallableUnitProvider {
    @Requirement
    private Logger logger;
    @Requirement
    TychoProjectManager projectManager;

    @Requirement
    private BuildPropertiesParser buildPropertiesParser;

    @Override
    public Collection<IInstallableUnit> getInstallableUnits(MavenProject project, MavenSession session)
            throws CoreException {

        Optional<Processor> bndTychoProject = projectManager.getBndTychoProject(project);
        if (bndTychoProject.isPresent()) {
            try (Processor processor = bndTychoProject.get()) {
                List<IRequirement> requirements = getBndClasspathRequirements(processor);
                if (!requirements.isEmpty()) {
                    return InstallableUnitProvider.createIU(requirements, "bnd-classpath-requirements");
                }
            } catch (IOException e) {
                logger.warn("Can't determine classpath requirements from " + project.getId(), e);
            }
        } else if (projectManager.getTychoProject(project).isPresent()) {
            //"classic" pde project with build properties
            ReactorProject reactorProject = DefaultReactorProject.adapt(project);
            BuildProperties buildProperties = buildPropertiesParser.parse(reactorProject);
            List<IRequirement> additionalBundleRequirements = buildProperties.getAdditionalBundles().stream()
                    .map(bundleName -> MetadataFactory.createRequirement(BundlesAction.CAPABILITY_NS_OSGI_BUNDLE,
                            bundleName, VersionRange.emptyRange, null, true, true))
                    .toList();
            return InstallableUnitProvider.createIU(additionalBundleRequirements, "additional-bundle-requirements");
        }
        return Collections.emptyList();
    }

    public static List<IRequirement> getBndClasspathRequirements(Processor processor) {
        //See https://bnd.bndtools.org/instructions/buildpath.html
        String buildPath = processor.mergeProperties(Constants.BUILDPATH);
        if (buildPath != null && !buildPath.isBlank()) {
            return Arrays.stream(buildPath.split(","))
                    .map(bundleName -> MetadataFactory.createRequirement(BundlesAction.CAPABILITY_NS_OSGI_BUNDLE,
                            bundleName.trim(), VersionRange.emptyRange, null, true, true))
                    .toList();
        }
        return Collections.emptyList();
    }

}
