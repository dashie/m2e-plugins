package com.eidosmedia.eclipse.maven.libsass;

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

	private static final Logger log = LoggerFactory
			.getLogger(Configurator.class);

	public Configurator() {

	}

	@Override
	public void configure(ProjectConfigurationRequest request,
			IProgressMonitor monitor) throws CoreException {

	}

	@Override
	public AbstractBuildParticipant getBuildParticipant(
			IMavenProjectFacade projectFacade, MojoExecution execution,
			IPluginExecutionMetadata executionMetadata) {

		String artifactId = execution.getArtifactId();
		if (log.isDebugEnabled()) {
			log.debug("getBuildParticipant: artifactId={}", artifactId);
		}

		String inputPathParam;
		String outputPathParam;
		
		if ("libsass-maven-plugin".equals(artifactId)) {
			inputPathParam = "inputPath";
			outputPathParam = "outputPath";
		} else if ("sass-maven-plugin".equals(artifactId)) {
			inputPathParam = "sassSourceDirectory";
			outputPathParam = "destination";
		} else {
			throw new IllegalStateException("Unsupported artifactId <"
					+ artifactId + ">");
		}

		return new BuildParticipant(execution, inputPathParam, outputPathParam);
	}
}
