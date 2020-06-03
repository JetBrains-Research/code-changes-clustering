package ru.hse.erokhina.encoder;

import sun.jvm.hotspot.utilities.Bits;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class Encoder {
    private final static int CODE_LEN = 29;
    //private final static int LEN_WITH_CONTEXT = 36;
    private final static int MODIFICATION_TYPE_LEN = 2;
    private final static int NODE_TYPE_LEN = 7;
    private final static int POSITION_LEN = 13;

    private Boolean useContext;
    private int curModificationLen;

    public Encoder(Boolean useContext) {
        this.useContext = useContext;
        this.curModificationLen = CODE_LEN;
    }

    public BitSet encode(List<String> actions) {
        int numActions = actions.size();

        BitSet result = new BitSet(numActions * curModificationLen);
        //System.out.println("INITIAL SIZE: " + numActions * curModificationLen + " " + result.size() + " " + result.length());

        for (int i = 0; i < numActions; i++) {
            String action = actions.get(i);

            int from = i * curModificationLen;
            int to = (i + 1) * curModificationLen;

            String[] tokens = action.split(" ");

            switch (tokens[0]) {
                case "INS": //00
                    break;
                case "DEL": //01
                    result.set(from + 1);
                    break;
                case "MOV": //10
                    result.set(from);
                    break;
                case "UPD": //11
                    result.set(from);
                    result.set(from + 1);
                    break;
            }

            int nodeType = getNodeType(tokens[1]);
            for (int j = 0; j < NODE_TYPE_LEN; j++) {
                if ((nodeType & (1 << j)) > 0)
                    result.set(from + MODIFICATION_TYPE_LEN + j);
            }

            switch (tokens[0]) {
                case "INS":
                case "MOV":
                    nodeType = getNodeType(tokens[2]);
                    for (int j = 0; j < NODE_TYPE_LEN; j++) {
                        if ((nodeType & (1 << j)) > 0)
                            result.set(from + MODIFICATION_TYPE_LEN + NODE_TYPE_LEN + j);
                    }

                    int position = Integer.parseInt(tokens[3]);
                    for (int j = 0; j < POSITION_LEN; j++) {
                        if ((position & (1 << j)) > 0)
                            result.set(from + MODIFICATION_TYPE_LEN + NODE_TYPE_LEN + NODE_TYPE_LEN + j);
                    }

                    break;
                case "DEL":
                case "UPD":
                    if (useContext) {
                        int contextPosition = 2;
                        int contextFrom = from + MODIFICATION_TYPE_LEN + NODE_TYPE_LEN;

                        nodeType = getNodeType(tokens[contextPosition]);
                        for (int j = 0; j < NODE_TYPE_LEN; j++) {
                            if ((nodeType & (1 << j)) > 0)
                                result.set(contextFrom + j);
                        }
                    }

                    break;
            }
        }

        System.out.println(actions);
        System.out.println(result.toString());



        return result;
    }

    private int getNodeType(String token) {
        String nodeTypeTag = "@@";
        String[] splitted = token.split(nodeTypeTag);
        return Integer.parseInt(splitted[0]);
    }

    public BitSet getActionsSublist(BitSet actions, int fromIndex, int toIndex) {
        int from = fromIndex * curModificationLen;
        int to = toIndex * curModificationLen;

        return actions.get(from, to);
    }

    public String decode(BitSet actions) {
        StringBuilder builder = new StringBuilder();

        String nodeTypeTag = "@@";

        int i = 0;
        int from = i * curModificationLen;
        int to = (i + 1) * curModificationLen;
        BitSet modification = actions.get(from, to);

        while (!modification.isEmpty()) {
            String modificationType = "";
            switch (modification.get(0, MODIFICATION_TYPE_LEN).toString()) {
                case "{}":
                    modificationType = "INS";
                    break;
                case "{1}":
                    modificationType = "DEL";
                    break;
                case "{0}":
                    modificationType = "MOV";
                    break;
                case "{0, 1}":
                    modificationType = "UPD";
                    break;
            }
            builder.append(modificationType);
            builder.append(" ");

            int nodeType = 0;
            for (int j = 0; j < NODE_TYPE_LEN; j++) {
                if (modification.get(MODIFICATION_TYPE_LEN + j))
                    nodeType |= (1 << j);
            }

            builder.append(nodeType);
            builder.append(nodeTypeTag);
            builder.append(" ");

            switch (modificationType) {
                case "INS":
                case "MOV":
                    nodeType = 0;
                    for (int j = 0; j < NODE_TYPE_LEN; j++) {
                        if (modification.get(MODIFICATION_TYPE_LEN + NODE_TYPE_LEN + j))
                            nodeType |= (1 << j);
                    }

                    builder.append(nodeType);
                    builder.append(nodeTypeTag);
                    builder.append(" ");

                    int position = 0;
                    for (int j = 0; j < POSITION_LEN; j++) {
                        if (modification.get(MODIFICATION_TYPE_LEN + NODE_TYPE_LEN + NODE_TYPE_LEN + j))
                            position |= (1 << j);
                    }

                    builder.append(position);
                    builder.append(" ");

                    break;
                case "DEL":
                case "UPD":
                    if (useContext) {
                        nodeType = 0;
                        for (int j = 0; j < NODE_TYPE_LEN; j++) {
                            if (modification.get(MODIFICATION_TYPE_LEN + NODE_TYPE_LEN + j))
                                nodeType |= (1 << j);
                        }

                        builder.append(nodeType);
                        builder.append(nodeTypeTag);
                        builder.append(" ");
                    }

                    break;
            }

            i++;
            from = i * curModificationLen;
            to = (i + 1) * curModificationLen;
            modification = actions.get(from, to);
        }

        return builder.toString().trim();
    }
}
