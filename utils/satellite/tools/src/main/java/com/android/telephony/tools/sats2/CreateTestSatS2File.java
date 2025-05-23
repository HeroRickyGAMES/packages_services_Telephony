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
import com.android.telephony.sats2range.read.SuffixTableRange;
import com.android.telephony.sats2range.write.SatS2RangeFileWriter;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Creates a Sat S2 file with a small amount of test data. Useful for testing other tools. */
public final class CreateTestSatS2File {
    private static final int S2_LEVEL = 12;
    private static final boolean IS_ALLOWED_LIST = true;
    private static final int ENTRY_VALUE_BYTE_SIZE = 4;
    private static final int VERSION_NUMBER = 1;

    /**
     * Usage:
     * CreateTestSatS2File &lt;file name&gt;
     */
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);

        SatS2RangeFileFormat fileFormat = FileFormats.getFileFormatForLevel(S2_LEVEL,
                IS_ALLOWED_LIST, ENTRY_VALUE_BYTE_SIZE, VERSION_NUMBER);
        if (fileFormat.getPrefixBitCount() != 11) {
            throw new IllegalStateException("Fake data requires 11 prefix bits");
        }

        try (SatS2RangeFileWriter satS2RangeFileWriter =
                     SatS2RangeFileWriter.open(file, fileFormat)) {
            // Two ranges that share a prefix.
            SuffixTableRange range1 = new SuffixTableRange(
                    fileFormat.createCellId(0b100_11111111, 1000),
                    fileFormat.createCellId(0b100_11111111, 2000),
                    1);
            SuffixTableRange range2 = new SuffixTableRange(
                    fileFormat.createCellId(0b100_11111111, 2000),
                    fileFormat.createCellId(0b100_11111111, 3000),
                    2);
            // This range has a different face, so a different prefix, and will be in a different
            // suffix table.
            SuffixTableRange range3 = new SuffixTableRange(
                    fileFormat.createCellId(0b101_11111111, 1000),
                    fileFormat.createCellId(0b101_11111111, 2000),
                    3);
            List<SuffixTableRange> allRanges = listOf(range1, range2, range3);
            satS2RangeFileWriter.createSortedSuffixBlocks(allRanges.iterator());
        }
    }

    @SafeVarargs
    private static <E> List<E> listOf(E... values) {
        return Arrays.asList(values);
    }
}
