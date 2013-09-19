package com.eidosmedia.eclipse.maven.resources.remote;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Scanner;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
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
		log.debug("execution: {}", mojoExecution);

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
		final MavenProject mavenProject = currentProject.getMavenProject();
		final BuildContext buildContext = getBuildContext();
		final IMavenProjectRegistry projectRegistry = MavenPlugin.getMavenProjectRegistry();

		ArtifactKey artifactKey = currentProject.getArtifactKey();
		String shortArtifactKey = artifactKey.getGroupId() + ":" + artifactKey.getArtifactId() + ":" + artifactKey.getVersion();
		log.debug("artifact key: {}", shortArtifactKey);

		File basedir = mavenProject.getBasedir();
		File sourcesDirectory = new File(basedir, "src");

		File resourcesDirectory = maven.getMojoParameterValue(getSession(), getMojoExecution(), "resourcesDirectory", File.class);
		File outputDirectory = maven.getMojoParameterValue(getSession(), getMojoExecution(), "outputDirectory", File.class);
		File remoteResourcesDescriptor = new File(outputDirectory, "META-INF/maven/remote-resources.xml");

		String preprocessedFiles = null; // (String) buildContext.getValue("preprocessedFiles");

		if (remoteResourcesDescriptor.exists()) {
			if ((INCREMENTAL_BUILD == kind || AUTO_BUILD == kind) && preprocessedFiles == null) {
				log.debug("scan resources {}", resourcesDirectory);
				Scanner ds = buildContext.newScanner(resourcesDirectory);
				ds.scan();
				String[] files = ds.getIncludedFiles();
				if (files == null || files.length <= 0) {
					log.debug("build check: no resource changes");
					log.debug("scan deleted resources {}", resourcesDirectory);
					ds = buildContext.newDeleteScanner(resourcesDirectory);
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
		} else {
			log.debug("build check: remote resources descriptor does not exists");
		}

		final Set<IProject> result = super.build(kind, monitor);
		if (outputDirectory != null && outputDirectory.exists()) {
			log.debug("refresh output directory: {}", outputDirectory);
			buildContext.refresh(outputDirectory);
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
		Set<String> bundleSet = new HashSet<String>(bundles.size());
		for (String bundle : bundles) {
			log.debug("remote bundle: {}", bundle);
			bundleSet.add(bundle);
		}

		File outputDirectory = maven.getMojoParameterValue(getSession(), getMojoExecution(), "outputDirectory", File.class);

		long lastModified = (outputDirectory.lastModified() / 1000) * 1000; // remove millis part

		Set<IProject> dependencyProjects = new HashSet<IProject>();

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
				continue;
			}
			log.debug("check workspace bundle: {}", shortArtifactKey);

			IProject dependencyProject = mavenProject.getProject();
			dependencyProjects.add(dependencyProject);

			if (skip) {
				// TODO visits only exported resources
				IPath path = mavenProject.getOutputLocation();
				IFolder outputLocation = workspace.getRoot().getFolder(path);
				CheckLastModifiedVisitor visitor = new CheckLastModifiedVisitor(lastModified);
				outputLocation.accept(visitor, IContainer.INCLUDE_PHANTOMS);
				if (visitor.getResult()) {
					skip = false;
				}
			}
		}

		if (buildContext.isIncremental() && skip) {
			log.debug("check: no remote resources to process");
			return dependencyProjects;
		}

		boolean cleanDestinationFolder = false;
		IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
		if (preferenceStore != null && preferenceStore.getBoolean(PreferenceConstants.P_CLEAN_DESTINATION_FOLDER)) {
			log.debug("cleanDestinationFolder option is active");
			final MavenProject mavenProject = currentProject.getMavenProject();
			String buildDirectoryPath = mavenProject.getBuild().getDirectory() + File.separatorChar;
			String outputDirectoryPath = outputDirectory.getCanonicalPath();
			if (outputDirectoryPath.startsWith(buildDirectoryPath)) {
				boolean enableClean = false;
				Xpp3Dom conf = getMojoExecution().getConfiguration();
				if (conf != null) {
					conf = conf.getChild("properties");
					if (conf != null) {
						conf = conf.getChild("cleanOutputDirectory");
						if (conf != null && "true".equalsIgnoreCase(conf.getValue())) {
							enableClean = true;
						}
					}
				}
				if (enableClean) {
					cleanDestinationFolder = true;
				} else {
					log.warn("cleanOutputDirectory not enable");
				}
			} else {
				log.warn("output directory path is not under 'target' folder, clean ignored");
			}
		}

		if (cleanDestinationFolder) {
			log.debug("clean destination folder");
			delete(outputDirectory, false);
		}

		log.debug("do mojo...");
		Set<IProject> result = super.build(kind, monitor);
		if (result == null) {
			result = dependencyProjects;
		} else {
			result.addAll(dependencyProjects);
		}

		log.debug("update destination folder timestamp");
		outputDirectory.setLastModified(System.currentTimeMillis()); // touch output folder

		log.debug("output resources: {}", outputDirectory);
		if (outputDirectory != null) {
			buildContext.refresh(outputDirectory);
		}

		return result;
	}

	/**
	 * 
	 * @param file
	 */
	private void delete(File file, boolean deleteParent) {
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				delete(child, true);
			}
		}
		if (deleteParent) {
			file.delete();
		}
	}
}
