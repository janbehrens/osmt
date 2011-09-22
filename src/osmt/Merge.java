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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

public class Merge {
	ArrayList<TileReader> inputTiles;
	String outputFile;
	
	BufferedReader br;
	Writer fh, fhTmp;
	
	/**
	 * Constructor
	 * @param inputFiles
	 * @param outputFile
	 */
	public Merge(ArrayList<String> inputFiles, String outputFile) {
		inputTiles = new ArrayList<TileReader>(inputFiles.size());
		
		for (String fn : inputFiles) {
			int tn = 0;
			
			try {
				tn = Integer.parseInt(fn.substring(fn.lastIndexOf("/") + 1).split("\\.")[0]);
			} catch (Exception e) {
				System.err.println("Warning: ignoring file " + fn);
			}
			if (tn != 0) {
				inputTiles.add(new TileReader(fn, tn));
			}
		}
		
		this.outputFile = outputFile;
		
		try {
			fh = new FileWriter(outputFile);
		} catch (IOException e) {
			System.err.println("Error: Cannot open file for writing: " + outputFile);
		}
	}
	
	/**
	 * merge
	 * @throws Exception
	 */
	void merge() throws Exception {
		//TreeMaps id -> tile number
		TreeMap<Long, TileReader> nodesMap = new TreeMap<Long, TileReader>();
		TreeMap<Long, TileReader> waysMap = new TreeMap<Long, TileReader>();
		
		TileReader tr;
		String line;
		long nodeId = 0L, wayId = 0L, newId = 1000000000L;
		boolean parse, splitWay = false, ndsWritten = false, segmentSaved = false;
		
		float minLat = 90F, minLon = 180F, maxLat = -90F, maxLon = -180F;
		
		WaySegment segment = new WaySegment();

		TreeMap<Long, LinkedList<WaySegment>> segments = new TreeMap<Long, LinkedList<WaySegment>>();
		TreeMap<Long, ArrayList<WaySegment>> mergedWays = new TreeMap<Long, ArrayList<WaySegment>>();
		
		
		//write opening tags
		fh.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		fh.write("<osm version=\"0.6\" generator=\"osmt\">\n");
		
		
		//1st pass: parse nodes, save them to TreeMap
		
		for (TileReader t : inputTiles) {
			br = t.inputReader;
			
			System.out.println("1st pass tile " + t.tn + " ...");
			
			//parse XML
			while ((line = br.readLine()) != null) {
				if (line.contains("<bounds ")) {
					String[] attr = line.replace('=', ' ').replace('"', ' ').split(" +");
					float tileMinLat = Float.valueOf(attr[2]);
					float tileMinLon = Float.valueOf(attr[4]);
					float tileMaxLat = Float.valueOf(attr[6]);
					float tileMaxLon = Float.valueOf(attr[8]);
					
					minLat = Math.min(minLat, tileMinLat);
					minLon = Math.min(minLon, tileMinLon);
					maxLat = Math.max(maxLat, tileMaxLat);
					maxLon = Math.max(maxLon, tileMaxLon);
				}
				else if (line.contains("<node ")) {
					String[] attr = line.replace('=', ' ').replace('"', ' ').split(" +");
					nodeId = Long.valueOf(attr[2]);
					nodesMap.put(nodeId, t);
				}
				else if (line.contains("<way ") || line.contains("<relation ") || line.contains("</osm>")) {
					break;
				}
			}
		}
		
		System.out.println(": found " + nodesMap.size() + " node objects");
		
		fh.write("<bounds minlat=\"" + minLat + "\" minlon=\"" + minLon + "\" maxlat=\"" + maxLat + "\" maxlon=\"" + maxLon + "\"/>\n");
		
		
		//write nodes to output file
		
		newReaders(inputTiles);
		System.out.println("writing nodes ...");
		
		int writeCount = 0;
		String pL;
		
		for (Map.Entry<Long, TileReader> entry : nodesMap.entrySet()) {
			nodeId = entry.getKey();
			tr = entry.getValue();
			
			parse = false;
			
			br = tr.inputReader;
			pL = tr.previousLine;	//tr.previousLine is the line read just before the loop broke the last time
			
			if (pL.contains("<node id=\"" + nodeId + "\"")) {
				fh.write(pL + "\n");
				parse = true;
				writeCount++;
			}
			
			while ((line = br.readLine()) != null) {
				if (parse) {
					if (line.contains("<node ") || line.contains("<way ") || line.contains("<relation ") || line.contains("</osm>")) {
						tr.previousLine = line;
						break;
					}
					else {
						fh.write(line + "\n");
					}
				}
				else if (line.contains("<node id=\"" + nodeId + "\"")) {
					fh.write(line + "\n");
					
					parse = true;
					writeCount++;
				}
			}
		} //end for (Map.Entry<Long, TileReader> entry : nodesMap.entrySet())
		
		System.out.println(": wrote " + writeCount + " nodes to output file");
		
		
		//2nd pass: parse ways, identify split ways, save them to TreeMap
		
		newReaders(inputTiles);
		
		boolean tnFound = false;
		
		for (TileReader t : inputTiles) {
			br = t.inputReader;
			
			splitWay = false;
			
			System.out.println("2nd pass tile " + t.tn + " ...");
			
			//parse XML
			while ((line = br.readLine()) != null) {
				if (line.contains("<way ")) {
					splitWay = parse = segmentSaved = false;
					segment = new WaySegment();
					
					String[] attr = line.replace('=', ' ').replace('"', ' ').split(" +");
					wayId = Long.valueOf(attr[2]);
					
					waysMap.put(wayId, t);
				}
				else if (line.contains("<nd ")) {
					String[] nd = line.replace('=', ' ').replace('"', ' ').split(" +");
					segment.refs.add(Long.valueOf(nd[2]));
					
					//tn attribute?
					tnFound = nd.length == 6 && nd[3].equals("tn");
					segment.refTn.add(tnFound ? Integer.parseInt(nd[4]) : 0);
					
					if (tnFound) splitWay = true;
				}
				else if ((line.contains("<tag ") || line.contains("</way>")) && splitWay && !segmentSaved) {
					if (segment.refs.size() > 0) {
						if (!segments.containsKey(wayId)) {
							segments.put(wayId, new LinkedList<WaySegment>());
						}
						segment.tn = t.tn;
						segments.get(wayId).add(new WaySegment(segment));
						
						segmentSaved = true;
					}
				}
				else if (line.contains("<relation ") || line.contains("</osm>")) {
					break;
				}
			} //end while ((line = br.readLine()) != null)
		} //end for (TileReader tr : inputTiles)
		
		System.out.println(": found " + waysMap.size() + " ways (" + segments.size() + " split ways)");
		
		
		//merge split ways
		
		System.out.println("merging ways ...");
		
		for (long id : segments.keySet()) {
//			System.out.println("merging way " + id );
			mergedWays.put(id, mergeSegments(segments.get(id), id == 0));	//insert way ID to debug
//			System.out.println(mergedWays.get(id).size());
		}
		
		System.out.println(": merged " + mergedWays.size() + " ways");
		
		
		//write ways to output file
		
		System.out.println("writing ways ...");
		
		newReaders(inputTiles);

		writeCount = 0;
		ArrayList<String> extraWays = new ArrayList<String>();
		
		for (Map.Entry<Long, TileReader> entry : waysMap.entrySet()) {
			wayId = entry.getKey();
			tr = entry.getValue();
			
			parse = splitWay = ndsWritten = false;
			String[] wayString = new String[mergedWays.containsKey(wayId) ? mergedWays.get(wayId).size() : 1];
			
			br = tr.inputReader;
			pL = tr.previousLine;	//tr.previousLine is the line read just before the loop broke the last time
			
			if (pL.contains("<way id=\"" + wayId + "\"")) {
				for (int w = 0; w < wayString.length; w++) {
					wayString[w] = pL + "\n";
				}
				
				if (mergedWays.containsKey(wayId)) {
					splitWay = true;
				}
				parse = true;
				writeCount++;
			}

			//parse
			while ((line = br.readLine()) != null) {
				if (parse) {
					if (line.contains("<way ") || line.contains("<relation ") || line.contains("</osm>")) {
						tr.previousLine = line;
						break;
					}
					else if (line.contains("<nd ")) {
						if (splitWay) {
							if (!ndsWritten) {
								for (int i = 0; i < mergedWays.get(wayId).size(); i++) {
									//first instance keeps original ID, others get incremental negative IDs
									if (i > 0) {
										int idIndex1 = wayString[i].indexOf("id=\"") + 4;
										int idIndex2 = wayString[i].indexOf("\"", idIndex1);
										wayString[i] = wayString[i].substring(0, idIndex1) + ++newId + wayString[i].substring(idIndex2);
									}
									for (Long r : mergedWays.get(wayId).get(i).refs) {
										wayString[i] += "		<nd ref=\"" + r + "\"/>\n";
									}
								}
								ndsWritten = true;
							}
						}
						else {
							for (int w = 0; w < wayString.length; w++) {
								wayString[w] += line + "\n";
							}
						}
					}
					else {
						for (int w = 0; w < wayString.length; w++) {
							wayString[w] += line + "\n";
						}
					}
				}
				else if (line.contains("<way id=\"" + wayId + "\"")) {
					for (int w = 0; w < wayString.length; w++) {
						wayString[w] = line + "\n";
					}
					
					if (mergedWays.containsKey(wayId)) {
						splitWay = true;
					}
					parse = true;
					writeCount++;
				}
			} //end while ((line = br.readLine()) != null)
			
			fh.write(wayString[0]);
			
			if (wayString.length > 1) {
				for (int w = 1; w < wayString.length; w++) {
					extraWays.add(wayString[w]);
				}
			}
			
			if (!parse) System.out.println("did not write way " + wayId);
		} //end for (Map.Entry<Long, Integer> entry : waysMap.entrySet())
		
		for (String s : extraWays) {
			fh.write(s);
		}
		
		System.out.println(": wrote " + writeCount + " ways to output file");

		fh.write("</osm>\n");
		fh.close();
	}
	
