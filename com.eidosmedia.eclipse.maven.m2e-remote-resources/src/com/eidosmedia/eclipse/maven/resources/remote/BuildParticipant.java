package com.eidosmedia.eclipse.maven.resources.remote;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Scanner;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
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
 * @author Maurizio Merli
 * 
 */
public class BuildParticipant extends MojoExecutionBuildParticipant {

	private static final Logger log = LoggerFactory.getLogger(BuildParticipant.class);

	public BuildParticipant(MojoExecution execution) {
		super(execution, true, true);
	}

	@Override
	public Set<IProject> build(int kind, IProgressMonitor monitor) throws Exception {

		final MojoExecution mojoExecution = getMojoExecution();

		if (mojoExecution == null) {
			return null;
		}

		final String phase = mojoExecution.getLifecyclePhase();
		log.debug("phase: {}", phase);

		final String goal = mojoExecution.getGoal();
		log.debug("goal: {}", goal);

		if ("bundle".equalsIgnoreCase(goal)) {
			return buildBundle(kind, monitor);
		} else if ("process".equalsIgnoreCase(goal)) {
			return buildProcess(kind, monitor);
		} else {
			return super.build(kind, monitor);
		}
	}

	/**
	 * 
	 * @param kind
	 * @param monitor
	 * @return
	 * @throws Exception
	 */
	private Set<IProject> buildBundle(int kind, IProgressMonitor monitor) throws Exception {

		log.info("process \"bundle\" goal");

		final IMaven maven = MavenPlugin.getMaven();
		final IMavenProjectFacade currentProject = getMavenProjectFacade();
		final BuildContext buildContext = getBuildContext();
		final IMavenProjectRegistry projectRegistry = MavenPlugin.getMavenProjectRegistry();

		ArtifactKey artifactKey = currentProject.getArtifactKey();
		String shortArtifactKey = artifactKey.getGroupId() + ":" + artifactKey.getArtifactId() + ":" + artifactKey.getVersion();
		log.debug("artifact key: {}", shortArtifactKey);

		File resourcesDirectory = maven.getMojoParameterValue(getSession(), getMojoExecution(), "resourcesDirectory", File.class);
		File outputDirectory = maven.getMojoParameterValue(getSession(), getMojoExecution(), "outputDirectory", File.class);
		File remoteResourcesDescriptor = new File(outputDirectory, "META-INF/maven/remote-resources.xml");

		if (remoteResourcesDescriptor.exists()) {
			if (INCREMENTAL_BUILD == kind || AUTO_BUILD == kind) {
				log.debug("scan resources {}", resourcesDirectory);
				Scanner ds = buildContext.newScanner(resourcesDirectory);
				ds.scan();
				String[] includedFiles = ds.getIncludedFiles();
				if (includedFiles == null || includedFiles.length <= 0) {
					log.debug("build check: no resource changes");
					return null;
				} else {
					log.debug("build check: resources changed");
				}
			} else {
				log.debug("build check: full build");
			}
		} else {
			log.debug("build check: remote resources descriptor does not exists");
		}

		final Set<IProject> result = super.build(kind, monitor);
		if (outputDirectory != null) {
			log.debug("refresh output directory: {}", outputDirectory);
			buildContext.refresh(outputDirectory);
		}

		// rebuild projects
		IMavenProjectFacade[] mavenProjects = projectRegistry.getProjects();
		for (IMavenProjectFacade mavenProjectFacade : mavenProjects) {
			if (mavenProjectFacade.equals(currentProject)) {
				continue;
			}
			MavenProject mavenProject = mavenProjectFacade.getMavenProject();
			Plugin plugin = mavenProject.getPlugin("org.apache.maven.plugins:maven-remote-resources-plugin");
			if (plugin == null) {
				continue;
			}
			boolean rebuild = false;
			Xpp3Dom pluginConf = (Xpp3Dom) plugin.getConfiguration();
			List<PluginExecution> executions = plugin.getExecutions();
			for (PluginExecution execution : executions) {
				List<String> goals = execution.getGoals();
				if (goals.contains("process")) {
					Xpp3Dom executionConf = (Xpp3Dom) execution.getConfiguration();
					Xpp3Dom configuration = Xpp3Dom.mergeXpp3Dom(executionConf, pluginConf);
					Xpp3Dom resourceBundlesNode = configuration.getChild("resourceBundles");
					if (resourceBundlesNode != null) {
						Xpp3Dom[] resourceBundles = resourceBundlesNode.getChildren("resourceBundle");
						for (Xpp3Dom resourceBundle : resourceBundles) {
							String bundleKey = resourceBundle.getValue();
							if (shortArtifactKey.equals(bundleKey)) {
								rebuild = true;
								break;
							}
						}
					}
				}
			}
			if (rebuild) {
				IProject project = mavenProjectFacade.getProject();
				log.debug("build project {}", project);
				rebuilProject(project);
			}
		}

		return result;
	}

