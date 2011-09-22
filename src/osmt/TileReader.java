/*
 *	This file is part of OSMT.
 *	
 *	OSMT is free software; you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License version 2 as
 *	published by the Free Software Foundation.
 *
 *	OSMT is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 *	GNU General Public License for more details.
 *
 * 	Author: Jan Behrens - 2011
 */

package osmt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class TileReader {
	String inputFile;
	BufferedReader inputReader;
	
	public int tn;
	public String previousLine;

	public TileReader(String fn, int tn) {
		inputFile = fn;
		this.tn = tn;
		previousLine = "";
		
		try {
			FileReader fr = new FileReader(fn);
			inputReader = new BufferedReader(fr);
		} catch (FileNotFoundException e) {
			System.err.println("Error: File not found.");
			System.exit(1);
		}
	}
}
