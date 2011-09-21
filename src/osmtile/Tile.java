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
 * 	Author: Jan Behrens - 2011
 */

package osmtile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.TreeMap;
import java.util.TreeSet;

public class Tile {
	public int tn;
	public TreeMap<Long, String> nodesMap;
	public TreeSet<Long> nodes;
	public TreeSet<Long> nodesExtra;
	
	String dataDir, tileFn, nodesFn, nodesExtraFn, waysFn;
	Writer tileWriter, nodesWriter, nodesExtraWriter, waysWriter;

	public Tile(int tn, String dataDir) {
		this.tn = tn;
		this.dataDir = dataDir;
		
		File dir = new File(dataDir + tn);
		if (!dir.exists()) dir.mkdir();
		
		tileFn = dataDir + tn + ".osm";
		nodesFn = dir + "/" + "nodes.osm";
		nodesExtraFn = dir + "/" + "nodes-extra.osm";
		waysFn = dir + "/" + "ways.osm";

		try {
			nodesWriter = new FileWriter(nodesFn);
		} catch (IOException e) {
			System.err.println("error opening file: " + nodesFn);
			e.printStackTrace();
		}
		try {
			nodesExtraWriter = new FileWriter(nodesExtraFn);
		} catch (IOException e) {
			System.err.println("error opening file: " + nodesExtraFn);
			e.printStackTrace();
		}
		try {
			waysWriter = new FileWriter(waysFn);
		} catch (IOException e) {
			System.err.println("error opening file: " + waysFn);
			e.printStackTrace();
		}

		nodesMap = new TreeMap<Long, String>();
		nodes = new TreeSet<Long>();
		nodesExtra = new TreeSet<Long>();
	}
	
	/**
	 * writeOpening: write the first few lines to the output file
	 */
	public void writeOpening() {
		float[] bounds = NodeToTileNumber.getBounds(tn);
		
		try {
			tileWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			tileWriter.write("<osm version=\"0.6\" generator=\"cutTheOsmPlanet\">\n");
			tileWriter.write("<bounds minlat=\"" + bounds[0] + "\" minlon=\"" + bounds[1] + "\" maxlat=\"" + bounds[2] + "\" maxlon=\"" + bounds[3] + "\"/>\n");
		} catch (IOException e) {
			System.err.println("error writing to file: " + tileFn);
			e.printStackTrace();
		} finally {
			try {
				tileWriter.flush();
			} catch (IOException e) {
				System.err.println("error flushing file: " + tileFn);
				e.printStackTrace();
			}
		}
	}

