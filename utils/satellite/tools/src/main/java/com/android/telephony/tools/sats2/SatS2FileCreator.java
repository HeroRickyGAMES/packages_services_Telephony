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

package com.android.telephony.tools.sats2;

import com.android.telephony.sats2range.read.SatS2RangeFileFormat;
import com.android.telephony.sats2range.read.SatS2RangeFileReader;
import com.android.telephony.sats2range.read.SuffixTableRange;
import com.android.telephony.sats2range.write.SatS2RangeFileWriter;

import com.google.common.base.Stopwatch;
import com.google.common.geometry.S2CellId;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/** A util class for creating a satellite S2 file from the list of S2 cells. */
public final class SatS2FileCreator {
    /**
     * @param inputFile The input text file containing the list of S2 Cell IDs. Each line in the
     *                  file contains two numbers separated by a comma. The first number is in the
     *                  range of a 64bit number which represents the ID of a S2 cell. The second
     *                  number is satellite access config ID for the S2 cell.
     * @param s2Level The S2 level of all S2 cells in the input file.
     * @param isAllowedList {@code true} means the input file contains an allowed list of S2 cells.
     *                      {@code false} means the input file contains a disallowed list of S2
     *                      cells.
     * @param outputFile The output file to which the satellite S2 data in block format will be
     *                   written.
     */
    public static void create(String inputFile, int s2Level, boolean isAllowedList,
            int entryValueSizeInBytes, int versionNumber, String outputFile) throws Exception {
        // Read a list of S2 cells from input file
        List<Pair<Long, Integer>> s2Cells = readS2CellsFromFile(inputFile);
        System.out.println("Number of S2 cells read from file:" + s2Cells.size());

        // Convert the input list of S2 Cells into the list of sorted S2CellId
        System.out.println("Denormalizing S2 Cell IDs to the expected s2 level=" + s2Level);
        List<Pair<S2CellId, Integer>> sortedS2CellIds = normalize(s2Cells, s2Level);

        // IDs of S2CellId are converted to unsigned long numbers, which will be then used to
        // compare S2CellId.
        sortedS2CellIds.sort(Comparator.comparing(o -> o.first));
        System.out.println("Number of S2 cell IDs:" + sortedS2CellIds.size());

        // Compress the list of S2CellId into S2 ranges
        List<SatS2Range> satS2Ranges = createSatS2Ranges(sortedS2CellIds, s2Level);

        // Write the S2 ranges into a block file
        SatS2RangeFileFormat fileFormat = FileFormats.getFileFormatForLevel(s2Level, isAllowedList,
                entryValueSizeInBytes, versionNumber);
        try (SatS2RangeFileWriter satS2RangeFileWriter =
                     SatS2RangeFileWriter.open(new File(outputFile), fileFormat)) {
            Iterator<SuffixTableRange> suffixTableRangeIterator = satS2Ranges
                    .stream()
                    .map(x -> new SuffixTableRange(x.rangeStart.id(), x.rangeEnd.id(),
                            x.entryValue))
                    .iterator();
            /*
             * Group the sorted ranges into contiguous suffix blocks. Big ranges might get split as
             * needed to fit them into suffix blocks.
             */
            satS2RangeFileWriter.createSortedSuffixBlocks(suffixTableRangeIterator);
        }

        // Validate the output block file
        System.out.println("Validating the output block file...");
        try (SatS2RangeFileReader satS2RangeFileReader =
                     SatS2RangeFileReader.open(new File(outputFile))) {
            if (isAllowedList != satS2RangeFileReader.isAllowedList()) {
                throw new IllegalStateException("isAllowedList="
                        + satS2RangeFileReader.isAllowedList() + " does not match the input "
                        + "argument=" + isAllowedList);
            }

            // Verify that all input S2 cells are present in the output block file and the their
            // entry value matches the provided entry value
            for (Pair<S2CellId, Integer> s2CellInfo : sortedS2CellIds) {
                SuffixTableRange entry =
                        satS2RangeFileReader.findEntryByCellId(s2CellInfo.first.id());
                if (entry == null) {
                    throw new IllegalStateException("s2CellInfo=" + s2CellInfo
                            + " is not present in the output sat s2 file");
                } else if (entry.getEntryValue() != s2CellInfo.second) {
                    throw new IllegalStateException("entry.getEntryValue=" + entry.getEntryValue()
                            + " does not match the provided entry value=" + s2CellInfo.second);
                }
            }

            // Verify the cell right before the first cell in the sortedS2CellIds is not present in
            // the output block file
            S2CellId prevCell = sortedS2CellIds.getFirst().first.prev();
            boolean containsPrevCell = sortedS2CellIds.stream()
                    .anyMatch(pair -> pair.first.equals(prevCell));
            if (!containsPrevCell
                    && satS2RangeFileReader.findEntryByCellId(prevCell.id()) != null) {
                throw new IllegalStateException("The cell " + prevCell + ", which is right "
                        + "before the first cell is unexpectedly present in the output sat s2"
                        + " file");
            } else {
                System.out.println("prevCell=" + prevCell + " is in the sortedS2CellIds");
            }

            // Verify the cell right after the last cell in the sortedS2CellIds is not present in
            // the output block file
            S2CellId nextCell = sortedS2CellIds.getLast().first.next();
            boolean containsNextCell = sortedS2CellIds.stream()
                    .anyMatch(pair -> pair.first.equals(nextCell));
            if (!containsNextCell
                    && satS2RangeFileReader.findEntryByCellId(nextCell.id()) != null) {
                throw new IllegalStateException("The cell " + nextCell + ", which is right "
                        + "after the last cell is unexpectedly present in the output sat s2"
                        + " file");
            } else {
                System.out.println("nextCell=" + nextCell + " is in the sortedS2CellIds");
            }
        }
        System.out.println("Successfully validated the output block file");
    }

