package com.eidosmedia.eclipse.maven.connectors;

import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Configurator extends AbstractProjectConfigurator {

	private static final Logger log = LoggerFactory.getLogger(Configurator.class);

	public Configurator() {

	}

	@Override
	public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {

	}

	@Override
	public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, MojoExecution execution,
			IPluginExecutionMetadata executionMetadata) {

		String artifactId = execution.getArtifactId();
		if (log.isDebugEnabled()) {
			log.debug("getBuildParticipant: artifactId={}", artifactId);
		}

		return new GenericBuildParticipant(execution, "sourceDirectory", "outputDirectory");
	}
}
