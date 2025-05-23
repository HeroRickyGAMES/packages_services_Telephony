/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.telephony.sats2range.utils;

import static com.android.storage.s2.S2Support.FACE_BIT_COUNT;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.android.storage.util.BitwiseUtils;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/** A utility class for satellite tests */
public class TestUtils {
    public static final int TEST_S2_LEVEL = 12;
    public static final String TEST_DATA_RESOURCE_DIR = "data/";

    /** Returns a valid {@link SatS2RangeFileFormat}. */
    public static SatS2RangeFileFormat createS2RangeFileFormat(boolean isAllowedList) {
        return createS2RangeFileFormat(isAllowedList,
                /* entryValueSizeInBytes */0,
                /* versionNumber */0);
    }

    /** Returns a valid {@link SatS2RangeFileFormat}. */
    public static SatS2RangeFileFormat createS2RangeFileFormat(boolean isAllowedList,
            int entryValueSizeInBytes, int versionNumber) {
        int dataS2Level = TEST_S2_LEVEL;
        int faceIdBits = 3;
        int bitCountPerLevel = 2;
        int s2LevelBitCount = (dataS2Level * bitCountPerLevel) + faceIdBits;
        int prefixLevel = 5;
        int prefixBitCount = faceIdBits + (prefixLevel * bitCountPerLevel);
        int suffixBitCount = s2LevelBitCount - prefixBitCount;
        int suffixTableEntryBitCount = 4 * Byte.SIZE;
        int suffixTableBlockIdOffset = 5;
        return new SatS2RangeFileFormat(dataS2Level, prefixBitCount, suffixBitCount,
                suffixTableBlockIdOffset, suffixTableEntryBitCount, isAllowedList,
                entryValueSizeInBytes, versionNumber);
    }

    /** Create an S2 cell ID */
    public static long createCellId(
            SatS2RangeFileFormat fileFormat, int faceId, int otherPrefixBits, int suffixBits) {
        int prefixBitCount = fileFormat.getPrefixBitCount();
        int otherPrefixBitsCount = prefixBitCount - FACE_BIT_COUNT;
        int maxOtherPrefixBits = (int) BitwiseUtils.getLowBitsMask(otherPrefixBitsCount);
        if (otherPrefixBits > maxOtherPrefixBits) {
            throw new IllegalArgumentException("otherPrefixBits=" + otherPrefixBits
                    + " (" + Integer.toBinaryString(otherPrefixBits) + ")"
                    + " has more bits than otherPrefixBitsCount=" + otherPrefixBitsCount
                    + " allows");
        }

        int prefixValue = faceId;
        prefixValue <<= otherPrefixBitsCount;
        prefixValue |= otherPrefixBits;

        int suffixBitCount = fileFormat.getSuffixBitCount();
        if (suffixBits > BitwiseUtils.getLowBitsMask(suffixBitCount)) {
            throw new IllegalArgumentException(
                    "suffixBits=" + suffixBits + " (" + Integer.toBinaryString(suffixBits)
                            + ") has more bits than " + suffixBitCount + " bits allows");
        }
        return fileFormat.createCellId(prefixValue, suffixBits);
    }

    /**
     * Copy a test resource to the target directory.
     */
    public static Path copyTestResource(Class<?> baseClass, String testResource, Path targetDir)
            throws IOException {
        Files.createDirectories(targetDir);
        return copyResource(baseClass, TEST_DATA_RESOURCE_DIR + testResource, targetDir);
    }

    private static Path copyResource(Class<?> baseClass, String relativeResourcePath,
            Path targetDir) throws IOException {
        String fileName = relativeResourcePath;
        if (relativeResourcePath.contains("/")) {
            fileName = relativeResourcePath.substring(relativeResourcePath.lastIndexOf('/') + 1);
        }
        Path targetResourceFile = targetDir.resolve(fileName);
        try (InputStream inputStream = baseClass.getResourceAsStream(relativeResourcePath)) {
            if (inputStream == null) {
                fail("Resource=" + relativeResourcePath + " not found");
            }
            Files.copy(inputStream, targetResourceFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetResourceFile;
    }

    /** Create a temporary directory */
    public static Path createTempDir(Class<?> testClass) throws IOException {
        return Files.createTempDirectory(testClass.getSimpleName());
    }

    /** Delete a directory */
    public static void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes)
                    throws IOException {
                Files.deleteIfExists(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }
        });
        assertFalse(Files.exists(dir));
    }

    /** Create a valid test satellite S2 cell file */
    public static void createValidTestS2CellFile(
            File outputFile, SatS2RangeFileFormat fileFormat) throws Exception {
        try (PrintStream printer = new PrintStream(outputFile)) {
            // Range 1
            for (int suffix = 1000; suffix < 2000; suffix++) {
                printer.println(fileFormat.createCellId(0b100_11111111, suffix) + ",1");
            }

            // Range 2
            for (int suffix = 2001; suffix < 3000; suffix++) {
                printer.println(fileFormat.createCellId(0b100_11111111, suffix) + ",2");
            }

            // Range 3
            for (int suffix = 1000; suffix < 2000; suffix++) {
                printer.println(fileFormat.createCellId(0b101_11111111, suffix) + ",3");
            }
            printer.print(fileFormat.createCellId(0b101_11111111, 2000) + ",3");
        }
    }

    /** Create a invalid test satellite S2 cell file */
    public static void createInvalidTestS2CellFile(
            File outputFile, SatS2RangeFileFormat fileFormat) throws Exception {
        try (PrintStream printer = new PrintStream(outputFile)) {
            // Valid line
            printer.println(fileFormat.createCellId(0b100_11111111, 100) + ",0");

            // Invalid line
            printer.print("Invalid line");

            // Another valid line
            printer.println(fileFormat.createCellId(0b100_11111111, 200) + ",1");
        }
    }

    /** Create a valid test satellite S2 cell file */
    public static void createValidTestS2CellFileWithValidEntryValue(
            File outputFile, SatS2RangeFileFormat fileFormat) throws Exception {

        try (PrintStream printer = new PrintStream(outputFile)) {
            // Range 1
            for (int suffix = 1000; suffix < 1500; suffix++) {
                printer.println(fileFormat.createCellId(0b100_11111111, suffix) + ",1");
            }

            // Range 2
            for (int suffix = 1500; suffix < 2000; suffix++) {
                printer.println(fileFormat.createCellId(0b100_11111111, suffix) + ",2");
            }

            // Range 3
            for (int suffix = 2001; suffix < 3000; suffix++) {
                printer.println(fileFormat.createCellId(0b100_11111111, suffix) + ",3");
            }

            // Range 4
            for (int suffix = 1000; suffix < 2000; suffix++) {
                printer.println(fileFormat.createCellId(0b101_11111111, suffix) + ",4");
            }
            printer.print(fileFormat.createCellId(0b101_11111111, 2000) + ",4");
        }
    }
}
