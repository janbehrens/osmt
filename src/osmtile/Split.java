/*
 *	This file is part of osmtile.
 *	
 *	osmtile is free software; you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License version 2 as
 *	published by the Free Software Foundation.
 *
 *	osmtile is distributed in the hope that it will be useful,
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

package osmtile;

import java.io.*;
import java.util.*;

public class Split {
	String inputFileName;
	NodeToTileNumber n2tn;
	BufferedReader br;
	String dataDir;
	float tilesize;
	boolean slim;
	
	float nodeLat, nodeLon;
	long nodeId = 0, ref = 0, firstRef = 0, previousRef = 0;
	int tn = 0, previousTn = 0;
	String line, wayLine = "";
	String target = "";
	Tile t, previousT;
	
	HashMap<Integer, Tile> tilesMap = new HashMap<Integer, Tile>();
	
	//sets and maps used during way processing
	HashSet<Tile> tiles = new HashSet<Tile>();	//tilesWayIsIn
	HashMap<Tile, Boolean> refsHaveBeenWritten = new HashMap<Tile, Boolean>();
	HashMap<Tile, Long> lastRemoteNodeAdded = new HashMap<Tile, Long>();
	HashMap<Tile, ArrayList<Long>> refs = new HashMap<Tile, ArrayList<Long>>();
	HashMap<Tile, ArrayList<Integer>> refTn = new HashMap<Tile, ArrayList<Integer>>();
	
	/**
	 * Constructor
	 * @param inputFileName
	 * @param node2tnFile
	 * @param dataDir
	 * @param tilesize
	 * @param slim
	 */
	public Split(String inputFileName, String node2tnFile, String dataDir, float tilesize, boolean slim) {
		try {
			n2tn = new NodeToTileNumber(node2tnFile, tilesize);
		} catch (Exception e) {
			System.err.println("Error writing index file");
			System.exit(1);
		}
		
		this.inputFileName = inputFileName;
		this.dataDir = dataDir;
		this.tilesize = tilesize;
		this.slim = slim;
		
		try {
			FileReader fr = new FileReader(inputFileName);
			br = new BufferedReader(fr);
		} catch (Exception e) {
			System.err.println("Error opening input file: " + inputFileName);
			System.exit(1);
		}
	}
	
	/**
	 * split
	 * @throws Exception
	 */
	public void split() throws Exception {
		System.out.println("Splitting file " + inputFileName + ", tile size: " + tilesize + "°");

		Date startDate = new Date();
		long startTime = startDate.getTime(), timeRunning, lineCount = 0;
		final long lineThreshold = 100000;
		
		boolean debug = false;
		
		//read lines
		while ((line = br.readLine()) != null) {
			lineCount++;
			
			//begin node
			if (line.contains("<node ")) {
				target = "nodes";
				
				//parse id, lat, lon
				String[] lineA = line.replace('=', ' ').replace('"', ' ').split(" +");
				nodeLat = Float.valueOf(lineA[4]);
				nodeLon = Float.valueOf(lineA[6]);
				nodeId = Long.valueOf(lineA[2]);
				
				//write tile number to random access file
				tn = n2tn.setTn(nodeId, nodeLat, nodeLon);
				
				//remember tile
				if (!tilesMap.containsKey(tn)) {
					t = new Tile(tn, dataDir);
					tilesMap.put(tn, t);
				}
				else {
					t = tilesMap.get(tn);
				}
				
				//write
				if (slim) {
					t.nodes.add(nodeId);
					t.writeTmpNodes(line);
				}
				else {
					storeNode(nodeId, t, line);
				}
			}
			//end node
			else if (line.contains("</node")) {
				//write
				if (slim) {
					t.writeTmpNodes(line);
				}
				else {
					storeNode(nodeId, t, line);
				}
			}
			//begin way
			else if (line.contains("<way ")) {
				//init
				target = "ways";
				wayLine = line;		//save the <way> line, will be written later
				firstRef = 0;
				previousRef = 0;
				previousTn = 0;
		        tiles.clear();
				refsHaveBeenWritten.clear();
				lastRemoteNodeAdded.clear();
				refs.clear();
				refTn.clear();
				
				//debug = wayLine.contains("way id=\"0\"");	//insert way ID to debug
			}
			//nd
			else if (line.contains("<nd ")) {
				//parse ref
				String[] lineA = line.replace('=', ' ').replace('"', ' ').split(" +");
				ref = Long.valueOf(lineA[2]);
				
				//get tile
				tn = n2tn.getTn(ref);
				t = tilesMap.get(tn);
				tiles.add(t);

//debug
if (debug) {
	System.out.println("== ref "+ref+" in tile "+tn+" ==");
}
				//init
				if (!refs.containsKey(t)) {
					refs.put(t, new ArrayList<Long>());
					refTn.put(t, new ArrayList<Integer>());
					lastRemoteNodeAdded.put(t, 0L);
				}
				
				//if way crosses a tile boundary...
				if (previousTn != tn && previousTn != 0) {
//debug
if (debug) {
	System.out.println("copying node "+ref+" from "+tn+" to "+previousTn);
}
					
					//copy <node> backward
					if (slim) {
						previousT.nodes.add(ref);
						previousT.nodesExtra.add(ref);
						previousT.writeRemoteNode(ref, t);
					}
					else {
						storeRemoteNode(ref, t, previousT);
					}
					
					//copy ref backward
					refs.get(previousT).add(ref);
					refTn.get(previousT).add(tn);
					
					lastRemoteNodeAdded.put(previousT, ref);
					
					if (lastRemoteNodeAdded.get(t) != previousRef) {	//prevent nodes from being inserting two subsequent times
//debug
if (debug) {
	System.out.println("copying node "+previousRef+" from "+previousTn+" to "+tn);
}
						
						//copy <node> forward
						if (slim) {
							t.nodes.add(previousRef);
							t.nodesExtra.add(previousRef);
							t.writeRemoteNode(previousRef, previousT);
						}
						else {
							storeRemoteNode(previousRef, previousT, t);
						}
						
						//copy ref forward
						refs.get(t).add(previousRef);
						refTn.get(t).add(previousTn);
					}
				}
				
				//other nd
				refs.get(t).add(ref);
				refTn.get(t).add(0);
				
				if (firstRef == 0) {
					firstRef = ref;
				}
								
				previousT = t;
				previousTn = tn;
				previousRef = ref;
			}
			//end way
			else if (line.contains("</way")) {
				//write all lines if not yet done (in each tile)
				for (Tile i : tiles) {
					if (!refsHaveBeenWritten.containsKey(i) || !refsHaveBeenWritten.get(i)) {
						//Closed ways: If the last nd equals the first nd, append to all segments
						//the first (local) nd.
						if (ref == firstRef && ref != refs.get(i).get(refs.get(i).size() - 1)) {
							refs.get(i).add(refs.get(i).get(0));
							refTn.get(i).add(refTn.get(i).get(0));
						}
//debug
if (debug) {
	System.out.println("closed way, write first nd again in tile "+tn);
}
						
						//write <way>, <nd>s
						i.writeTmpWays(wayLine);
						writeRefs(i);
						refsHaveBeenWritten.put(i, true);
					}
					//write
					i.writeTmpWays(line);
				}
			}
			//end
			else if (line.contains("<relation ") || line.contains("</osm")) {
				break;
			}
			//tags
			else {
				if (target == "nodes") {
					if (slim) {
						t.writeTmpNodes(line);
					}
					else {
						storeNode(nodeId, t, line);
					}
				}
				else if (target == "ways") {
					//write all lines if not yet done (in each tile)
					for (Tile i : tiles) {
						if (!refsHaveBeenWritten.containsKey(i) || !refsHaveBeenWritten.get(i)) {
							//Closed ways: If the last nd equals the first nd, append to all segments
							//the first (local) nd.
							if (ref == firstRef && ref != refs.get(i).get(refs.get(i).size() - 1)) {
								refs.get(i).add(refs.get(i).get(0));
								refTn.get(i).add(refTn.get(i).get(0));
							}
//debug
if (debug) {
	System.out.println("closed way, write first nd again in tile "+tn);
}

							//write <way>, <nd>s
							i.writeTmpWays(wayLine);
							writeRefs(i);
							refsHaveBeenWritten.put(i, true);
						}
						//write
						i.writeTmpWays(line);
					}
				}
			}
			
			// performance status
			if (lineCount % lineThreshold == 0) {
				timeRunning = new Date().getTime() - startTime;
				startTime = new Date().getTime();

				System.out.println("read " + lineCount + " lines (" + lineThreshold + " in " + timeRunning + " ms)");
			}
		}
		
		System.out.println("writing nodes ...");
		
		for (Tile i : tilesMap.values()) {
			//close temp. writers
			i.nodesWriter.close();
			i.nodesExtraWriter.close();
			i.waysWriter.close();
			
			//create writer for output file
			i.tileWriter = new FileWriter(i.tileFn);
			i.writeOpening();
			
			//write nodes
			if (slim) {
				i.writeNodesFromTmp();
			}
			else {
				for (String s : i.nodesMap.values()) {
					i.writeLine(s);
				}
			}
		}
		
		System.out.println("writing ways ...");
		
		for (Tile i : tilesMap.values()) {
			//write ways
			i.writeWaysFromTmp();
		}
		
		System.out.println("closing ...");
		
		for (Tile i : tilesMap.values()) {
			i.writeClosingTags();
			i.removeTmpFiles();
		}
	}
	
	/**
	 * storeNode: write node data to TreeMap 
	 * @param ref
	 * @param tn
	 * @param s
	 */
	void storeNode(long ref, Tile tile, String s) {
		if (tile.nodesMap.containsKey(ref)) {
			tile.nodesMap.put(ref, tile.nodesMap.get(ref) + "\n" + s);
		}
		else tile.nodesMap.put(ref, s);
	}

	/**
	 * storeRemoteNode: write a single node entry from tile to remote's TreeMap
	 * @param ref
	 * @param tn
	 * @param remoteTn
	 */
	void storeRemoteNode(long ref, Tile tile, Tile remote) {
		String s = tile.nodesMap.get(ref);

		if (remote.nodesMap.containsKey(ref)) {
			remote.nodesMap.put(ref, remote.nodesMap.get(ref) + "\n" + s);
		}
		else remote.nodesMap.put(ref, s);
	}
	
	/**
	 * writeRefs: write <nd .../> lines to file
	 * @param tn
	 */
	void writeRefs(Tile tile) {
		//fix problem with first/last node of closed way being the last remote node
		if (ref == firstRef && ref != refs.get(tile).get(0) && ref == lastRemoteNodeAdded.get(tile)) {
			refs.get(tile).add(0, ref);
			refTn.get(tile).add(0, refTn.get(tile).get(refTn.get(tile).size() - 1));
		}
		for (int i = 0; i < refs.get(tile).size(); i++) {
			if (refTn.get(tile).size() > i && refTn.get(tile).get(i) != 0) {
				tile.writeTmpWays("		<nd ref=\"" + refs.get(tile).get(i) + "\" tn=\"" + refTn.get(tile).get(i) + "\"/>");
			}
			else {
				tile.writeTmpWays("		<nd ref=\"" + refs.get(tile).get(i) + "\"/>");
			}
		}
	}
}
