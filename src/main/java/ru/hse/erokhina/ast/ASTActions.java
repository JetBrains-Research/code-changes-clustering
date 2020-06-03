package ru.hse.erokhina.ast;

import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.gen.Generators;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import ru.hse.erokhina.utils.Pair;
import ru.hse.erokhina.utils.Utils;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ASTActions {
    public static TreeContext treeContext = null;
    private static String actionsFilename = "actions.txt";
    private static String rawActionsFilename = "raw_actions.txt";

    public static List<Action> buildMethodActions(String methodBeforeContent, String methodAfterContent)
            throws IOException {
        Pair<ITree, ITree> trees = buildTrees(methodBeforeContent, methodAfterContent);

        if (trees == null)
            return null;

        ITree src = trees.getFirst();
        ITree dst = trees.getSecond();

        Matcher matcher = Matchers.getInstance().getMatcher(src, dst);
        try {
            matcher.match();
        } catch (NullPointerException e) {
            System.out.println("Cannot match: NullPointerException in m.match()");
            return null;
        }

        ActionGenerator generator = new ActionGenerator(src, dst, matcher.getMappings());
        generator.generate();

        return generator.getActions();
    }

    private static Pair<ITree, ITree> buildTrees(String methodBeforeContent, String methodAfterContent) throws IOException {
        String wrappedMethodBeforeContent = Utils.wrapMethod(methodBeforeContent);
        String wrappedAfterBeforeContent = Utils.wrapMethod(methodAfterContent);

        File beforeMethodFile = new File(Paths.get(".tmp").resolve("Before.java").toString());
        if (!beforeMethodFile.exists()) {
            beforeMethodFile.getParentFile().mkdirs();
            beforeMethodFile.createNewFile();
        }
        try (PrintWriter out = new PrintWriter(beforeMethodFile.getAbsolutePath())) {
            out.print(wrappedMethodBeforeContent);
        }

        File afterMethodFile = new File(Paths.get(".tmp").resolve("After.java").toString());
        if (!afterMethodFile.exists()) {
            afterMethodFile.getParentFile().mkdirs();
            afterMethodFile.createNewFile();
        }
        try (PrintWriter out = new PrintWriter(afterMethodFile.getAbsolutePath())) {
            out.print(wrappedAfterBeforeContent);
        }

        ITree src = buildTree(beforeMethodFile);
        ITree dst = buildTree(afterMethodFile);

        if (src == null || dst == null) {
            return null;
        }

        return new Pair<>(src, dst);
    }

    private static ITree buildTree(File file) {
        ITree tree = null;
        TreeContext context = null;

        try {
            context = Generators.getInstance().getTree(file.getPath());
            tree = context.getRoot();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        if (treeContext == null) {
            treeContext = context;
        }

        return tree;
    }

    public static List<String> getSavedActions(String path) {
        File actionsFile = new File(path);

        if (!actionsFile.exists()) {
            return null;
        }

        String fileContent = Utils.readContent(actionsFile.getAbsolutePath());
        String[] actions = fileContent.split("\n");

        return new ArrayList<>(Arrays.asList(actions));
    }

    public static void saveActions(String pathToSaveActions, String name,
                                   Pair<List<String>, List<String>> actionsPair) throws IOException {
        List<String> rawActions = actionsPair.getFirst();
        List<String> actions = actionsPair.getSecond();

        File actionsFile = new File(Paths.get(pathToSaveActions).resolve(name).resolve(actionsFilename).toString());
        actionsFile.getParentFile().mkdirs();
        actionsFile.createNewFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(actionsFile.getAbsolutePath()));
        for (String action : actions) {
            writer.write(action + "\n");
        }
        writer.close();

        File rawActionsFile = new File(Paths.get(pathToSaveActions).resolve(name).resolve(rawActionsFilename).toString());
        rawActionsFile.getParentFile().mkdirs();
        rawActionsFile.createNewFile();
        BufferedWriter writerRaw = new BufferedWriter(new FileWriter(rawActionsFile.getAbsolutePath()));
        for (String action : rawActions) {
            writerRaw.write(action + "\n");
        }
        writerRaw.close();
    }

    public static boolean elementActionsFileExists(String pathToSave, String name) {
        File elementActionsFile = new File(Paths.get(pathToSave).resolve(name).resolve(actionsFilename).toString());
        return elementActionsFile.exists();
    }
}
