package com.poz.mdapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.poz.util.UI;
import com.poz.util.FileUtil;
import com.sforce.soap.metadata.AsyncResult;
import com.sforce.soap.metadata.CodeCoverageWarning;
import com.sforce.soap.metadata.DeployDetails;
import com.sforce.soap.metadata.DeployMessage;
import com.sforce.soap.metadata.DeployOptions;
import com.sforce.soap.metadata.DeployResult;
import com.sforce.soap.metadata.FileProperties;
import com.sforce.soap.metadata.ListMetadataQuery;
import com.sforce.soap.metadata.ManageableState;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.metadata.PackageTypeMembers;
import com.sforce.soap.metadata.RetrieveMessage;
import com.sforce.soap.metadata.RetrieveRequest;
import com.sforce.soap.metadata.RetrieveResult;
import com.sforce.soap.metadata.RetrieveStatus;
import com.sforce.soap.metadata.RunTestFailure;
import com.sforce.soap.metadata.RunTestsResult;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.bind.TypeMapper;
import com.sforce.ws.parser.XmlOutputStream;

public class MetadataApiUtil {

	public static final String MANIFEST_PACKAGE		= "package.xml";
	public static final String MANIFEST_DESTRUCTIVE	= "destructiveChanges.xml";

	// one second in milliseconds
	private static final long ONE_SECOND = 1000;
	// maximum number of attempts to deploy the zip file
	private static final int MAX_NUM_POLL_REQUESTS = 50;


	private MetadataConnection connection;
	private double apiVersion;

	public MetadataApiUtil(MetadataConnection connection, double apiVersion) {
		this.connection = connection;
		this.apiVersion = apiVersion;
	}

	/**
	 * Builds a list of all metadata of the given types
	 * @param types metadata types that are retrieved. If null, all metadata types are retrieved.
	 * @return list of metadata
	 * @throws ConnectionException
	 */
	public List<PackageTypeMembers> getCustomMetadata(MetadataType types[]) throws ConnectionException {
		if (types == null || types.length == 0)
			types = MetadataType.values();
		List<PackageTypeMembers> packageMembers = new ArrayList<>();
		for (MetadataType type : types) {
			List<String> objects = getUnmanagedMetadataObjects(type);
			// Apply filter on custom apps
			if (type == MetadataType.CustomApplication)
				objects = filterCustomApplications(objects);
			// Assemble manifest
			if (objects.size() > 0) {
				PackageTypeMembers packageType = new PackageTypeMembers();
				packageType.setName(type.toString());
				packageType.setMembers(objects.toArray(new String[objects.size()]));
				packageMembers.add(packageType);
			}
		}
		return packageMembers;
	}

	/**
	 * Writes a XML manifest file with the provided entries
	 * @param filePath path of the generated manifest XML file
	 * @param listPackageTypes list of metadata to be included in the manifest
	 * @throws IOException
	 */
	public void writeManifest(String filePath, List<PackageTypeMembers> listPackageTypes) throws IOException {
		com.sforce.soap.metadata.Package manifest = new com.sforce.soap.metadata.Package();
		PackageTypeMembers[] packageTypesArray = new PackageTypeMembers[listPackageTypes.size()];
		manifest.setTypes(listPackageTypes.toArray(packageTypesArray));
		manifest.setVersion(Double.toString(apiVersion));

		try (
				FileOutputStream fos = new FileOutputStream(new File(filePath));
				XmlOutputStream xout = new XmlOutputStream(fos, true);
			)
		{
			xout.setPrefix("","http://soap.sforce.com/2006/04/metadata");
			xout.startDocument();
			manifest.write(new QName("http://soap.sforce.com/2006/04/metadata","Package"), xout, new TypeMapper());
			xout.endDocument();
		}
	}

	/**
	 * Gets all metadata of a given type
	 * @param metadataType
	 * @return list of metadata of a given type
	 * @throws ConnectionException
	 */
	public List<String> getUnmanagedMetadataObjects(MetadataType metadataType) throws ConnectionException {
		List<String> mdObjectNames = new ArrayList<>();
		ListMetadataQuery query = new ListMetadataQuery();
		query.setType(metadataType.toString());
		FileProperties[] results = connection.listMetadata(new ListMetadataQuery[] { query }, apiVersion);
		if (results != null) {
			for (FileProperties object : results) {
				if (object.getManageableState() == ManageableState.unmanaged) {
					mdObjectNames.add(object.getFullName());
				}
			}
		}
		return mdObjectNames;
	}