    /**
     * Read a list of S2 cells from the inputFile.
     *
     * @param inputFile A file containing the list of S2 cells. Each line in the inputFile contains
     *                  a 64-bit number - the ID of a S2 cell.
     * @return A list of S2 cells.
     */
    private static List<Pair<Long, Integer>> readS2CellsFromFile(String inputFile)
            throws Exception {
        List<Pair<Long, Integer>> s2Cells = new ArrayList<>();
        InputStream inputStream = new FileInputStream(inputFile);
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] s2CellInfoStrs = line.split(",");
                if (s2CellInfoStrs == null || s2CellInfoStrs.length != 2) {
                    throw new IllegalStateException("The Input s2 cell file has invalid format, "
                            + "current line=" + line);
                }
                try {
                    s2Cells.add(new Pair<>(Long.parseLong(s2CellInfoStrs[0]),
                            Integer.parseUnsignedInt(s2CellInfoStrs[1])));
                } catch (Exception ex) {
                    throw new IllegalStateException("Input s2 cell file has invalid format, "
                            + "current line=" + line + ", ex=" + ex);
                }
            }
        }
        return s2Cells;
    }

    /**
     * Convert the list of S2 Cell numbers into the list of S2 Cell IDs at the expected level.
     */
    private static List<Pair<S2CellId, Integer>> normalize(
            List<Pair<Long, Integer>> s2CellNumbers, int s2Level) {
        Map<S2CellId, Integer> s2CellIdMap = new HashMap<>();
        for (Pair<Long, Integer> s2CellInfo : s2CellNumbers) {
            S2CellId s2CellId = new S2CellId(s2CellInfo.first);
            if (s2CellId.level() == s2Level) {
                if (!s2CellIdMap.containsKey(s2CellId)) {
                    s2CellIdMap.put(s2CellId, s2CellInfo.second);
                }
            } else if (s2CellId.level() < s2Level) {
                S2CellId childEnd = s2CellId.childEnd(s2Level);
                for (s2CellId = s2CellId.childBegin(s2Level); !s2CellId.equals(childEnd);
                        s2CellId = s2CellId.next()) {
                    if (!s2CellIdMap.containsKey(s2CellId)) {
                        s2CellIdMap.put(s2CellId, s2CellInfo.second);
                    }
                }
            } else {
                S2CellId parent = s2CellId.parent(s2Level);
                if (!s2CellIdMap.containsKey(parent)) {
                    s2CellIdMap.put(parent, s2CellInfo.second);
                }
            }
        }

        List<Pair<S2CellId, Integer>> result = new ArrayList();
        for (Map.Entry<S2CellId, Integer> entry : s2CellIdMap.entrySet()) {
            result.add(new Pair<>(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Compress the list of sorted S2CellId into S2 ranges.
     *
     * @param sortedS2CellIds List of S2CellId sorted in ascending order.
     * @param s2Level The level of all S2CellId.
     * @return List of S2 ranges.
     */
    private static List<SatS2Range> createSatS2Ranges(List<Pair<S2CellId, Integer>> sortedS2CellIds,
            int s2Level) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<SatS2Range> ranges = new ArrayList<>();
        if (sortedS2CellIds != null && !sortedS2CellIds.isEmpty()) {
            S2CellId rangeStart = null;
            S2CellId rangeEnd = null;
            int rangeEntryValue = 0;
            for (int i = 0; i < sortedS2CellIds.size(); i++) {
                S2CellId currentS2CellId = sortedS2CellIds.get(i).first;
                checkCellIdIsAtLevel(currentS2CellId, s2Level);

                SatS2Range currentRange = createS2Range(currentS2CellId, s2Level,
                        sortedS2CellIds.get(i).second);
                S2CellId currentS2CellRangeStart = currentRange.rangeStart;
                S2CellId currentS2CellRangeEnd = currentRange.rangeEnd;

                if (rangeStart == null) {
                    // First time round the loop initialize rangeStart / rangeEnd only.
                    rangeStart = currentS2CellRangeStart;
                    rangeEntryValue = currentRange.entryValue;
                } else if (rangeEnd.id() != currentS2CellRangeStart.id()
                        || currentRange.entryValue != rangeEntryValue) {
                    // If there's a gap between cellIds or entry values are different, store the
                    // range we have so far and start a new range.
                    ranges.add(new SatS2Range(rangeStart, rangeEnd, rangeEntryValue));
                    rangeStart = currentS2CellRangeStart;
                    rangeEntryValue = currentRange.entryValue;
                }
                rangeEnd = currentS2CellRangeEnd;
            }
            ranges.add(new SatS2Range(rangeStart, rangeEnd, rangeEntryValue));
        }

        // Sorting the ranges is not necessary. As the input is sorted , it will already be sorted.
        System.out.printf("Created %s SatS2Ranges in %s milliseconds\n",
                ranges.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
        return ranges;
    }

    /**
     * @return A pair of S2CellId for the range [s2CellId, s2CellId's next sibling)
     */
    private static SatS2Range createS2Range(S2CellId s2CellId, int s2Level, int entryValue) {
        // Since s2CellId is at s2Level, s2CellId.childBegin(s2Level) returns itself.
        S2CellId firstS2CellRangeStart = s2CellId.childBegin(s2Level);
        // Get the immediate next sibling of s2CellId
        S2CellId firstS2CellRangeEnd = s2CellId.childEnd(s2Level);

        if (firstS2CellRangeEnd.face() < firstS2CellRangeStart.face()
                || !firstS2CellRangeEnd.isValid()) {
            // Fix this if it becomes an issue.
            throw new IllegalStateException("firstS2CellId=" + s2CellId
                    + ", childEnd(" + s2Level + ") produced an unsupported"
                    + " value=" + firstS2CellRangeEnd);
        }
        return new SatS2Range(firstS2CellRangeStart, firstS2CellRangeEnd, entryValue);
    }

    private static void checkCellIdIsAtLevel(S2CellId cellId, int s2Level) {
        if (cellId.level() != s2Level) {
            throw new IllegalStateException("Bad level for cellId=" + cellId
                    + ". Must be s2Level=" + s2Level);
        }
    }

    /**
     * A range of S2 cell IDs at a fixed S2 level. The range is expressed as a start cell ID
     * (inclusive) and an end cell ID (exclusive).
     */
    private static class SatS2Range {
        public final int entryValue;
        public final S2CellId rangeStart;
        public final S2CellId rangeEnd;

        /**
         * Creates an instance. If the range is invalid or the cell IDs are from different levels
         * this method throws an {@link IllegalArgumentException}.
         */
        SatS2Range(S2CellId rangeStart, S2CellId rangeEnd, int entryValue) {
            this.entryValue = entryValue;
            this.rangeStart = Objects.requireNonNull(rangeStart);
            this.rangeEnd = Objects.requireNonNull(rangeEnd);
            if (rangeStart.level() != rangeEnd.level()) {
                throw new IllegalArgumentException(
                        "Levels differ: rangeStart=" + rangeStart + ", rangeEnd=" + rangeEnd);
            }
            if (rangeStart.greaterOrEquals(rangeEnd)) {
                throw new IllegalArgumentException(
                        "Range start (" + rangeStart + " >= range end (" + rangeEnd + ")");
            }
        }
    }

    /** A basic pair class. */
    static class Pair<A, B> {

        public final A first;

        public final B second;

        Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }
}
