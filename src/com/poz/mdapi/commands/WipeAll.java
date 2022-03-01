package com.poz.mdapi.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.poz.mdapi.GenericCommand;
import com.poz.mdapi.MetadataApiUtil;
import com.poz.mdapi.MetadataType;
import com.poz.util.FileUtil;
import com.poz.util.UI;
import com.sforce.soap.metadata.DeployOptions;
import com.sforce.soap.metadata.PackageTypeMembers;
import com.sforce.ws.ConnectionException;

/**
 * @author pozil
 * Wipes ALL custom metadata from an org
 */
public class WipeAll extends GenericCommand {
	
	private static final MetadataType[] TARGET_MD_TYPES = new MetadataType[]{
		MetadataType.InstalledPackage,
		MetadataType.CustomApplication,
		MetadataType.CustomTab,
		MetadataType.ApexClass, 
		MetadataType.AuraDefinitionBundle,
		MetadataType.CustomObject,
		MetadataType.GlobalValueSet
		//, MetadataType.CONTENT_ASSET
	};
	
	private static final String DEPLOY_FOLDER	= "wipe-all";
	private static final String DEPLOY_ZIP		= "wipe-all.zip";
	
	public WipeAll(String[] args) {
		super(args);
		
		// Retrieve existing metadata
		System.out.println("Collecting metadata...");
		List<PackageTypeMembers> packageMembers;
		try {
			packageMembers = mdapiUtil.getCustomMetadata(TARGET_MD_TYPES);
			mdapiUtil.printMetaDataTypes(packageMembers);
		}
		catch (ConnectionException e) {
			System.err.println("Failed to retrieve metadata");
			e.printStackTrace(System.err);
			System.exit(-2);
			return;
		}
		
		// Check for metadata
		if (packageMembers.size() == 0) {
			System.out.println("Operation aborted: no metadata found.");
			System.exit(0);
		}
		
		// Write metadata list to a destructive manifest
		try {
			File deployDir = new File(DEPLOY_FOLDER);
			if (!deployDir.exists())
				deployDir.mkdirs();
			else if (!deployDir.isDirectory())
				throw new Exception(DEPLOY_FOLDER +" is not a directory");
			mdapiUtil.writeManifest(DEPLOY_FOLDER +"/"+ MetadataApiUtil.MANIFEST_PACKAGE, new ArrayList<PackageTypeMembers>());
			mdapiUtil.writeManifest(DEPLOY_FOLDER +"/"+ MetadataApiUtil.MANIFEST_DESTRUCTIVE, packageMembers);
		} catch (Exception e) {
			System.err.println("Manifest build failed");
			e.printStackTrace(System.err);
			System.exit(-3);
		}
		
		// User prompt to review content & confirm operation
		String userInput;
		do {
			userInput = UI.prompt("Please confirm mass deletion (yes/no): ");
		} while (!"yes".equalsIgnoreCase(userInput) && !"no".equalsIgnoreCase(userInput));
		if ("no".equalsIgnoreCase(userInput))
			System.exit(0);
		
		// Zip deploy folder
		try {
			FileUtil.zipFolder(DEPLOY_ZIP, DEPLOY_FOLDER);
			System.out.println(DEPLOY_ZIP +" created.");
		} catch (Exception e) {
			System.err.println("Manifest zip failed");
			e.printStackTrace(System.err);
			System.exit(-4);
		}
		
		// Deploy zip to org
		try {
			deployZip(DEPLOY_ZIP);
		} catch (Exception e) {
			System.err.println("Deployment failed");
			e.printStackTrace(System.err);
			System.exit(-5);
		}
	}
	
	private void deployZip(String zipPath) throws Exception {
		System.out.println("Deploying to server...");

		DeployOptions deployOptions = new DeployOptions();
        deployOptions.setIgnoreWarnings(false);
        deployOptions.setPerformRetrieve(false);
        deployOptions.setRollbackOnError(false);
        deployOptions.setPurgeOnDelete(true); // Skip recycle bin
        
        mdapiUtil.deployZip(zipPath, deployOptions);
	}
	
	public static void main(String[] args) throws ConnectionException {
		System.out.println("Running wipe-all command");
		new WipeAll(args);
	}
}