	/**
	 * mergeSegments: merge ArrayLists of node IDs
	 * @param list
	 * @param debug
	 * @return 
	 */
	ArrayList<WaySegment> mergeSegments(LinkedList<WaySegment> segments, boolean debug) {
		WaySegment result = new WaySegment();
		ArrayList<WaySegment> resultList = new ArrayList<WaySegment>();
		boolean closed = false;
		boolean incomplete = false;
		
		//trim duplicate closed-way nodes
		for (int i = 0; i < segments.size(); i++) {
			ArrayList<Long> r = segments.get(i).refs;
			ArrayList<Integer> rtn = segments.get(i).refTn;
			
			if (rtn.get(rtn.size() - 1) != 0 && rtn.get(rtn.size() - 2) != 0) {
				rtn.remove(rtn.size() - 1);
				r.remove(r.size() - 1);
				closed = true;
			}
			if (rtn.get(0) != 0 && rtn.get(1) != 0) {
				rtn.remove(0);
				r.remove(0);
				closed = true;
			}
		}
		
		WaySegment a = new WaySegment();
		
		
		while (!segments.isEmpty()) {
			
if (debug) {
	for (WaySegment ws : segments) {
		System.out.println(ws.print());
	}
	System.out.println("");
}

			a = segments.get(0);

			//find the first segment, if available
			for (int i = 0; i < segments.size(); i++) {
				if (!segments.get(i).refTn.isEmpty()) {
					if (segments.get(i).refTn.get(0) == 0) {
						a = segments.get(i);
						break;
					}
				}
			}

			//try forward merge
			result = iterateSegments(segments, a, false, debug);
			
if (debug) {
	System.out.println("");
	System.out.println("result after forward merge: "+result.print());
	System.out.println("");
	for (WaySegment ws : segments) {
		System.out.println(ws.print());
	}
	System.out.println("");
}

			if (!segments.isEmpty()) {
				a = new WaySegment(result);

				//try backward merge
				result = iterateSegments(segments, a, true, debug);
				
				segments.remove(a);
				
				//invert order
				for (int i = result.refs.size() - 1; i >= 0; i--) {
					result.refs.add(result.refs.get(i));
					result.refs.remove(i);
				}
				
if (debug) {
	System.out.println("");
	System.out.println("result after backward merge: "+result.print());
	System.out.println("");
}
			}
			
			incomplete = result.refTn.get(result.refTn.size() - 1) != 0 || result.refTn.get(0) != 0;

			//closed ways
			if (incomplete && closed) {
				result.refs.add(result.refs.get(0));
			}

			resultList.add(new WaySegment(result));
			
			result.refs.clear();
			result.refTn.clear();
		} //end while (!segments.isEmpty())
		
if (debug) {
	System.out.println("resultList:");
	for (WaySegment ws : resultList) {
		System.out.println(ws.refs);
	}
	System.out.println("");
}
		
		return resultList;
	}
	
