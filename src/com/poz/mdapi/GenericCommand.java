package com.poz.mdapi;

import com.poz.util.UI;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.ws.ConnectionException;

public class GenericCommand {

	protected static final double API_VERSION = 54.0;
	private static final String ORG_TYPE_PRODUCTION = "production";
	
	protected MetadataConnection connection;
	protected MetadataApiUtil mdapiUtil;
	
	private boolean isProduction = true;
	private String username, password;
	
	public GenericCommand(String[] args) {
		
		System.out.println();
		getLoginInfo(args);
		
		System.out.println("Connecting as user "+ username);
		System.out.println("Org type: "+ (isProduction ? "production" : "sandbox"));
		try {
			connection = MetadataLoginUtil.login(username, password, isProduction, API_VERSION);
			mdapiUtil = new MetadataApiUtil(connection, API_VERSION);
		}
		catch (ConnectionException e) {
			System.err.println("Failed to connect to org");
			e.printStackTrace(System.err);
			System.exit(-1);
		}
	}
	
	public void getLoginInfo(String[] args) {
		String orgType = ORG_TYPE_PRODUCTION;
		if (args.length == 0) {
			username = UI.prompt("Username: ");
			password = UI.prompt("Password: ");
			orgType = UI.prompt("Org type [production (default) / sandbox]: ");
		}
		else if (args.length == 2) {
			username = args[0];
			password = args[1];
		}
		else if (args.length > 3) {
			username = args[0];
			password = args[1];
			orgType =  args[2];
		}
		else
			throw new RuntimeException("Unsupported parameters");
		
		isProduction = "".equals(orgType) || ORG_TYPE_PRODUCTION.equalsIgnoreCase(orgType);
	}
}