	/**
	 * 
	 * @param kind
	 * @param monitor
	 * @return
	 * @throws Exception
	 */
	private Set<IProject> buildProcess(int kind, IProgressMonitor monitor) throws Exception {

		log.info("process \"process\" goal");

		final IMaven maven = MavenPlugin.getMaven();
		final IMavenProjectFacade currentProject = getMavenProjectFacade();
		final BuildContext buildContext = getBuildContext();
		final IMavenProjectRegistry projectRegistry = MavenPlugin.getMavenProjectRegistry();
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();

		List<String> bundles = maven.getMojoParameterValue(getSession(), getMojoExecution(), "resourceBundles", List.class);
		Set<String> bundleSet = new HashSet<>(bundles.size());
		for (String bundle : bundles) {
			log.debug("remote bundle: {}" + bundle);
			bundleSet.add(bundle);
		}

		File outputDirectory = maven.getMojoParameterValue(getSession(), getMojoExecution(), "outputDirectory", File.class);
		long lastModified = (outputDirectory.lastModified() / 1000) * 1000; // remove millis part

		if (buildContext.isIncremental()) {
			boolean skip = true;
			IMavenProjectFacade[] mavenProjects = projectRegistry.getProjects();
			for (IMavenProjectFacade mavenProject : mavenProjects) {

				if (mavenProject.equals(currentProject)) {
					continue;
				}

				ArtifactKey artifactKey = mavenProject.getArtifactKey();
				String shortArtifactKey = artifactKey.getGroupId() + ":" + artifactKey.getArtifactId() + ":" + artifactKey.getVersion();
				if (!bundleSet.contains(shortArtifactKey)) {
					log.debug("skip workspace bundle: {}", shortArtifactKey);
				}
				log.debug("check workspace bundle: {}", shortArtifactKey);

				// TODO visits only exported resources
				IPath path = mavenProject.getOutputLocation();
				IFolder outputLocation = workspace.getRoot().getFolder(path);
				CheckLastModifiedVisitor visitor = new CheckLastModifiedVisitor(lastModified);
				outputLocation.accept(visitor, IContainer.INCLUDE_PHANTOMS);
				if (visitor.getResult()) {
					skip = false;
					break;
				}
			}
			if (skip) {
				log.debug("check: no remote resources to process");
				return null;
			}
		}

		final Set<IProject> result = super.build(kind, monitor);
		outputDirectory.setLastModified(System.currentTimeMillis()); // touch output folder

		log.debug("output resources: {}", outputDirectory);
		if (outputDirectory != null) {
			buildContext.refresh(outputDirectory);
		}

		return result;
	}

	/**
	 * 
	 * @param project
	 */
	private void rebuilProject(final IProject project) {
		// Schedule a build job, because I cannot process another concurrent build during the current build (eclipse ignores it)
		Job job = new Job("Refresh MAVEN project") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					project.build(IncrementalProjectBuilder.FULL_BUILD, "org.eclipse.m2e.core.maven2Builder", null, monitor);
					return Status.OK_STATUS;
				} catch (Exception ex) {
					return Status.CANCEL_STATUS;
				}
			}
		};
		job.schedule();
	}

}