	public DeployResult deployZip(String zipPath, DeployOptions deployOptions) throws Exception {
		byte zipBytes[] = FileUtil.readFile(zipPath);

		AsyncResult asyncResult = connection.deploy(zipBytes, deployOptions);
		DeployResult result = waitForDeployCompletion(asyncResult.getId());
		if (result.isSuccess()) 
			printDeploySuccess(result);
		else {
			printDeployErrors(result, "Final list of failures:\n");
			throw new Exception("Deployment failed");
		}
		return result;
	}

	private DeployResult waitForDeployCompletion(String asyncResultId) throws Exception {
		int poll = 0;
		long waitTimeMilliSecs = ONE_SECOND;
		DeployResult deployResult;
		boolean fetchDetails;
		do {
			Thread.sleep(waitTimeMilliSecs);
			// double the wait time for the next iteration

			waitTimeMilliSecs *= 2;
			if (poll++ > MAX_NUM_POLL_REQUESTS) {
				throw new Exception("Request timed out after "+ MAX_NUM_POLL_REQUESTS +" retries.");
			}
			// Fetch in-progress details once for every 3 polls
			fetchDetails = (poll % 3 == 0);

			deployResult = connection.checkDeployStatus(asyncResultId, fetchDetails);
			System.out.println("Status: " + deployResult.getStatus());
			if (!deployResult.isDone() && fetchDetails) {
				printDeployErrors(deployResult, "Failures for deployment in progress:\n");
			}
		}
		while (!deployResult.isDone());

		if (!deployResult.isSuccess() && deployResult.getErrorStatusCode() != null) {
			throw new Exception(deployResult.getErrorStatusCode() + " msg: " + deployResult.getErrorMessage());
		}

		if (!fetchDetails) {
			// Get the final result with details if we didn't do it in the last attempt.
			deployResult = connection.checkDeployStatus(asyncResultId, true);
		}

		return deployResult;
	}
	
	/**
	 * Retrieves the metadata specified in packageManifest and extracts it to a zip file
	 * @param packageManifest
	 * @param zipPath
	 * @throws Exception
	 */
	public void retrieveZip(com.sforce.soap.metadata.Package packageManifest, String zipPath) throws Exception {
        RetrieveRequest retrieveRequest = new RetrieveRequest();
        // The version in package.xml overrides the version in RetrieveRequest
        retrieveRequest.setApiVersion(apiVersion);
        retrieveRequest.setUnpackaged(packageManifest);

        AsyncResult asyncResult = connection.retrieve(retrieveRequest);
        RetrieveResult result = waitForRetrieveCompletion(asyncResult);

        if (result.getStatus() == RetrieveStatus.Failed) {
            throw new Exception(result.getErrorStatusCode() + " msg: " + result.getErrorMessage());
        } else if (result.getStatus() == RetrieveStatus.Succeeded) {  
	        // Print out any warning messages
	        StringBuilder stringBuilder = new StringBuilder();
	        if (result.getMessages() != null) {
	            for (RetrieveMessage rm : result.getMessages()) {
	                stringBuilder.append(rm.getFileName() + " - " + rm.getProblem() + "\n");
	            }
	        }
	        if (stringBuilder.length() > 0) {
	            System.out.println("Retrieve warnings:\n" + stringBuilder);
	        }
	
	        System.out.println("Writing results to "+ zipPath);
	        FileUtil.writeFile(zipPath, result.getZipFile());
	        System.out.println("Done.");
        }
    }
	
	private RetrieveResult waitForRetrieveCompletion(AsyncResult asyncResult) throws Exception {
        int poll = 0;
        long waitTimeMilliSecs = ONE_SECOND;
        String asyncResultId = asyncResult.getId();
        RetrieveResult result = null;
        do {
            Thread.sleep(waitTimeMilliSecs);
            // Double the wait time for the next iteration
            waitTimeMilliSecs *= 2;
            if (poll++ > MAX_NUM_POLL_REQUESTS) {
            	throw new Exception("Request timed out after "+ MAX_NUM_POLL_REQUESTS +" retries.");
            }
            result = connection.checkRetrieveStatus(asyncResultId, true);
            System.out.println("Retrieve Status: " + result.getStatus());
        } while (!result.isDone());         

        return result;
    }

