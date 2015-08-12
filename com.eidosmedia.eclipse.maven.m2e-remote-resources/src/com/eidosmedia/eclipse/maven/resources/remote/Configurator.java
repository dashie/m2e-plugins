package com.eidosmedia.eclipse.maven.resources.remote;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Maurizio Merli
 * 
 */
public class Configurator extends AbstractProjectConfigurator {

	private static final Logger log = LoggerFactory.getLogger(Configurator.class);

	public Configurator() {

	}

	@Override
	public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {

		final IMavenProjectFacade mavenProjectFacade = request.getMavenProjectFacade();
		final IProject project = mavenProjectFacade.getProject();
		final IMavenProjectRegistry projectRegistry = MavenPlugin.getMavenProjectRegistry();

		Set<String> bundleSet = getResourceBundles(mavenProjectFacade, monitor);
		if (bundleSet.isEmpty()) {
			return;
		}

		IProjectDescription description = project.getDescription();

		IProject[] oldRefs = description.getReferencedProjects();
		Set<IProject> refs = new HashSet<IProject>();
		if (oldRefs != null) {
			refs.addAll(Arrays.asList(oldRefs));
		}

		IMavenProjectFacade[] mavenProjectFacades = projectRegistry.getProjects();
		for (IMavenProjectFacade facade : mavenProjectFacades) {
			IProject pi = facade.getProject();
			if (pi.equals(project)) {
				continue;
			}
			MavenProject mp = facade.getMavenProject(null);
			if (mp == null) {
				log.error("configure: [" + project + "] maven project reference is null " + pi);
			} else {
				if (addToReferences(mp, bundleSet)) {
					log.info("configure: [" + project + "] add maven project reference to " + pi);
					refs.add(pi);
				}
			}
		}

		IProject[] array = refs.toArray(new IProject[refs.size()]);
		description.setReferencedProjects(array);
		project.setDescription(description, monitor);
	}

	@Override
	public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, MojoExecution execution, IPluginExecutionMetadata executionMetadata) {

		return new BuildParticipant(execution);
	}

	/**
	 * 
	 * @param mavenProjectFacade
	 * @return
	 */
	private Set<String> getResourceBundles(IMavenProjectFacade mavenProjectFacade, IProgressMonitor monitor) throws CoreException {
		Set<String> bundles = new HashSet<String>();
		List<MojoExecution> executions = mavenProjectFacade.getMojoExecutions(Activator.MAVEN_GROUP_ID, Activator.MAVEN_ARTIFACT_ID, monitor, "process");
		for (MojoExecution execution : executions) {
			Xpp3Dom node = execution.getConfiguration();
			if (node != null) {
				node = node.getChild("resourceBundles");
				if (node != null) {
					Xpp3Dom[] nodes = node.getChildren("resourceBundle");
					if (nodes != null) {
						for (Xpp3Dom n : nodes) {
							String bundle = n.getValue();
							bundles.add(bundle);
						}
					}
				}
			}
		}
		return bundles;
	}

	/**
	 * 
	 * @param mavenProject
	 * @param bundleSet
	 * @return
	 */
	private boolean addToReferences(MavenProject mavenProject, Set<String> bundleSet) {
		String shortArtifactKey = mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() + ":" + mavenProject.getVersion();
		if (!bundleSet.contains(shortArtifactKey)) {
			return false;
		}
		Plugin plugin = mavenProject.getPlugin(Activator.MAVEN_ID);
		if (plugin == null) {
			return false;
		}
		return true;
	}
}
