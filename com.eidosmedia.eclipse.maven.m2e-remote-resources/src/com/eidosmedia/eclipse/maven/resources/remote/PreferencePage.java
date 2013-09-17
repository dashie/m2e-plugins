package com.eidosmedia.eclipse.maven.resources.remote;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * @author Maurizio Merli
 */
public class PreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public PreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Maven remote-resources-plugin preferences page.");
	}

	public void createFieldEditors() {
		addField(new BooleanFieldEditor(PreferenceConstants.P_CLEAN_DESTINATION_FOLDER, "&Clean destination folder", getFieldEditorParent()));
	}

	public void init(IWorkbench workbench) {
	}

}