	public com.sforce.soap.metadata.Package parsePackageManifest(File file)
            throws ParserConfigurationException, IOException, SAXException {
        List<PackageTypeMembers> listPackageTypes = new ArrayList<PackageTypeMembers>();
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputStream inputStream = new FileInputStream(file);
        Element d = db.parse(inputStream).getDocumentElement();
        for (Node c = d.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element) {
                Element ce = (Element) c;
                NodeList nodeList = ce.getElementsByTagName("name");
                if (nodeList.getLength() == 0) {
                    continue;
                }
                String name = nodeList.item(0).getTextContent();
                NodeList m = ce.getElementsByTagName("members");
                List<String> members = new ArrayList<String>();
                for (int i = 0; i < m.getLength(); i++) {
                    Node mm = m.item(i);
                    members.add(mm.getTextContent());
                }
                PackageTypeMembers packageTypes = new PackageTypeMembers();
                packageTypes.setName(name);
                packageTypes.setMembers(members.toArray(new String[members.size()]));
                listPackageTypes.add(packageTypes);
            }
        }
        com.sforce.soap.metadata.Package packageManifest = new com.sforce.soap.metadata.Package();
        packageManifest.setTypes(listPackageTypes.toArray(new PackageTypeMembers[listPackageTypes.size()]));
        packageManifest.setVersion(Double.toString(apiVersion));
        return packageManifest;
    }
	
	public void printMetaDataTypes(List<PackageTypeMembers> packageMembers) {
		System.out.println("---");
		for (PackageTypeMembers packageMember : packageMembers) {
			String[] members = packageMember.getMembers();
			System.out.println(packageMember.getName() +" ("+ members.length +")");
			for (String member : members) {
				System.out.println("\t"+ member);
			}
		}
		System.out.println("---");
	}
	
	private void printDeploySuccess(DeployResult result) {
		System.out.println("---");
		DeployDetails details = result.getDetails();
		for (DeployMessage message : details.getComponentSuccesses()) {
			// Ignore manifest file
			if (!"".equals(message.getComponentType())) {
				String status;
				if (message.isCreated())
					status = "created";
				else if (message.isDeleted())
					status = "deleted";
				else if (message.isChanged())
					status = "changed";
				else
					status = "unchanged";
				
				UI.printKeyValue(message.getComponentType() +" "+ message.getFullName(), status, 40);
			}
		}
		System.out.println("---");
	}
	
	/**
	 * Print out any errors, if any, related to the deploy.
	 * @param result - DeployResult
	 */
	private void printDeployErrors(DeployResult result, String messageHeader) {
		DeployDetails details = result.getDetails();
		StringBuilder stringBuilder = new StringBuilder();
		if (details != null) {
			DeployMessage[] componentFailures = details.getComponentFailures();
			for (DeployMessage failure : componentFailures) {
				String loc = "(" + failure.getLineNumber() + ", " + failure.getColumnNumber() + ")";
				stringBuilder.append(failure.getFileName() + " "+ loc +": " 
						+ failure.getProblem()).append('\n');
			}
			RunTestsResult rtr = details.getRunTestResult();
			if (rtr.getFailures() != null) {
				for (RunTestFailure failure : rtr.getFailures()) {
					String n = (failure.getNamespace() == null ? "" :
						(failure.getNamespace() + ".")) + failure.getName();
					stringBuilder.append("Test failure, method: " + n + "." +
							failure.getMethodName() + " -- " + failure.getMessage() + 
							" stack " + failure.getStackTrace() + "\n\n");
				}
			}
			if (rtr.getCodeCoverageWarnings() != null) {
				for (CodeCoverageWarning ccw : rtr.getCodeCoverageWarnings()) {
					stringBuilder.append("Code coverage issue");
					if (ccw.getName() != null) {
						String n = (ccw.getNamespace() == null ? "" :
							(ccw.getNamespace() + ".")) + ccw.getName();
						stringBuilder.append(", class: " + n);
					}
					stringBuilder.append(" -- " + ccw.getMessage() + "\n");
				}
			}
		}
		if (stringBuilder.length() > 0) {
			stringBuilder.insert(0, messageHeader);
			System.out.println(stringBuilder.toString());
		}
	}

	private List<String> filterCustomApplications(List<String> objectNames) {
		List<String> filteredObjects = new ArrayList<>();
		for (String objectName : objectNames) {
			if (!objectName.startsWith("standard__"))
				filteredObjects.add(objectName);
		}
		return filteredObjects;
	}
}
