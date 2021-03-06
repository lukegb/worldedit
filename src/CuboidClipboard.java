// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

import com.sk89q.worldedit.*;
import com.sk89q.worldedit.blocks.*;
import com.sk89q.worldedit.data.*;
import org.jnbt.*;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * The clipboard remembers the state of a cuboid region.
 *
 * @author sk89q
 */
public class CuboidClipboard {
    private BaseBlock[][][] data;
    private Vector offset;
    private Vector origin;
    private Vector size;

    /**
     * Constructs the clipboard.
     *
     * @param size
     */
    public CuboidClipboard(Vector size) {
        this.size = size;
        data = new BaseBlock[size.getBlockX()][size.getBlockY()][size.getBlockZ()];
        origin = new Vector();
        offset = new Vector();
    }

    /**
     * Constructs the clipboard.
     *
     * @param size
     * @param origin
     */
    public CuboidClipboard(Vector size, Vector origin) {
        this.size = size;
        data = new BaseBlock[size.getBlockX()][size.getBlockY()][size.getBlockZ()];
        this.origin = origin;
        offset = new Vector();
    }

    /**
     * Constructs the clipboard.
     *
     * @param size
     * @param origin
     */
    public CuboidClipboard(Vector size, Vector origin, Vector offset) {
        this.size = size;
        data = new BaseBlock[size.getBlockX()][size.getBlockY()][size.getBlockZ()];
        this.origin = origin;
        this.offset = offset;
    }

    /**
     * Get the width (X-direction) of the clipboard.
     *
     * @return width
     */
    public int getWidth() {
        return size.getBlockX();
    }

    /**
     * Get the length (Z-direction) of the clipboard.
     *
     * @return length
     */
    public int getLength() {
        return size.getBlockZ();
    }

    /**
     * Get the height (Y-direction) of the clipboard.
     *
     * @return height
     */
    public int getHeight() {
        return size.getBlockY();
    }

