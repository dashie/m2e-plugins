package com.eidosmedia.eclipse.maven.resources.remote;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;

/**
 * 
 * @author Maurizio Merli
 * 
 */
public class CheckLastModifiedVisitor implements IResourceProxyVisitor {

	private final long threashold;

	private long lastModified = IResource.NULL_STAMP;

	private boolean terminated = false;

	public CheckLastModifiedVisitor(long threashold) {
		this.threashold = threashold;
	}

	@Override
	public boolean visit(IResourceProxy proxy) throws CoreException {
		System.out.println(" >> visit >> " + proxy.requestFullPath());
		if (terminated) {
			return false;
		}
		IResource resource = proxy.requestResource();
		long value = (resource.getLocalTimeStamp() / 1000) * 1000;
		if (value > lastModified) {
			lastModified = value;
			if (lastModified > threashold) {
				terminated = true;
				return false;
			}
		}
		return true;
	}

	public long getLastModified() {
		return lastModified;
	}

	public boolean getResult() {
		return lastModified > threashold;
	}

}
