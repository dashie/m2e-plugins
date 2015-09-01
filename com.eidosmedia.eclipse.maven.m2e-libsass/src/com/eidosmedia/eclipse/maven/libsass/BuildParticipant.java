package com.eidosmedia.eclipse.maven.libsass;

import java.io.File;
import java.util.Set;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Scanner;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * 
 */
public class BuildParticipant extends MojoExecutionBuildParticipant {

	private static final Logger log = LoggerFactory.getLogger(BuildParticipant.class);

	private final String inputPathParam;

	private final String outputPathParam;
	
	public BuildParticipant(MojoExecution execution, String inputPathParam,  String outputPathParam) {
		super(execution, true, true);
		this.inputPathParam = inputPathParam;
		this.outputPathParam = outputPathParam;
	}

	@Override
	public Set<IProject> build(int kind, final IProgressMonitor monitor)
			throws Exception {

		final MojoExecution mojoExecution = getMojoExecution();

		if (mojoExecution == null) {
			return null;
		}

		final String phase = mojoExecution.getLifecyclePhase();
		log.debug("phase: {}", phase);

		final String goal = mojoExecution.getGoal();
		log.debug("goal: {}", goal);

		final IMaven maven = MavenPlugin.getMaven();
		final IMavenProjectFacade currentProject = getMavenProjectFacade();
		final BuildContext buildContext = getBuildContext();
		final IMavenProjectRegistry projectRegistry = MavenPlugin.getMavenProjectRegistry();

		ArtifactKey artifactKey = currentProject.getArtifactKey();
		String shortArtifactKey = artifactKey.getGroupId() + ":"
				+ artifactKey.getArtifactId() + ":" + artifactKey.getVersion();
		log.debug("artifact key: {}", shortArtifactKey);

		MavenProject mavenProject = currentProject.getMavenProject();
		// File basedir = mavenProject.getBasedir();
		// File inputPath = new File(basedir, "src");
		File inputPath = maven.getMojoParameterValue(mavenProject, mojoExecution, inputPathParam, File.class, monitor);
		
		String outputDirectoryPath = mavenProject.getBuild().getDirectory();
		File outputDirectory = new File(outputDirectoryPath);

		if (INCREMENTAL_BUILD == kind || AUTO_BUILD == kind) {
			log.debug("scan resources {}", inputPath);
			Scanner ds = buildContext.newScanner(inputPath);
			ds.scan();
			String[] files = ds.getIncludedFiles();
			if (files == null || files.length <= 0) {
				log.debug("build check: no resource changes");
				log.debug("scan deleted resources {}", inputPath);
				ds = buildContext.newDeleteScanner(inputPath);
				ds.scan();
				files = ds.getIncludedFiles();
				if (files == null || files.length <= 0) {
					return null;
				} else {
					log.debug("build check: resources deleted");
				}
			} else {
				log.debug("build check: resources changed");
			}
		} else {
			log.debug("build check: full build");
		}

		final Set<IProject> result = super.build(kind, monitor);

		IProject project = currentProject.getProject();
		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);

		if (outputDirectory != null && outputDirectory.exists()) {
			log.debug("refresh output directory: {}", outputDirectory);
			buildContext.refresh(outputDirectory);
		}

		return result;
	}

}
