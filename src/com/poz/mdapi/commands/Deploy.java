package com.poz.mdapi.commands;

import java.io.File;

import com.poz.mdapi.GenericCommand;
import com.poz.util.UI;
import com.sforce.soap.metadata.DeployOptions;

public class Deploy extends GenericCommand {
	
	private final static String DEFAULT_DEPLOY_FILE = "deploy.zip";
	
	public Deploy(String[] args) {
		super(args);
		
		// Get deploy file
		File deployFile;
		try {
			deployFile = getDeployFile(args);
		}
		catch (Exception e) {
			System.err.println("Failed to find deploy file");
			e.printStackTrace(System.err);
			System.exit(-2);
			return;
		}
		
		// Deploy zip to org
		try {
			deployZip(deployFile.getAbsolutePath());
		} catch (Exception e) {
			System.err.println("Deployment failed");
			e.printStackTrace(System.err);
			System.exit(-3);
			return;
		}
	}
	
	private File getDeployFile(String[] args) throws Exception {
		String deployFileName = DEFAULT_DEPLOY_FILE;
		if (args.length == 4) { // CLI mode
			deployFileName =  args[3];
		}
		else { // Interactive mode
			String userInput = UI.prompt("Enter path of file to deploy (deploy.zip): ");
			if (!"".equals(userInput))
				deployFileName = userInput;	
		}
		
		// Check file
		File deployFile = new File(deployFileName);
		if (!deployFile.exists())
			throw new Exception("Deploy file not found.");
		if (!deployFile.isFile())
			throw new Exception("Provided path does not denote a file.");
		if (!deployFile.getName().endsWith(".zip"))
			throw new Exception("Provided path does not denote a ZIP file.");
		return deployFile;
	}
	
	private void deployZip(String zipPath) throws Exception {
		System.out.println("Deploying to server...");

		DeployOptions deployOptions = new DeployOptions();
        deployOptions.setIgnoreWarnings(false);
        deployOptions.setPerformRetrieve(false);
        deployOptions.setRollbackOnError(true);
        deployOptions.setPurgeOnDelete(true); // Skip recycle bin
        
        mdapiUtil.deployZip(zipPath, deployOptions);
	}
	
	public static void main(String[] args) {
		System.out.println("Running deploy command");
		new Deploy(args);
	}

}
