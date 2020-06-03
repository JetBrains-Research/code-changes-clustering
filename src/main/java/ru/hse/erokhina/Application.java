package ru.hse.erokhina;

import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import ru.hse.erokhina.ast.ASTActions;
import ru.hse.erokhina.representation.Parameters;
import ru.hse.erokhina.representation.RepresentationsStore;
import ru.hse.erokhina.utils.Pair;
import ru.hse.erokhina.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

public class Application {

    public static List<String> representationTypes = Arrays.asList("short_as_ngram", "concat");

    public static void main(String[] args) throws IOException {
        args = new String[8];
        args[0] = "represent";
        args[1] = "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/LaseDataset";
        args[2] = "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/СodeChangesRepresentation/LaseHists2";
        args[3] = "concat";
        args[4] = "3";
        args[5] = "true";
        args[6] = "true";
        args[7] = "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/СodeChangesRepresentation/LaseActionsTest2";

        if (args.length == 0) {
            System.out.println("Use command: represent");
            return;
        }
        switch (args[0]) {
            case "represent":
                if (args.length != 6 && args.length != 8) {
                    System.out.println("Expected: represent <path to directory with code changes> " +
                            "<path to file to save representations> <representation type> <n> " +
                            "<if use context> [<if save or use saved edit scripts>] " +
                            "[<path to directory with edit scripts>]\n");

                    return;
                }

                Parameters parameters = validateParameters(args[3], args[4], args[5]);

                Boolean saveOrUseActions = null;
                String pathToActions = null;
                if (args.length == 8) {
                    if (!args[6].equals("true") && !args[6].equals("false")) {
                        System.out.println("Wrong value for <if save or use saved edit scripts>.");
                    } else {
                        saveOrUseActions = Boolean.parseBoolean(args[6]);
                        System.out.println(args[6] + " " + saveOrUseActions);
                        pathToActions = args[7];
                    }
                }

                if (parameters == null || saveOrUseActions == null) {
                    System.out.println("Illegal parameter name. Choose <representation type> from {" +
                            String.join(", ", representationTypes) + "}; <n> from {1, 2, 3, ...}; " +
                            "<if use context> from {true, false}; " +
                            "<if save or use saved edit scripts> from {true, false}.");
                    return;
                }

                represent(args[1], args[2], saveOrUseActions, pathToActions, parameters);
                break;
            default:
                System.out.println(args[0] + ": unknown command");
        }
    }

    private static Parameters validateParameters(String representationType, String n,
                                                 String useContext) {
        if (!representationTypes.contains(representationType)) {
            System.out.println("Unknown value for <representation type>.");
            return null;
        }
        if (!Utils.isNumeric(n)) {
            System.out.println("Wrong value for <n>.");
            return null;
        }
        if (!useContext.equals("true") && !useContext.equals("false")) {
            System.out.println("Wrong value for <if use context>.");
            return null;
        }

        Parameters.RepresentationType finalRepresentationType;

        switch (representationType) {
            case "concat":
                finalRepresentationType = Parameters.RepresentationType.CONCAT;
                break;
            case "short_as_ngram":
                finalRepresentationType = Parameters.RepresentationType.SHORT_AS_NGRAM;
                break;
            default:
                return null;
        }

        Integer finalN = Integer.parseInt(n);
        Boolean finalUseContext = Boolean.parseBoolean(useContext);

        return new Parameters(finalRepresentationType, finalN, finalUseContext);
    }

    private static void represent(String pathToDataset, String pathToSaveRepresentations, Boolean saveOrUseActions,
                                  String pathToSaveActions, Parameters parameters) throws IOException {
        Run.initGenerators();

        RepresentationsStore store = new RepresentationsStore(parameters);

        File datasetDir = new File(pathToDataset);
        File[] elements = Arrays.stream(datasetDir.listFiles())
                .filter(file -> Utils.isNumeric(file.getName()))
                .toArray(File[]::new);

        Arrays.sort(elements, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        for (File elementDir : elements) {
            if (saveOrUseActions &&
                    ASTActions.elementActionsFileExists(pathToSaveActions, elementDir.getName())) {
                List<String> actions = ASTActions.getSavedActions(elementDir.getName());
                store.addActions(elementDir.getName(), actions);
            } else {
                Path methodBeforePath = Paths.get(pathToDataset).resolve(elementDir.getName()).resolve("before.txt");
                Path methodAfterPath = Paths.get(pathToDataset).resolve(elementDir.getName()).resolve("after.txt");

                String methodBeforeContent = Utils.readContent(methodBeforePath.toString());
                String methodAfterContent = Utils.readContent(methodAfterPath.toString());

                List<Action> actions = ASTActions.buildMethodActions(methodBeforeContent, methodAfterContent);
                Pair<List<String>, List<String>> actionsStrings = store.convertToStrings(actions);

                if (saveOrUseActions) {
                    ASTActions.saveActions(pathToSaveActions, elementDir.getName(), actionsStrings);
                }

                store.addActions(elementDir.getName(), actionsStrings.getSecond());
            }
        }

        store.saveRepresentations(pathToSaveRepresentations, ASTActions.treeContext);
    }
}
