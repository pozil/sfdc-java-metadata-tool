package com.poz.util;

import java.util.Scanner;

public abstract class UI {

	private static Scanner scanner = new Scanner(System.in);
	
	public static String prompt(String label) {
	    System.out.print(label);
	    String userInput = scanner.nextLine();
	    System.out.println();
	    return userInput;
	}
	
	public static void printKeyValue(String key, String value, int keyLength) {
		String output = key;
		for (int i=key.length(); i<keyLength; i++)
			output += " ";
		output += value;
		System.out.println(output);
	}
}