	/**
	 * iterateSegments
	 * @param segments
	 * @param a
	 * @param backward
	 * @param debug
	 * @return
	 */
	WaySegment iterateSegments(LinkedList<WaySegment> segments, WaySegment a, boolean backward, boolean debug) {
		WaySegment result = new WaySegment();
		int current = backward ? a.refs.size() - 1 : 0;
		int nextTile;
		boolean skipTile = false;
		
		//first element
		if (a.refTn.get(current) != 0) {
			result.refs.add(a.refs.get(current));
			result.refTn.add(a.refTn.get(current));
			a.refs.remove(current);
			a.refTn.remove(current);
		}
		
		//iterate over node refs
		while (!a.refTn.isEmpty()) {
			current = backward ? a.refs.size() - 1 : 0;
			
			//if not remote, keep it
			if (a.refTn.get(current) == 0) {
				result.refs.add(a.refs.get(current));
				result.refTn.add(a.refTn.get(current));
if (debug) {
	System.out.println("adding "+a.refs.get(current)+" to result");
}
			}
			//else get to the next tile
			else {
				nextTile = -1;
				skipTile = false;
				
				for (WaySegment ws : segments) {
					if (ws.tn.equals(a.refTn.get(current))) {
						nextTile = segments.indexOf(ws);
						break;
					}
				}
			
if (debug) {
	System.out.println("nextTile: "+nextTile);
}

				//if next tile is missing: check if we can skip it
				if (nextTile == -1) {
					if (segments.size() == 2) {
						for (int i = 0; i < segments.size(); i++) {
							if (segments.get(i) != a && segments.get(i).refTn.get(backward ? 0 : segments.get(i).refTn.size() - 1) == 0) {
if (debug) {
	System.out.println("skipping");
}
								nextTile = i;
								skipTile = true;
							}
						}
					}
				}
				//if next tile is available...
				if (nextTile > -1) {
					//if we are skipping, keep remote nodes also
					if (skipTile) {
	if (debug) {
		System.out.println("adding "+a.refs.get(current)+" to result");
	}

						result.refs.add(a.refs.get(current));
						result.refTn.add(a.refTn.get(current));
					}
					
					//special case: only 1 node in outer tile
					if (1 < a.refTn.size() && a.refTn.get(backward ? a.refTn.size() - 2 : 1) != 0) {
						a.refs.remove(current);
						a.refTn.remove(current);
					}
					
					//remove a if it's done (i.e. 1 entry left), make a the next segment
					if (a.refs.size() <= 1) {
						WaySegment a_old = a;
						a = segments.get(nextTile);
						segments.remove(a_old);
					}
					else {
						a = segments.get(nextTile);
					}
				}
				//if it isn't...
				else {
if (debug) {
	System.out.println("adding "+a.refs.get(current)+" to result");
}

					result.refs.add(a.refs.get(current));
					result.refTn.add(a.refTn.get(current));
					
				}
				current = backward ? a.refs.size() - 1 : 0;
			}
			
			//remove the nd we just added (or the first one, which is remote)
			if (!a.refs.isEmpty() && !skipTile) {
				a.refs.remove(current);
				a.refTn.remove(current);
			}
		} //end while (!a.refTn.isEmpty())
		
		if (a.refs.isEmpty()) {
			segments.remove(a);
		}

		return result;
	}
	
	/**
	 * newReaders
	 * @param tr
	 */
	void newReaders(ArrayList<TileReader> readers) {
		try {
			for (TileReader tr : readers) {
				FileReader fr = new FileReader(tr.inputFile);
				tr.inputReader = new BufferedReader(fr);
				tr.previousLine = "";
			}
		} catch (FileNotFoundException e) {
			System.err.println("Error: File not found");
		}
	}
}

class WaySegment {
	ArrayList<Long> refs;
	ArrayList<Integer> refTn;
	Integer tn;
	
	WaySegment() {
		refs = new ArrayList<Long>();
		refTn = new ArrayList<Integer>();
	}

	WaySegment(WaySegment ws) {
		refs = new ArrayList<Long>(ws.refs);
		refTn = new ArrayList<Integer>(ws.refTn);
		tn = ws.tn;
	}
	
	boolean isEmpty() {
		return refs.isEmpty() && refTn.isEmpty();
	}

	String print() {
		String str = tn + ": [";
		
		for (int i = 0; i < refs.size(); i++) {
			str += refs.get(i) + " (" + refTn.get(i) + "), ";
		}
		str += "]";
		
		return str;
	}
}