    /**
     * Rotate the clipboard in 2D. It can only rotate by angles divisible by 90.
     * 
     * @param angle in degrees
     */
    public void rotate2D(int angle) {
        angle = angle % 360;
        if (angle % 90 != 0) { // Can only rotate 90 degrees at the moment
            return;
        }

        int width = getWidth();
        int length = getLength();
        int height = getHeight();
        Vector sizeRotated = size.transform2D(angle, 0, 0, 0, 0);
        int shiftX = sizeRotated.getX() < 0 ? -sizeRotated.getBlockX() - 1 : 0;
        int shiftZ = sizeRotated.getZ() < 0 ? -sizeRotated.getBlockZ() - 1 : 0;

        BaseBlock newData[][][] = new BaseBlock
                [Math.abs(sizeRotated.getBlockX())]
                [Math.abs(sizeRotated.getBlockY())]
                [Math.abs(sizeRotated.getBlockZ())];

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                Vector v = (new Vector(x, 0, z)).transform2D(angle, 0, 0, 0, 0);
                int newX = v.getBlockX();
                int newZ = v.getBlockZ();
                for (int y = 0; y < height; y++) {
                    newData[shiftX + newX][y][shiftZ + newZ] = data[x][y][z];
                }
            }
        }

        data = newData;
        size = new Vector(Math.abs(sizeRotated.getBlockX()),
                          Math.abs(sizeRotated.getBlockY()),
                          Math.abs(sizeRotated.getBlockZ()));
        offset = offset.transform2D(angle, 0, 0, 0, 0)
                .subtract(shiftX, 0, shiftZ);;
    }

    /**
     * Copy to the clipboard.
     *
     * @param editSession
     */
    public void copy(EditSession editSession) {
        for (int x = 0; x < size.getBlockX(); x++) {
            for (int y = 0; y < size.getBlockY(); y++) {
                for (int z = 0; z < size.getBlockZ(); z++) {
                    data[x][y][z] =
                        editSession.getBlock(new Vector(x, y, z).add(getOrigin()));
                }
            }
        }
    }

    /**
     * Paste from the clipboard.
     *
     * @param editSession
     * @param newOrigin Position to paste it from
     * @param noAir True to not paste air
     * @throws MaxChangedBlocksException
     */
    public void paste(EditSession editSession, Vector newOrigin, boolean noAir)
            throws MaxChangedBlocksException {
        place(editSession, newOrigin.add(offset), noAir);
    }

    /**
     * Places the blocks in a position from the minimum corner.
     * 
     * @param editSession
     * @param pos
     * @param noAir
     * @throws MaxChangedBlocksException
     */
    public void place(EditSession editSession, Vector pos, boolean noAir)
            throws MaxChangedBlocksException {
        for (int x = 0; x < size.getBlockX(); x++) {
            for (int y = 0; y < size.getBlockY(); y++) {
                for (int z = 0; z < size.getBlockZ(); z++) {
                    if (noAir && data[x][y][z].isAir())
                        continue;

                    editSession.setBlock(new Vector(x, y, z).add(pos),
                            data[x][y][z]);
                }
            }
        }
    }

    /**
     * Saves the clipboard data to a .schematic-format file.
     *
     * @param path
     * @throws IOException
     * @throws DataException
     */
    public void saveSchematic(String path) throws IOException, DataException {
        int width = getWidth();
        int height = getHeight();
        int length = getLength();

        if (width > 65535) {
            throw new DataException("Width of region too large for a .schematic");
        }
        if (height > 65535) {
            throw new DataException("Height of region too large for a .schematic");
        }
        if (length > 65535) {
            throw new DataException("Length of region too large for a .schematic");
        }

        HashMap<String,Tag> schematic = new HashMap<String,Tag>();
        schematic.put("Width", new ShortTag("Width", (short)width));
        schematic.put("Length", new ShortTag("Length", (short)length));
        schematic.put("Height", new ShortTag("Height", (short)height));
        schematic.put("Materials", new StringTag("Materials", "Alpha"));
        schematic.put("WEOriginX", new IntTag("WEOriginX", getOrigin().getBlockX()));
        schematic.put("WEOriginY", new IntTag("WEOriginY", getOrigin().getBlockY()));
        schematic.put("WEOriginZ", new IntTag("WEOriginZ", getOrigin().getBlockZ()));

        // Copy
        byte[] blocks = new byte[width * height * length];
        byte[] blockData = new byte[width * height * length];
        ArrayList<Tag> tileEntities = new ArrayList<Tag>();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    int index = y * width * length + z * width + x;
                    blocks[index] = (byte)data[x][y][z].getID();
                    blockData[index] = (byte)data[x][y][z].getData();

                    // Store TileEntity data
                    if (data[x][y][z] instanceof TileEntityBlock) {
                        TileEntityBlock tileEntityBlock =
                                (TileEntityBlock)data[x][y][z];

                        // Get the list of key/values from the block
                        Map<String,Tag> values = tileEntityBlock.toTileEntityNBT();
                        if (values != null) {
                            values.put("id", new StringTag("id",
                                    tileEntityBlock.getTileEntityID()));
                            values.put("x", new IntTag("x", x));
                            values.put("y", new IntTag("y", y));
                            values.put("z", new IntTag("z", z));
                            CompoundTag tileEntityTag =
                                    new CompoundTag("TileEntity", values);
                            tileEntities.add(tileEntityTag);
                        }
                    }
                }
            }
        }
        
        schematic.put("Blocks", new ByteArrayTag("Blocks", blocks));
        schematic.put("Data", new ByteArrayTag("Data", blockData));
        schematic.put("Entities", new ListTag("Entities", CompoundTag.class, new ArrayList<Tag>()));
        schematic.put("TileEntities", new ListTag("TileEntities", CompoundTag.class, tileEntities));

        // Build and output
        CompoundTag schematicTag = new CompoundTag("Schematic", schematic);
        NBTOutputStream stream = new NBTOutputStream(new FileOutputStream(path));
        stream.writeTag(schematicTag);
        stream.close();
    }

    /**
     * Load a .schematic file into a clipboard.
     * 
     * @param path
     * @param origin
     * @return clipboard
     * @throws DataException
     * @throws IOException
     */
    public static CuboidClipboard loadSchematic(String path)
            throws DataException, IOException {
        FileInputStream stream = new FileInputStream(path);
        NBTInputStream nbtStream = new NBTInputStream(stream);

        Vector origin = new Vector();

        // Schematic tag
        CompoundTag schematicTag = (CompoundTag)nbtStream.readTag();
        if (!schematicTag.getName().equals("Schematic")) {
            throw new DataException("Tag \"Schematic\" does not exist or is not first");
        }

        // Check
        Map<String,Tag> schematic = schematicTag.getValue();
        if (!schematic.containsKey("Blocks")) {
            throw new DataException("Schematic file is missing a \"Blocks\" tag");
        }

        // Get information
        short width = (Short)getChildTag(schematic, "Width", ShortTag.class).getValue();
        short length = (Short)getChildTag(schematic, "Length", ShortTag.class).getValue();
        short height = (Short)getChildTag(schematic, "Height", ShortTag.class).getValue();

        try {
            int originX = (Integer)getChildTag(schematic, "WEOriginX", IntTag.class).getValue();
            int originY = (Integer)getChildTag(schematic, "WEOriginY", IntTag.class).getValue();
            int originZ = (Integer)getChildTag(schematic, "WEOriginZ", IntTag.class).getValue();
            origin = new Vector(originX, originY, originZ);
        } catch (DataException e) {
            // No origin data
        }

        // Check type of Schematic
        String materials = (String)getChildTag(schematic, "Materials", StringTag.class).getValue();
        if (!materials.equals("Alpha")) {
            throw new DataException("Schematic file is not an Alpha schematic");
        }

        // Get blocks
        byte[] blocks = (byte[])getChildTag(schematic, "Blocks", ByteArrayTag.class).getValue();
        byte[] blockData = (byte[])getChildTag(schematic, "Data", ByteArrayTag.class).getValue();

        // Need to pull out tile entities
        List<Tag> tileEntities = (List<Tag>)((ListTag)getChildTag(schematic, "TileEntities", ListTag.class))
                .getValue();
        Map<BlockVector,Map<String,Tag>> tileEntitiesMap =
                new HashMap<BlockVector,Map<String,Tag>>();

        for (Tag tag : tileEntities) {
            if (!(tag instanceof CompoundTag)) continue;
            CompoundTag t = (CompoundTag)tag;

            int x = 0;
            int y = 0;
            int z = 0;

            Map<String,Tag> values = new HashMap<String,Tag>();

            for (Map.Entry<String,Tag> entry : t.getValue().entrySet()) {
                if (entry.getKey().equals("x")) {
                    if (entry.getValue() instanceof IntTag) {
                        x = ((IntTag)entry.getValue()).getValue();
                    }
                } else if (entry.getKey().equals("y")) {
                    if (entry.getValue() instanceof IntTag) {
                        y = ((IntTag)entry.getValue()).getValue();
                    }
                } else if (entry.getKey().equals("z")) {
                    if (entry.getValue() instanceof IntTag) {
                        z = ((IntTag)entry.getValue()).getValue();
                    }
                }

                values.put(entry.getKey(), entry.getValue());
            }

            BlockVector vec = new BlockVector(x, y, z);
            tileEntitiesMap.put(vec, values);
        }

        Vector size = new Vector(width, height, length);
        CuboidClipboard clipboard = new CuboidClipboard(size);
        clipboard.setOrigin(origin);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    int index = y * width * length + z * width + x;
                    BlockVector pt = new BlockVector(x, y, z);
                    BaseBlock block;

                    if (blocks[index] == 63 || blocks[index] == 68) {
                        block = new SignBlock(blocks[index], blockData[index]);
                        if (tileEntitiesMap.containsKey(pt)) {
                            ((TileEntityBlock)block).fromTileEntityNBT(
                                    tileEntitiesMap.get(pt));
                        }
                    } else if(blocks[index] == 54) {
                        block = new ChestBlock();
                        if (tileEntitiesMap.containsKey(pt)) {
                            ((TileEntityBlock)block).fromTileEntityNBT(
                                    tileEntitiesMap.get(pt));
                        }
                    } else {
                        block = new BaseBlock(blocks[index], blockData[index]);
                    }

                    clipboard.data[x][y][z] = block;
                }
            }
        }

        return clipboard;
    }

    /**
     * Get child tag of a NBT structure.
     * 
     * @param items
     * @param key
     * @param expected
     * @return child tag
     * @throws DataException
     */
    private static Tag getChildTag(Map<String,Tag> items, String key, Class expected)
            throws DataException {
        if (!items.containsKey(key)) {
            throw new DataException("Schematic file is missing a \"" + key + "\" tag");
        }
        Tag tag = items.get(key);
        if (!expected.isInstance(tag)) {
            throw new DataException(
                key + " tag is not of tag type " + expected.getName());
        }
        return tag;
    }

    /**
     * @return the origin
     */
    public Vector getOrigin() {
        return origin;
    }

    /**
     * @param origin the origin to set
     */
    public void setOrigin(Vector origin) {
        this.origin = origin;
    }
}
