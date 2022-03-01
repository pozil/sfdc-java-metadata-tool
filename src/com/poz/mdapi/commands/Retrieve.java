package com.poz.mdapi.commands;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.poz.mdapi.GenericCommand;
import com.poz.mdapi.MetadataApiUtil;
import com.poz.mdapi.MetadataType;
import com.poz.util.UI;
import com.sforce.soap.metadata.PackageTypeMembers;

public class Retrieve extends GenericCommand {
	
	private static final String ZIP_NAME = "retrieved.zip";

	
	public Retrieve(String[] args) {
		super(args);
		
		// Check & parse manifest
		File packageFile = new File(MetadataApiUtil.MANIFEST_PACKAGE);
		checkForManifest(packageFile);
		com.sforce.soap.metadata.Package packageManifest;
		try {
			packageManifest = mdapiUtil.parsePackageManifest(packageFile);
			mdapiUtil.printMetaDataTypes(Arrays.asList(packageManifest.getTypes()));
		} catch (Exception e) {
			System.err.println("Failed to parse "+ MetadataApiUtil.MANIFEST_PACKAGE);
			e.printStackTrace(System.err);
			System.exit(-2);
			return;
		}
		
		// Retrieve zip
		try {
			mdapiUtil.retrieveZip(packageManifest, ZIP_NAME);
		} catch (Exception e) {
			System.err.println("Operation failed: "+ e.getMessage());
			e.printStackTrace(System.err);
			System.exit(-3);
		}
	}
	
	private void checkForManifest(File packageManifest) {
		if (!packageManifest.exists()) {
			System.out.println(MetadataApiUtil.MANIFEST_PACKAGE + " not found.");
			String userInput = UI.prompt("Should we create a package.xml template for you? (*yes/no) ");
			if ("yes".equalsIgnoreCase(userInput) || "".equals(userInput)) {
				try {
					generatePackageManifest();
					System.out.println(MetadataApiUtil.MANIFEST_PACKAGE +" template created.");
				} catch (IOException e) {
					System.err.println("Failed to generate "+ MetadataApiUtil.MANIFEST_PACKAGE +" template");
					e.printStackTrace(System.err);
					System.exit(-2);
				}
			}
			else {
				System.exit(0);
			}
			userInput = UI.prompt("Resume operation? (*yes/no) ");
			if (!"yes".equalsIgnoreCase(userInput) && !"".equals(userInput)) {
				System.exit(0);
			}
		}
	}
	
	private void generatePackageManifest() throws IOException {
		List<PackageTypeMembers> packageTypes = new ArrayList<>();
		for (MetadataType type : MetadataType.values()) {
			PackageTypeMembers packageType = new PackageTypeMembers();
			packageType.setName(type.toString());
			packageType.setMembers(new String[]{"*"});
			packageTypes.add(packageType);
		}
		mdapiUtil.writeManifest(MetadataApiUtil.MANIFEST_PACKAGE, packageTypes);
	}
	
	public static void main(String[] args) {
		System.out.println("Running retrieve command");
		new Retrieve(args);
	}

}
