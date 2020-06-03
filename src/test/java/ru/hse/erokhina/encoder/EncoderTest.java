package ru.hse.erokhina.encoder;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class EncoderTest {

    @org.junit.Test
    public void encodeDecodeUpdContext() {
        List<String> modification = Collections.singletonList("UPD 27@@ 25@@");
        Encoder encoder = new Encoder(true);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecodeDelContext() {
        List<String> modification = Collections.singletonList("DEL 42@@ 27@@");
        Encoder encoder = new Encoder(true);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecodeInsContext() {
        List<String> modification = Collections.singletonList("INS 8@@ 25@@ 2");
        Encoder encoder = new Encoder(true);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecodeMovContext() {
        List<String> modification = Collections.singletonList("MOV 32@@ 27@@ 1");
        Encoder encoder = new Encoder(true);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecodeUpd() {
        List<String> modification = Collections.singletonList("UPD 27@@");
        Encoder encoder = new Encoder(false);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecodeDel() {
        List<String> modification = Collections.singletonList("DEL 42@@");
        Encoder encoder = new Encoder(false);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecodeIns() {
        List<String> modification = Collections.singletonList("INS 8@@ 25@@ 2");
        Encoder encoder = new Encoder(false);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecodeMov() {
        List<String> modification = Collections.singletonList("MOV 32@@ 27@@ 1");
        Encoder encoder = new Encoder(false);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecode2gram() {
        List<String> modification = Arrays.asList("INS 40@@ 4@@ 8", "DEL 58@@ 24@@");
        Encoder encoder = new Encoder(true);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecode3gram() {
        List<String> modification = Arrays.asList("INS 8@@ 54@@ 0", "UPD 42@@ 59@@", "DEL 39@@ 58@@");
        Encoder encoder = new Encoder(true);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecode4gram() {
        List<String> modification = Arrays.asList("UPD 27@@ 25@@", "MOV 42@@ 43@@ 0", "INS 52@@ 7@@ 1", "DEL 7@@ 21@@");
        Encoder encoder = new Encoder(true);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

    @org.junit.Test
    public void encodeDecode5gram() {
        List<String> modification = Arrays.asList("INS 51@@ 8@@ 4", "INS 60@@ 8@@ 0", "INS 40@@ 7@@ 0",
                                                    "INS 42@@ 32@@ 0", "INS 42@@ 59@@ 0");
        Encoder encoder = new Encoder(true);

        BitSet bitSet = encoder.encode(modification);
        String decodedModification = encoder.decode(bitSet);

        assertEquals(String.join(" ", modification), decodedModification);
    }

}