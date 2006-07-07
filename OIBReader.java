//
// OIBReader.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-2006 Melissa Linkert, Curtis Rueden and Eric Kjellman.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Library General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Library General Public License for more details.

You should have received a copy of the GNU Library General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;

/**
 * OIBReader is the file format reader for Fluoview FV1000 OIB files.
 *
 * @author Melissa Linkert linkert at cs.wisc.edu
 */
public class OIBReader extends FormatReader {

  // -- Fields --

  /** Flag indicating whether current file is little endian. */
  protected boolean littleEndian;

  /** Number of images in the file. */
  private int numImages;

  /** Width of normal image. */
  private int width;

  /** Height of normal image. */
  private int height;

  /** Pixel data. */
  private Hashtable pixelData;

  // -- Constructor --

  /** Constructs a new OIB reader. */
  public OIBReader() { super("Fluoview FV1000 OIB", "oib"); }


  // -- FormatReader API methods --

  /** Checks if the given block is a valid header for an OIB file. */
  public boolean isThisType(byte[] block) {
    return false;
  }

  /** Determines the number of images in the given OIB file. */
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return numImages;
  }

  /** Checks if the images in the file are RGB. */
  public boolean isRGB(String id) throws FormatException, IOException {
    return false;
  }

  /** Returns the number of channels in the file. */
  public int getChannelCount(String id) throws FormatException, IOException {
    return 1;
  }

  /** Obtains the specified image from the given OIB file as a byte array. */
  public byte[] openBytes(String id, int no)
    throws FormatException, IOException
  {
    return ImageTools.getBytes(openImage(id, no), false, no);
  }

  /** Obtains the specified image from the given OIB file. */
  public BufferedImage openImage(String id, int no)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);

    if (no < 0 || no >= getImageCount(id)) {
      throw new FormatException("Invalid image number: " + no);
    }

    byte[] pixels = (byte[]) pixelData.get(new Integer(no));
    RandomAccessStream ra = new RandomAccessStream(pixels);

    Hashtable[] fds = TiffTools.getIFDs(ra, 0);
    BufferedImage img = TiffTools.getImage(fds[0], ra);
    ra.close();
    return img;
  }

  /** Closes any open files. */
  public void close() throws FormatException, IOException {
    currentId = null;
  }

  /** Initializes the given OIB file. */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);

    pixelData = new Hashtable();

    OLEParser parser = new OLEParser(id);
    parser.parse(0);
    Vector[] files = parser.getFiles();

    for (int i=0; i<files[0].size(); i++) {
      byte[] data = (byte[]) files[1].get(i);
      String pathName = ((String) files[0].get(i)).trim();
      pathName = DataTools.stripString(pathName);

      if (pathName.endsWith("OibInfo.txt")) {
        // some kind of metadata
      }
      else if (pathName.indexOf("Stream") != -1) {
        // first get the image number
        String num = pathName.substring(pathName.indexOf("Stream") + 6);
        if (TiffTools.isValidHeader(data)) {
          if (width == 0 && height == 0) {
            // get the width and height
            RandomAccessStream ras = new RandomAccessStream(data);
            Hashtable[] ifds = TiffTools.getIFDs(ras);
            width = TiffTools.getIFDIntValue(ifds[0], TiffTools.IMAGE_WIDTH,
              false, -1);
            height = TiffTools.getIFDIntValue(ifds[0], TiffTools.IMAGE_LENGTH,
              false, -1);
            pixelData.put(Integer.valueOf(num), data);
            ras.close();
            ras = null;
          }
          else {
            RandomAccessStream ra = new RandomAccessStream(data);
            try {
              Hashtable[] ifds = TiffTools.getIFDs(ra);
              int w = TiffTools.getIFDIntValue(ifds[0], TiffTools.IMAGE_WIDTH,
                false, -1);
              int h = TiffTools.getIFDIntValue(ifds[0], TiffTools.IMAGE_LENGTH,
                false, -1);
              if (w == width && h == height) {
                pixelData.put(Integer.valueOf(num), data);
              }
            }
            catch (IOException e) { }
            ra.close();
            ra = null;
          }
        }
      }
    }

    // align keys in pixelData

    Integer[] keys = (Integer[]) pixelData.keySet().toArray(new Integer[0]);
    Arrays.sort(keys);

    Hashtable t = new Hashtable();
    int i = 0;
    for (int j=0; j<keys.length; j++) {
      t.put(new Integer(i), pixelData.get(keys[j]));
      i++;
    }
    pixelData = t;

    numImages = pixelData.size();

    // initialize metadata

    TiffReader btr = new TiffReader();
    btr.ifds = TiffTools.getIFDs(new RandomAccessStream(
      (byte[]) pixelData.get(new Integer(0))));
    btr.initMetadata();
    metadata = btr.metadata;  // hack
    ome = btr.ome;
    OMETools.setSizeZ(ome, numImages);
  }


  // -- Main method --

  public static void main(String[] args) throws FormatException, IOException {
    new OIBReader().testRead(args);
  }

}
