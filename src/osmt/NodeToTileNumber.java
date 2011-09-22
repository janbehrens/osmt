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

import java.io.*;

public class NodeToTileNumber {
	private RandomAccessFile node2tn;
	public static float tilesizeLat, tilesizeLon;

	public NodeToTileNumber(String node2tnFile, float tilesize) throws Exception {
		node2tn = new RandomAccessFile(node2tnFile, "rw");
		tilesizeLat = tilesize;
		tilesizeLon = tilesize;
	}
	
	/**
	 * setTn - set tile number calculated from lat/lon
	 * @param nodeId
	 * @param lat
	 * @param lon
	 * @return
	 * @throws IOException
	 */
	public int setTn(long nodeId, float lat, float lon) throws IOException {
		//4 bytes for tile number
		long seekPos = nodeId*4;
		
		if (this.node2tn.length() < seekPos) {
			this.node2tn.setLength(seekPos + (4*1024*1024*100));
		}
		
		boolean coordinatesValid = 90.0 >= lat && -90.0 <= lat && -180.0 <= lon && 180.0 >= lon;

		if (coordinatesValid) {
			int tn = ((lat+lon) != 0) ? calcTn(lat, lon) : -1;
			this.node2tn.seek(seekPos);
			this.node2tn.writeInt(tn);
			return tn;
		}
		return -1;
	}
	
	/**
	 * setTn - set tile number explicitly
	 * @param nodeId
	 * @param tn
	 * @throws IOException
	 */
	public void setTn(long nodeId, int tn) throws IOException {
		long seekPos = nodeId*4;
		
		if (this.node2tn.length() < seekPos) {
			this.node2tn.setLength(seekPos + (4*1024*1024*100));
		}

		this.node2tn.seek(seekPos);
		this.node2tn.writeInt(tn);
	}

	/**
	 * getTn - get tile number
	 * @param nodeId
	 * @return
	 * @throws Exception
	 */
	public int getTn(long nodeId) throws Exception {
		node2tn.seek(nodeId*4);
		int tn = node2tn.readInt();
		return tn;
	}
	
	/**
	 * calcTn - calculate tile number from lat/lon
	 * @param lat
	 * @param lon
	 * @return
	 */
	public static int calcTn(float lat, float lon) {
		return ((int)((lat + 90)/tilesizeLat) + (int)((lon + 180)/tilesizeLon) * (int)(180/tilesizeLon)) + 1;
	}
	
	/**
	 * getBounds - calculate bounding box from tile number
	 * @param tn
	 * @return
	 */
	public static float[] getBounds(int tn) {
		float minlat = ((tn - 1) % (180/tilesizeLon)) * tilesizeLat - 90;
		float minlon = (int)((tn - 1) * (tilesizeLon/180)) * tilesizeLat - 180;
		float maxlat = minlat + tilesizeLat;
		float maxlon = minlon + tilesizeLon;
		return new float[] {minlat, minlon, maxlat, maxlon};
	}
}