	/**
	 * writeLine: write a line to the output file
	 * @param s - the line to be written
	 */
	public void writeLine(String s) {
		try {
			tileWriter.write(s+"\n");
		} catch (IOException e) {
			System.err.println("error writing to file: " + tileFn);
			e.printStackTrace();
		} finally {
			try {
				tileWriter.flush();
			} catch (IOException e) {
				System.err.println("error flushing file: " + tileFn);
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * writeRemoteNode
	 */
	public void writeRemoteNode(long id, Tile remote) throws IOException {
		String line;
		boolean parse = false;
		
		try {
			FileReader fr = new FileReader(remote.nodesFn);
			BufferedReader br = new BufferedReader(fr);

			while ((line = br.readLine()) != null) {
				if (parse) {
					if (line.contains(" id=\"")) {
						break;
					}
					else try {
						nodesExtraWriter.write(line + "\n");
					} catch (IOException e) {
						System.err.println("error writing to file: " + nodesExtraFn);
						e.printStackTrace();
					}
				}
				else if (line.contains(" id=\"" + id + "\"")) {
					try {
						nodesExtraWriter.write(line + "\n");
					} catch (IOException e) {
						System.err.println("error writing to file: " + nodesExtraFn);
						e.printStackTrace();
					}
					parse = true;
				}
			}
			//close BufferedReader
			try {
				br.close();
			} catch (Exception e) {
				System.err.println("error closing BufferedReader");
				e.printStackTrace();
			}
		} catch (IOException e) {
			System.err.println("error opening file: " + remote.nodesFn);
			e.printStackTrace();
		} finally {
			try {
				nodesExtraWriter.flush();
			} catch (IOException e) {
				System.err.println("error flushing file: " + nodesExtraFn);
				e.printStackTrace();
			}
		}
	}

	/**
	 * writeTmpNodes: write node data to temporary file
	 * @param s
	 */
	public void writeTmpNodes(String s) {
		try {
			nodesWriter.write(s + "\n");
		} catch (IOException e) {
			System.err.println("error writing to file: " + nodesFn);
		} finally {
			try {
				nodesWriter.flush();
			} catch (IOException e) {
				System.err.println("error flushing file: " + nodesFn);
			}
		}
	}

	/**
	 * writeTmpWays: write way data to temporary file
	 * @param s
	 */
	public void writeTmpWays(String s) {
		try {
			waysWriter.write(s + "\n");
		} catch (IOException e) {
			System.err.println("error writing to file: " + waysFn);
		} finally {
			try {
				waysWriter.flush();
			} catch (IOException e) {
				System.err.println("error flushing file: " + waysFn);
			}
		}
	}

	/**
	 * writeNodesFromTmp: write node data from temporary files to output file
	 */
	public void writeNodesFromTmp() {
		String line, previousLine = "";
		boolean parse;
		
		try {
			FileReader fr = new FileReader(nodesFn);
			BufferedReader br = new BufferedReader(fr);
			
			try {
				for (long id : nodes) {
					parse = false;
					
					//look in nodes-extra
					if (nodesExtra.contains(id)) {
						FileReader extraFr = new FileReader(nodesExtraFn);
						BufferedReader extraBr = new BufferedReader(extraFr);
						
						while ((line = extraBr.readLine()) != null) {
							if (parse) {
								if (line.contains(" id=\"")) {
									break;
								}
								else try {
									tileWriter.write(line + "\n");
								} catch (IOException e) {
									System.err.println("error writing to file: " + tileFn);
									e.printStackTrace();
								}
							}
							else if (line.contains(" id=\"" + id + "\"")) {
								try {
									tileWriter.write(line + "\n");
								} catch (IOException e) {
									System.err.println("error writing to file: " + tileFn);
									e.printStackTrace();
								}
								parse = true;
							}
						}
						//close BufferedReader
						try {
							extraBr.close();
						} catch (Exception e) {
							System.err.println("error closing BufferedReader");
							e.printStackTrace();
						}
					}
					//if not found in nodes-extra, continue with nodes
					else {
						//previousLine is the line read just before the loop broke the last time
						if (previousLine.contains(" id=\"" + id + "\"")) {
							try {
								tileWriter.write(previousLine + "\n");
							} catch (IOException e) {
								System.err.println("error writing to file: " + tileFn);
								e.printStackTrace();
							}
							parse = true;
						}
						while ((line = br.readLine()) != null) {
							if (parse) {
								if (line.contains(" id=\"")) {
									previousLine = line;
									break;
								}
								else try {
									tileWriter.write(line + "\n");
								} catch (IOException e) {
									System.err.println("error writing to file: " + tileFn);
									e.printStackTrace();
								}
							}
							else if (line.contains(" id=\"" + id + "\"")) {
								try {
									tileWriter.write(line + "\n");
								} catch (IOException e) {
									System.err.println("error writing to file: " + tileFn);
									e.printStackTrace();
								}
								parse = true;
							}
						}
					}
				}
			} catch (IOException e) {
				System.err.println("error opening file: " + nodesExtraFn);
				e.printStackTrace();
			} finally {
				try {
					tileWriter.flush();
				} catch (IOException e) {
					System.err.println("error flushing file: " + tileFn);
					e.printStackTrace();
				}
				//close BufferedReader
				try {
					br.close();
				} catch (Exception e) {
					System.err.println("error closing BufferedReader");
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			System.err.println("error opening file: " + nodesFn);
			e.printStackTrace();
		}
	}
	/**
	 * writeWaysFromTmp: write way data from temporary files to output file
	 */
	public void writeWaysFromTmp() {
		String line;

		try {
			FileReader fr = new FileReader(waysFn);
			BufferedReader br = new BufferedReader(fr);

			while ((line = br.readLine()) != null) {
				//read line, write line
				try {
					tileWriter.write(line + "\n");
				} catch (IOException e) {
					System.err.println("error writing to file: " + tileFn);
					e.printStackTrace();
				}
			}
			//close BufferedReader
			try {
				br.close();
			} catch (Exception e) {
				System.err.println("error closing BufferedReader");
				e.printStackTrace();
			}
		} catch (IOException e) {
			System.err.println("error opening file: " + waysFn);
			e.printStackTrace();
		} finally {
			try {
				tileWriter.flush();
			} catch (IOException e) {
				System.err.println("error flushing file: " + waysFn);
				e.printStackTrace();
			}
		}
	}

	/**
	 * writeClosingTags: write "</osm>" and close file handles
	 */
	public void writeClosingTags() {
		try {
			tileWriter.write("</osm>\n");
		} catch (IOException e) {
			System.err.println("error writing to file: " + tileFn);
			e.printStackTrace();
		} finally {
			//close handle
			try {
				tileWriter.close();
			} catch (IOException e) {
				System.err.println("error closing file: " + tileFn);
				e.printStackTrace();
			}
		}
	}

	/**
	 * removeTmpFiles
	 */
	public void removeTmpFiles() {
		File dir = new File(dataDir + tn);
        
        for (String file : dir.list()) {
        	File f = new File(dir + "/" + file);
        	if (!f.delete()) {
        		System.err.println("error deleting directory: " + f);
        	}
        }
	    if (!dir.delete()) {
	    	System.err.println("error deleting directory: " + dir);
        }
	}
}
