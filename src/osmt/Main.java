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
 * 	Based on cutTheOsmPlanet:
 * 		Copyright (C) 2010 Heiko Budras
 *		Author: Heiko Budras
 *		Tile logic: Carsten Schwede
 *	Modified by: Jan Behrens - 2011
 */

package osmt;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import com.esotericsoftware.wildcard.Paths;

public class Main {
	final static String VERSION = "Version 1.0";
	HashMap<String, String> params;
	
	/**
	 * Constructor
	 * @param args
	 */
	private Main(String[] args) {
		params = new HashMap<String, String>();
		this.storeParams(args);
	}
	
	/**
	 * storeParams - parse and store the command line arguments
	 * @param args
	 */
	private void storeParams(String[] args) {
		for (int i = 0; i < args.length; i++) {
			try {
				String[] keyValue = args[i].replace("--", "").replace("\"", "").split("=");
				params.put(keyValue[0], keyValue[1]);
			}
			catch (Exception e) {
				String key = args[i].replace("--", "").replace("\"", "").replace("=", "");
				params.put(key, "");
			}
		}
	}
	
	/**
	 * help - print the help text
	 */
	public static void help() {
		System.out.println("Usage: osmt --split [OPTIONS] SOURCE");
		System.out.println("or:    osmt --merge --of=DEST [OPTIONS] SOURCE");
		System.out.println("Split SOURCE file, or merge SOURCE files to DEST file");
		System.out.println("");
		System.out.println("Options (only in splitting mode):");
		System.out.println("--output-dir=DIR      write tiles to DIR (defaults to working directory)");
		System.out.println("--index-file=FILE     write index file (~6 GB) to FILE (defaults to \"node2tn\" in working directory)");
		System.out.println("--tile-size=SIZE      create tiles of SIZE degrees in width and height (defaults to 1)");
		System.out.println("--slim                save temporary nodes on disk, not in RAM");
		System.out.println("");
		System.out.println("Other options:");
		System.out.println("--help                print help");
	}
	
	/**
	 * main
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) {
		//System.out.println(VERSION);
		
		ArrayList<String> inputFiles = new ArrayList<String>();
		String outputFile = "";
		String node2tnFile = "node2tn";
		String outputDir = "";
		float tilesize = 1.0f;
		boolean merge = false;
		boolean slim = false;
		
		Main main = new Main(args);
		
		//help
		if (main.params.containsKey("help")) {
			help();
			System.exit(1);
		}
		
		//split or merge
		if (main.params.containsKey("split")) {
			merge = false;
			main.params.remove("split");
		}
		else if (main.params.containsKey("merge")) {
			merge = true;
			main.params.remove("merge");
		}
		else {
			System.err.println("Error: please specify either \"--split\" or \"--merge\"");
			System.err.println("");
			help();
			System.exit(1);
		}
		
		//merge arguments
		if (merge) {
			if (main.params.containsKey("of") && main.params.get("of") != "") {
				outputFile = main.params.get("of");
				main.params.remove("of");
			}
			else {
				System.err.println("Error: please specify an output file");
				System.err.println("");
				help();
				System.exit(1);
			}
		}
		//split arguments
		else {
			if (main.params.containsKey("index-file") && main.params.get("index-file") != "") {
				node2tnFile = main.params.get("index-file");
				main.params.remove("index-file");
			}
			
			if (main.params.containsKey("output-dir") && main.params.get("output-dir") != "") {
				outputDir = main.params.get("output-dir");
				
				File dir = new File(outputDir);
				if (!dir.canExecute()) {
					System.err.println("Error: output-dir does not exist or is not executable");
					System.exit(1);
				}
				if (!outputDir.endsWith("/")) {
					outputDir += "/";
				}
				main.params.remove("output-dir");
			}
			
			if (main.params.containsKey("tile-size") && main.params.get("tile-size") != "") {
				tilesize = Float.parseFloat(main.params.get("tile-size"));
				
				if (tilesize < 0.006F) {
					System.err.println("Error: Tile size must be 0.006 degrees or more");
					System.exit(1);
				}
				main.params.remove("tile-size");
			}
			
			if (main.params.containsKey("slim")) {
				slim = true;
				main.params.remove("slim");
			}
		}
		
		//input files
		if (!main.params.isEmpty()) {
			for (String p : main.params.keySet()) {
				int indexOfAsterisk = p.indexOf("*");
				int lastIndexOfSlash = indexOfAsterisk >= 0 ? p.substring(0, indexOfAsterisk).lastIndexOf("/") : p.lastIndexOf("/");
				String basedir = lastIndexOfSlash > -1 ? p.substring(0, lastIndexOfSlash) : System.getProperty("user.dir");
				String wildcard = lastIndexOfSlash > -1 ? p.substring(lastIndexOfSlash + 1) : p;
				
				//resolve wildcards
				Paths paths = new Paths(basedir, wildcard);
				
				for (String path : paths.getPaths()) {
					inputFiles.add(path);
				}
			}
			if (!merge && inputFiles.size() > 1) {
				System.err.println("Warning: Skipping input files.");
				System.err.println("");
			}
		}
		else {
			System.err.println(merge ? "Error: please specify at least one input file" : "Error: please specify an input file");
			System.err.println("");
			help();
			System.exit(1);
		}
		
		//start over
		
		if (merge) {
			Merge mrg = new Merge(inputFiles, outputFile);
			
			try {
				mrg.merge();
			} catch (Exception e) {
				System.err.println("Error: Merging failed.");
				e.printStackTrace();
			}
		}
		else {
			Split splt = new Split(inputFiles.get(0), node2tnFile, outputDir, tilesize, slim);
			
			NodeToTileNumber.tilesizeLat = tilesize;
			NodeToTileNumber.tilesizeLon = tilesize;
			
			try {
				splt.split();
			} catch (Exception e) {
				System.err.println("Error: Splitting failed.");
				e.printStackTrace();
			}
		}
	}
}
