package com.poz.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class FileUtil {
	
	/**
	 * Zips a folder
	 * @param zipFilePath path to generated zip file
	 * @param zipSourceDirPath path to the directory that will be zipped
	 * @throws IOException
	 */
	public static void zipFolder(String zipFilePath, String zipSourceDirPath) throws IOException {
		byte[] buffer = new byte[1024];
		File dir = new File(zipSourceDirPath);
		File[] files = dir.listFiles();
		
		try (
				FileOutputStream fos = new FileOutputStream(zipFilePath);
				ZipOutputStream zos = new ZipOutputStream(fos);
			) {
			for (int i = 0; i < files.length; i++) {
				try (FileInputStream fis = new FileInputStream(files[i])) {
					zos.putNextEntry(new ZipEntry(dir.getName() +"/"+ files[i].getName()));
					int length;
					while ((length = fis.read(buffer)) > 0) {
						zos.write(buffer, 0, length);
					}
					zos.closeEntry();
				}
			}
		}
	}

	/**
    * Reads the content of a file and returns it as a byte array.
    * @param filePath file's path
    * @return byte array
	* @throws IOException
    */
    public static byte[] readFile(String filePath) throws IOException {
        byte[] result = null;
        File file = new File(filePath);
        if (!file.exists() || !file.isFile())
            throw new FileNotFoundException("Cannot find the file:"+ file.getAbsolutePath());

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead = 0;
            while (-1 != (bytesRead = fileInputStream.read(buffer))) {
                bos.write(buffer, 0, bytesRead);
            }
            result = bos.toByteArray();
        }
        return result;
    }
    
    /**
     * Writes binary content to a file
     * @param filePath
     * @param fileContent
     * @throws IOException
     */
    public static void writeFile(String filePath, byte[] fileContent) throws IOException {
    	File resultsFile = new File(filePath);
        try (FileOutputStream os = new FileOutputStream(resultsFile)) {
            os.write(fileContent);
        }
    }
}