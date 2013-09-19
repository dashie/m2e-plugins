package com.eidosmedia.eclipse.maven.resources.remote;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "com.eidosmedia.eclipse.maven.m2e-remote-resources"; //$NON-NLS-1$

	public static final String MAVEN_GROUP_ID = "org.apache.maven.plugins"; //$NON-NLS-1$

	public static final String MAVEN_ARTIFACT_ID = "maven-remote-resources-plugin"; //$NON-NLS-1$

	public static final String MAVEN_ID = MAVEN_GROUP_ID + ":" + MAVEN_ARTIFACT_ID; //$NON-NLS-1$

	private static Activator plugin;

	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

}
