import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.Generators;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class Main {

    public static Map<String, Integer> ngrams_stats = new HashMap<>();
    public static List<String> all_patterns_list = new ArrayList<>();

    // For the initial research
    public static Map<String, ArrayList<String>> ngrams_locations_as_sub = new HashMap<>();
    public static Map<String, ArrayList<String>> ngrams_locations_full = new HashMap<>();

    //public static Map<String, Map<String, Integer>> from_pattern_to_hist = new HashMap<>();
    public static Map<String, Map<String, Integer>> from_sample_to_hist = new HashMap<>();
    public static Map<String, Map<String, Integer>> from_edit_script_to_hist = new HashMap<>();

    public static List<String> consideredPatterns = Arrays.asList("10 136", "11 719", "14 239", "18 800",
            "22 3921", "25 8331", "27 3622", "27 7869", "9 35");
    public static int consideredPatternsA = 3;
    public static int consideredPatternsB = 50;

    // only 5-grams
    public static List<String> consideredEditScripts = Arrays.asList("DEL 32@@ DEL 21@@ DEL 42@@ DEL 32@@ DEL 21@@",
            "INS 32@@ 32@@ at 0 MOV 42@@ 32@@ at 1 MOV 3@@ 32@@ at 2 MOV 3@@ 32@@ at 3 INS 32@@ 32@@ at 0",
            "INS 42@@ 32@@ at 0 INS 42@@ 32@@ at 1 INS 40@@ 32@@ at 2 INS 9@@ 32@@ at 3 MOV 21@@ 8@@ at 0",
            "INS 8@@ 25@@ at 1 INS 32@@ 25@@ at 0 INS 8@@ 25@@ at 1 INS 32@@ 25@@ at 0 INS 8@@ 25@@ at 1",
            "INS 32@@ 59@@ at 1 INS 42@@ 43@@ at 0 INS 42@@ 59@@ at 0 MOV 32@@ 59@@ at 1 INS 42@@ 32@@ at 0",
            "MOV 42@@ 32@@ at 0 UPD 42@@ MOV 42@@ 32@@ at 1 UPD 42@@ MOV 42@@ 32@@ at 2");

    public static int num_changes = 0;
    public static int NUM_ACTIONS_THRESHOLD = 200;

    public static int NGRAM = 4;
    private static String RESULTS_FOR_HIST_DIR = "/Volumes/Seagate/Alina/result_for_raw_tufano_with_UPD";
    private static boolean TREAT_SMALL_SCRIPTS_AS_NGRAM = false;
    private static boolean CALCULATE_HISTS = true;
    private static boolean USE_SAVED_ACTIONS = true;
    private static boolean USE_PARENTS = true;
    private static String ACTIONS_DIR_PATH =
            "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/RawTufanoActions24kWithUPD";

    public static void dfs(ITree node/*, List<ITree> visited*/) {
        List<ITree> children = node.getChildren();

        System.out.println(node.toShortString() + " " + node.getPos() + " " + node.getEndPos());

        for (ITree child : children) {
            System.out.println("Parent: " + node.toShortString() + " Child: " + child.toShortString());

            dfs(child);
        }
    }

    public static String readAllBytesJava7(String filePath)
    {
        String content = "";
        try
        {
            content = new String ( Files.readAllBytes( Paths.get(filePath) ) );
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return content;
    }

    /**
     * Finds a node in ITree which covers a fragment of code.
     * @param node a root of tree
     * @param start start position of code fragment
     * @param end end position of code fragment
     * @return min node which covers the fragment
     */
    // TODO: change loop to range-based; add new scopes to get less trees.
    public static ITree findMinCoverNode(ITree node, int start, int end) {
        //System.out.println("NEW CALL");
        //System.out.println(node.getHash());
        //System.out.println(node.hashCode());

        int node_start = node.getPos();
        int node_end = node.getEndPos();

        if (!(node_start <= start && end <= node_end)) {
            return null;
        }

        //List<ITree> children = node.getChildren();

        int num_children = node.getChildren().size();
        for (int i = 0; i < num_children; i++) {
            //System.out.println("INSIDE");
            //System.out.println(node.getChild(i).getHash());
            //System.out.println(node.getChild(i).hashCode());
            ITree res = findMinCoverNode(node.getChild(i), start, end);
            if (res != null) {
                return res;
            }
        }

        //Map<String, String> mp = new HashMap<>();
        //mp.get("dfgh");

        return node;
    }

    public static void mainExperiment(String[] args) throws IOException {
        Run.initGenerators();

        String fragmentBefore = readAllBytesJava7("fragment_before2.txt");//.replace("\t", "    ");
        String file1 = "file_before.java";
        String content1 = readAllBytesJava7(file1);
        ITree src = Generators.getInstance().getTree(file1).getRoot();
        int start_ind = content1.indexOf(fragmentBefore);
        if (start_ind == -1) {
            System.out.println("NOT FOUND");
            return;
        }
        int end_ind = start_ind + fragmentBefore.length();
        ITree beforeNode = findMinCoverNode(src, start_ind, end_ind);
        System.out.println(beforeNode.toTreeString());

        System.out.println("__________________________________________________________________");

        String fragmentAfter = readAllBytesJava7("fragment_after2.txt");//.replace("\t", "    ");
        String file2 = "file_after.java";
        String content2 = readAllBytesJava7(file2);
        ITree dst = Generators.getInstance().getTree(file2).getRoot();
        int start_ind2 = content2.indexOf(fragmentAfter);
        if (start_ind2 == -1) {
            System.out.println("NOT FOUND");
            return;
        }
        int end_ind2 = start_ind2 + fragmentAfter.length();
        ITree afterNode = findMinCoverNode(dst, start_ind2, end_ind2);
        System.out.println(afterNode.toTreeString());

        Matcher m = Matchers.getInstance().getMatcher(beforeNode, afterNode); // retrieve the default matcher
        m.match();
        ActionGenerator g = new ActionGenerator(beforeNode, afterNode, m.getMappings());
        g.generate();
        List<Action> actions = g.getActions();

        for (Action action : actions) {
            System.out.println(action.getNode().toShortString() + " | " +
                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.toString());
        }

        //dfs(src);

        //System.out.println(src.toTreeString());

        /*Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
        m.match();
        ActionGenerator g = new ActionGenerator(src, dst, m.getMappings());
        g.generate();
        List<Action> actions = g.getActions(); // return the actions
        */

        /*for (Action action : actions) {
            System.out.println(action.getNode().toShortString() + " | " +
                    action.getNode().getLabel() + " | " + action.toString());
        }*/
    }

    public static boolean isNumeric(String strNum) {
        try {
            double d = Double.parseDouble(strNum);
        } catch (NumberFormatException | NullPointerException nfe) {
            return false;
        }
        return true;
    }

    public static String getOnlyBlackChanges1(String fragment) {
        String[] lines = fragment.split("\n");
        int start = -1;
        int end = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            //System.out.println("LINE " + line);

            if (line.contains("<a id=\"change\">") || line.contains("</a>")) {
                if (start == -1) {
                    start = i;
                }
                end = i;
            }

            lines[i] = line.replace("<a id=\"change\">", "").replace("</a>", "");
        }

        //System.out.println("BOUNDS " + start + " " + end);

        String[] extractedLines = Arrays.copyOfRange(lines, start, end + 1);

        return String.join("\n", extractedLines);
    }

    public static String getOnlyBlackChanges2(String fragment) {
        int start = fragment.indexOf("<a id=\"change\">") + "<a id=\"change\">".length();
        int end = fragment.lastIndexOf("</a>");

        return fragment.substring(start, end)
                .replace("<a id=\"change\">", "")
                .replace("</a>", "");
    }

    static class Indices {
        int start;
        int end;

        Indices(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public static int numberOfOccurrences(String str, String substr) {
        int lastIndex = 0;
        int count = 0;

        while(lastIndex != -1){

            lastIndex = str.indexOf(substr, lastIndex);

            if(lastIndex != -1){
                count ++;
                lastIndex += substr.length();
            }
        }

        return count;
    }


    /**
     * Gets indices of substring of fragment which starts and ends with highlighted tokens.
     * @param file
     * @param fragment
     * @return
     */
    // TODO: embed this function to the main code.
    public static Indices getCorrectIndices(String file, String fragment, int fromIndex) {
        String startTag = "<a id=\"change\">";
        String endTag = "</a>";

        //System.out.println("\n\nFRAGMENT");
        //System.out.println(fragment);

        fragment = strip(fragment.trim(), '\n');

        String notHighlighted = fragment
                .replace(startTag, "")
                .replace(endTag, "");

        //System.out.println("|" + notHighlighted + "|");
        //System.out.println("From Index: " + fromIndex + " Fragment: " + file.substring(fromIndex - 50, fromIndex + 50));
        int startFragment = file.indexOf(notHighlighted, fromIndex);

        System.out.println("\n______________________NUMBER OF FRAGMENT OCCURENCES: " + numberOfOccurrences(file, notHighlighted) + "\n");

        if (startFragment == -1) {
            return null;
        }

        int startHighlighted = fragment.indexOf(startTag);
        int start = startFragment + startHighlighted;

        int endHighlighted = fragment.lastIndexOf(endTag) + endTag.length();
        int end = startFragment + endHighlighted - numberOfOccurrences(fragment, startTag) * startTag.length()
                - numberOfOccurrences(fragment, endTag) * endTag.length();

        //System.out.println(fragment.substring(startHighlighted, endHighlighted));
        //System.out.println(numberOfOccurrences(fragment, startTag));
        //System.out.println(numberOfOccurrences(fragment, endTag));

        //System.out.println(file.substring(start, end));

        return new Indices(start, end);
    }

    // Saving files from commits.
    private static String COMMIT_FILES_DIR = "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/commit_files";


    public static void mainTest(String[] args) throws IOException {
        String str = "42c94aaaa60d62fac57dbef4f5dc1008ab8013dd,tests/org.jboss.tools.docker.ui.bot.test/src/org/jboss/tools/docker/ui/bot/test/image/PushImageTest.java,PushImageTest,pushImage,#,52,jbosstools/jbosstools-integration-tests";
        System.out.println(str.split(",")[1]);

        File file_before = new File(str.split(",")[1]);
        //System.out.println(file_before.getParentFile().getAbsolutePath());
        file_before.getParentFile().mkdirs();
        file_before.createNewFile();
    }

    public static void mainSavingCommitFiles(String[] args) throws IOException, GitAPIException {
        Run.initGenerators();

        File patternsDir = new File(PATTERNS_PATH);
        File[] size_dirs = Arrays.stream(patternsDir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(size_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        //int num_changes = 0;
        boolean if_break = false;

        for (File size_dir : size_dirs) {
            if (if_break) {
                break;
            }

            /*if (size_dir.getName().equals("60")) {
                break;
            }*/

            File[] id_dirs = Arrays.stream(size_dir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
            Arrays.sort(id_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

            int num_processed = 0;

            for (File id_dir : id_dirs) {
                /*if (num_processed >= 150) {
                    break;
                }*/
                num_processed++;

                if (if_break) {
                    break;
                }

                File[] files = id_dir.listFiles();

                for (File file : files) {
                    if (if_break) {
                        break;
                    }

                    if (!file.getName().startsWith("sampleChange") || file.getName().equals("sampleChange.html")) {
                        continue;
                    }

                    System.out.println("\n\n\n" + size_dir.getName() + " " + id_dir.getName());
                    System.out.println("FILE: " + file.getName());

                    String content = readAllBytesJava7(file.getAbsolutePath());
                    int startInd = content.indexOf("<html><h3>") + "<html><h3>".length();
                    int endInd = content.indexOf("</h3><h3>");

                    String name = content.substring(startInd, endInd);
                    System.out.println(name);
                    String[] parts = name.split(",");
                    String repoName = parts[parts.length - 1].trim();
                    String commitName = parts[0];
                    String fileName = parts[1];

                    if (repoName.equals("apache/pig") && commitName.equals("19c9d91aa66d3b3d102dbf44437958f5b7957098"))
                        commitName = "deb4a30b610806bb1b99bccd454811e77eafab90";
                    if (repoName.equals("apache/pig") && commitName.equals("3adf82bbd8d6c324e5a09554f5064c316af071a3"))
                        commitName = "dc0fa7b44b8ac83d8df4a0dac9f48eab76851148";
                    if (repoName.equals("apache/pig") && commitName.equals("d1ea940063270199e57c5efde9fbf76912773c7a"))
                        commitName = "6dc4f57f4b8592c1804df08891036ce1515b1f9a";
                    if (repoName.equals("apache/pig") && commitName.equals("9e280062373d817221e2466ce1234741ad964f44"))
                        commitName = "e9ff9c27c10c61362e8460b35af4b88b21ef8f15";


                    File projectDir = new File(REPOS_PATH + "/" + repoName);
                    if (!projectDir.exists()) {
                        continue;
                    }

                    GitConnector gc = new GitConnector(REPOS_PATH + "/" + repoName + "/.git");
                    System.out.println(REPOS_PATH + "/" + repoName + "/.git");
                    ArrayList<String> afterAndBefore = null;

                    File fileBeforeSaved = new File(COMMIT_FILES_DIR + "/" +
                            repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_before.java");
                    File fileAfterSaved = new File(COMMIT_FILES_DIR + "/" +
                            repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_after.java");

                    //System.out.println(fileBeforeSaved.getAbsolutePath());

                    if (fileBeforeSaved.exists() && fileAfterSaved.exists()) {
                        System.out.println("BEFORE AND AFTER FILES EXIST!");
                        continue;
                    }

                    if (gc.connect()) {
                        System.out.println("Connecting...");
                        afterAndBefore = gc.getFileFromCommit(commitName, fileName);
                        gc.close();
                    } else {
                        System.out.println("GitConnector is not connected");
                    }

                    // TODO: deal with replacement!!!!!!!
                    String afterContent = afterAndBefore.get(0);//.replace("\t", "    ");
                    String beforeContent = afterAndBefore.get(1);//.replace("\t", "    ");

                    File file_before = new File(fileBeforeSaved.getAbsolutePath());
                    file_before.getParentFile().mkdirs();
                    file_before.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
                        out.print(beforeContent);
                    }

                    File file_after = new File(fileAfterSaved.getAbsolutePath());
                    file_after.getParentFile().mkdirs();
                    file_after.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_after.getAbsolutePath())) {
                        out.print(afterContent);
                    }
                }
            }
        }
    }

    public static TreeContext getTreeContext() throws IOException, GitAPIException {
        File file = new File(PATTERNS_PATH + "/3/1/sampleChange1.html");

        String content = readAllBytesJava7(file.getAbsolutePath());
        int startInd = content.indexOf("<html><h3>") + "<html><h3>".length();
        int endInd = content.indexOf("</h3><h3>");

        String name = content.substring(startInd, endInd);
        String[] parts = name.split(",");
        String repoName = parts[parts.length - 1].trim();
        String commitName = parts[0];
        String fileName = parts[1];

        GitConnector gc = new GitConnector(REPOS_PATH + "/" + repoName + "/.git");
        System.out.println(REPOS_PATH + "/" + repoName + "/.git");
        ArrayList<String> afterAndBefore = null;

        File fileBeforeSaved = new File(COMMIT_FILES_DIR + "/" +
                repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_before.java");
        File fileAfterSaved = new File(COMMIT_FILES_DIR + "/" +
                repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_after.java");

        if (!fileBeforeSaved.exists() || !fileAfterSaved.exists()) {
            if (gc.connect()) {
                afterAndBefore = gc.getFileFromCommit(commitName, fileName);
                gc.close();
            } else {
                System.out.println("GitConnector is not connected");
            }
        } else {
            System.out.println("Files were saved.");

            afterAndBefore = new ArrayList<>();
            afterAndBefore.add(readAllBytesJava7(fileAfterSaved.getAbsolutePath()));
            afterAndBefore.add(readAllBytesJava7(fileBeforeSaved.getAbsolutePath()));
        }

        String beforeContent = afterAndBefore.get(1).replace("\t", "    ")
                .replace("&gt;", ">")
                .replace("&lt;", "<");

        File file_before = new File("tmp/file_for_tree_context.java");
        file_before.createNewFile();
        try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
            out.print(beforeContent);
        }
        TreeContext treeSrc = null;
        try {
            treeSrc = Generators.getInstance().getTree(file_before.getPath());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return treeSrc;
    }

    public static void mainToShowTrees(String[] args) throws IOException, GitAPIException {
        //showTreesForPattern("5", "2013", "sampleChange16.html");
        //showTreesForPattern("8", "2266", "sampleChange63.html");
        //showTreesForPattern("8", "2266", "sampleChange40.html");
        showTreesForPattern("8", "2266", "sampleChange12.html");
        //showTreesForPattern("4", "13964", "sampleChange12.html");
    }

    public static void showTreesForPattern(String size_dir, String id_dir, String filename) throws IOException, GitAPIException {
        Run.initGenerators();

        TreeContext treeContext = null;

        File file = new File(PATTERNS_PATH + "/" + size_dir + "/" + id_dir + "/" + filename);

        System.out.println("\n\n\n" + size_dir + " " + id_dir);
        System.out.println("FILE: " + file.getName());

        String content = readAllBytesJava7(file.getAbsolutePath());
        int startInd = content.indexOf("<html><h3>") + "<html><h3>".length();
        int endInd = content.indexOf("</h3><h3>");

        String name = content.substring(startInd, endInd);
        String[] parts = name.split(",");
        String repoName = parts[parts.length - 1].trim();
        String commitName = parts[0];
        String fileName = parts[1];

        GitConnector gc = new GitConnector(REPOS_PATH + "/" + repoName + "/.git");
        System.out.println(REPOS_PATH + "/" + repoName + "/.git");
        ArrayList<String> afterAndBefore = null;


        File fileBeforeSaved = new File(COMMIT_FILES_DIR + "/" +
                repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_before.java");
        File fileAfterSaved = new File(COMMIT_FILES_DIR + "/" +
                repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_after.java");


        if (!fileBeforeSaved.exists() || !fileAfterSaved.exists()) {
            if (gc.connect()) {
                afterAndBefore = gc.getFileFromCommit(commitName, fileName);
                gc.close();
            } else {
                System.out.println("GitConnector is not connected");
            }
        } else {
            System.out.println("Files were saved.");

            afterAndBefore = new ArrayList<>();
            afterAndBefore.add(readAllBytesJava7(fileAfterSaved.getAbsolutePath()));
            afterAndBefore.add(readAllBytesJava7(fileBeforeSaved.getAbsolutePath()));
        }

        String afterContent = afterAndBefore.get(0).replace("\t", "    ")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
        String beforeContent = afterAndBefore.get(1).replace("\t", "    ")
                .replace("&gt;", ">")
                .replace("&lt;", "<");

        File file_before = new File("tmp/file_before.java");
        file_before.createNewFile();
        try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
            out.print(beforeContent);
        }
        ITree src = null;
        TreeContext treeSrc = null;
        try {
            treeSrc = Generators.getInstance().getTree(file_before.getPath());
            src = treeSrc.getRoot();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        treeContext = treeSrc;

        for (int i = 0; i < 100; i++) {
            if (treeContext.getTypeLabel(i) != null) {
                System.out.println(i + " " + treeContext.getTypeLabel(i));
            }
        }
        System.out.println("\n\n\n");

        File file_after = new File("tmp/file_after.java");
        file_after.createNewFile();
        try (PrintWriter out = new PrintWriter(file_after.getAbsolutePath())) {
            out.print(afterContent);
        }
        ITree dst = null;
        TreeContext treeDst = null;
        try {
            treeDst = Generators.getInstance().getTree(file_after.getPath());
            dst = treeDst.getRoot();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        ArrayList<String> beforeAndAfterFragments = null;
        beforeAndAfterFragments = getFragments(content);

        Indices indicesBefore = getCorrectIndices(beforeContent, beforeAndAfterFragments.get(0), 0);
        Indices indicesAfter = getCorrectIndices(afterContent, beforeAndAfterFragments.get(1), 0);

        if (indicesBefore == null) {
            System.out.println("Before fragment wasn't found in file.");
            System.out.println("Before fragment:");
            System.out.println(beforeAndAfterFragments.get(0));

            FileWriter fileWriter = new FileWriter("NotFoundFragments.txt", true);
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.print(size_dir + " " + id_dir + " " + file.getName() + " " + "before" + "\n");
            printWriter.close();
            return;
        }
        if (indicesAfter == null) {
            System.out.println("After fragment wasn't found in file.");
            System.out.println("After fragment:");
            System.out.println(beforeAndAfterFragments.get(1));
            FileWriter fileWriter = new FileWriter("NotFoundFragments.txt", true);
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.print(size_dir + " " + id_dir + " " + file.getName() + " " + "after" + "\n");
            printWriter.close();
            return;
        }


        ITree beforeNode = findMinCoverNode(src, indicesBefore.start, indicesBefore.end);
        if (beforeNode == null) {
            System.out.println("beforeNode is NULL " + file.getName());
            return;
        }

        ITree afterNode = findMinCoverNode(dst, indicesAfter.start, indicesAfter.end);
        if (afterNode == null) {
            System.out.println("afterNode is NULL " + file.getName());
            return;
        }

        System.out.println();

        try (PrintWriter out = new PrintWriter("tmp/before_node" + id_dir + "_" + filename)) {
            out.print(beforeNode.toTreeString());
        }

        try (PrintWriter out = new PrintWriter("tmp/after_node" + id_dir + "_" + filename)) {
            out.print(afterNode.toTreeString());
        }

        try (PrintWriter out = new PrintWriter("tmp/src_tree" + id_dir + "_" + filename)) {
            out.print(src.toTreeString());
        }
        try (PrintWriter out = new PrintWriter("tmp/dst_tree" + id_dir + "_" + filename)) {
            out.print(dst.toTreeString());
        }

        try (PrintWriter out = new PrintWriter("tmp/src_tree_preorder" + id_dir + "_" + filename)) {
            List<ITree> srcList = new ArrayList<>();
            src.preOrder().forEach(srcList::add);
            for (ITree iTree : srcList) {
                out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                        iTree.toShortString() + "       -> " + iTree.getId());
            }
        }
        try (PrintWriter out = new PrintWriter("tmp/dst_tree_preorder" + id_dir + "_" + filename)) {
            List<ITree> dstList = new ArrayList<>();
            dst.preOrder().forEach(dstList::add);
            for (ITree iTree : dstList) {
                out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                        iTree.toShortString() + "       -> " + iTree.getId());
            }
        }


        // Match
        Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
        try {
            m.match();
        } catch (NullPointerException e) {
            System.out.println("Cannot match: NullPointerException in m.match()");
        }


        System.out.println("\nMAPPINGS:");
        for (Mapping mapping : m.getMappingsAsSet()) {
            System.out.println(mapping.first.getId() + " -> " + mapping.second.getId());
        }
        System.out.println("\n");


        ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
        allFileGen.generate();

        BeforeAfterNodes nodes = getBeforeAfterNodes(m,
                indicesBefore.start, indicesBefore.end, indicesAfter.start, indicesAfter.end,
                beforeContent, afterContent);





        Matcher localM = Matchers.getInstance().getMatcher(nodes.before.getParent(), nodes.after.getParent()); // retrieve the default matcher
        try {
            localM.match();
        } catch (NullPointerException e) {
            System.out.println("Cannot match: NullPointerException in localM.match()");
        }
        ActionGenerator localGen = new ActionGenerator(nodes.before.getParent(), nodes.after.getParent(), localM.getMappings());
        localGen.generate();
        List<Action> localActions = localGen.getActions();
        System.out.println("\nACTIONS OLD WAY:");
        System.out.println(localActions.size());
        try {
            for (Action action : localActions) {
                System.out.println(action.toString() + "       -> " + action.getNode().getId());
            }

        } catch (NullPointerException e) {
            for (Action action : localActions) {
                System.out.println(action.getName() + " " + action.getNode().toShortString() + "       -> " + action.getNode().getId());
            }
        }
        System.out.println("\n");





        ITree nodeBeforeRaw = nodes.rawBefore;
        ITree nodeAfterRaw = nodes.rawAfter;

        System.out.println("NODE BEFORE:");
        System.out.println(nodeBeforeRaw.toTreeString());
        System.out.println("___________________\n");
        System.out.println("NODE AFTER:");
        System.out.println(nodeAfterRaw.toTreeString());
        System.out.println("___________________\n");

        List<Action> allActions = allFileGen.getActions();
        try (PrintWriter out = new PrintWriter("tmp/all_actions" + id_dir + "_" + filename)) {
            for (int i = 0; i < allActions.size(); i++) {
                out.println(allActions.get(i).toString() + "       -> " + allActions.get(i).getNode().getId());
            }
        }
        List<Action> extractedActionsRaw = extractActionsWithOrdering(allActions, nodeBeforeRaw, nodeAfterRaw);

        System.out.println("______________________________________");
        System.out.println("ACTIONS SIZE");
        System.out.println(extractedActionsRaw.size());

        try {
            System.out.println("\nActions retrieved in the third (raw) way:");
            for (Action action : extractedActionsRaw) {
                System.out.println(action.toString() + "       -> " + action.getNode().getId());
            }

        } catch (NullPointerException e) {
            System.out.println("\nActions retrieved in the third (raw) way:");
            for (Action action : extractedActionsRaw) {
                System.out.println(action.toString() + "       -> " + action.getNode().getId());
            }
        }
    }

    //private static String RESULTS_FOR_HIST_DIR = "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/result_for_hist_all_data3-60";
    //private static String RESULTS_FOR_HIST_DIR = "/Volumes/Seagate/Alina/result_for_hist_all_data3-50-1gram";
    //private static boolean USE_SAVED_ACTIONS = true;

    // TODO: delete "if (num_processed >= 30)"

    /*
     Last main which can generate edit scripts, save them and use them to calculate hists. It generates edit scripts as
     subsequence of edit script of all file. However, it leads to incorrect mappings. For example, a node of one method
     can be mapped to a node of another method. So next try is to extract each method ("before" and "after") to
     separate file and run gumtree algorithm on these two files.
    */
    public static void mainMAIN(String[] args) throws IOException, GitAPIException {
        Run.initGenerators();

        TreeContext treeContext = null;

        if (USE_SAVED_ACTIONS) {
            treeContext = getTreeContext();
        }

        File patternsDir = new File(PATTERNS_PATH);
        File[] size_dirs = Arrays.stream(patternsDir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(size_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        boolean if_break = false;

        for (File size_dir : size_dirs) {
            if (if_break) {
                break;
            }

            // TODO: UNCOMMENT WHILE CALCULATING HISTS
            if (!(consideredPatternsA <= Integer.parseInt(size_dir.getName()) &&
                    Integer.parseInt(size_dir.getName()) <= consideredPatternsB)) {
                continue;
            }


            File[] id_dirs = Arrays.stream(size_dir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
            Arrays.sort(id_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

            int num_processed = 0;

            for (File id_dir : id_dirs) {
                // Too long calculation
                if (id_dir.getName().equals("6520")) {
                    continue;
                }
                if (id_dir.getName().equals("10518")) {
                    continue;
                }
                if (id_dir.getName().equals("12342")) {
                    continue;
                }

                /*if (num_processed >= 30) {
                    break;
                }*/
                num_processed++;


                if (if_break) {
                    break;
                }

                File[] files = id_dir.listFiles();

                for (File file : files) {
                    if (if_break) {
                        break;
                    }

                    if (!file.getName().startsWith("sampleChange") || file.getName().equals("sampleChange.html")) {
                        continue;
                    }

                    System.out.println("\n\n\n" + size_dir.getName() + " " + id_dir.getName());
                    System.out.println("FILE: " + file.getName());

                    //
                    //
                    //
                    //
                    // TODO: DELETE WHILE CALCULATING HISTS !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                    /*String filename = file.getName();
                    File curActionFile = new File(ACTIONS_DIR_PATH + "/" +
                            size_dir.getName() + "/" + id_dir.getName() + "/" + filename.substring(0, filename.length() - 5));
                    if (curActionFile.exists()) {
                        System.out.println("EDIT SCRIPT EXISTS!");
                        continue;
                    }*/
                    //
                    //
                    //
                    //
                    //

                    if (USE_SAVED_ACTIONS) {
                        // TODO: make correct calculation of num_changes.
                        num_changes++;
                        addNgramsToSet(null, size_dir.getName(), id_dir.getName(), file.getName(), true);
                        continue;
                    }

                    String content = readAllBytesJava7(file.getAbsolutePath());
                    int startInd = content.indexOf("<html><h3>") + "<html><h3>".length();
                    int endInd = content.indexOf("</h3><h3>");

                    String name = content.substring(startInd, endInd);
                    String[] parts = name.split(",");
                    String repoName = parts[parts.length - 1].trim();
                    String commitName = parts[0];
                    String fileName = parts[1];

                    if (repoName.equals("apache/pig") && commitName.equals("19c9d91aa66d3b3d102dbf44437958f5b7957098"))
                        commitName = "deb4a30b610806bb1b99bccd454811e77eafab90";
                    if (repoName.equals("apache/pig") && commitName.equals("3adf82bbd8d6c324e5a09554f5064c316af071a3"))
                        commitName = "dc0fa7b44b8ac83d8df4a0dac9f48eab76851148";
                    if (repoName.equals("apache/pig") && commitName.equals("d1ea940063270199e57c5efde9fbf76912773c7a"))
                        commitName = "6dc4f57f4b8592c1804df08891036ce1515b1f9a";
                    if (repoName.equals("apache/pig") && commitName.equals("9e280062373d817221e2466ce1234741ad964f44"))
                        commitName = "e9ff9c27c10c61362e8460b35af4b88b21ef8f15";

                    GitConnector gc = new GitConnector(REPOS_PATH + "/" + repoName + "/.git");
                    System.out.println(REPOS_PATH + "/" + repoName + "/.git");
                    ArrayList<String> afterAndBefore = null;

                    File fileBeforeSaved = new File(COMMIT_FILES_DIR + "/" +
                            repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_before.java");
                    File fileAfterSaved = new File(COMMIT_FILES_DIR + "/" +
                            repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_after.java");

                    if (!fileBeforeSaved.exists() || !fileAfterSaved.exists()) {
                        if (gc.connect()) {
                            afterAndBefore = gc.getFileFromCommit(commitName, fileName);
                            gc.close();
                        } else {
                            System.out.println("GitConnector is not connected");
                        }
                    } else {
                        System.out.println("Files were saved.");

                        afterAndBefore = new ArrayList<>();
                        afterAndBefore.add(readAllBytesJava7(fileAfterSaved.getAbsolutePath()));
                        afterAndBefore.add(readAllBytesJava7(fileBeforeSaved.getAbsolutePath()));
                    }

                    String afterContent = afterAndBefore.get(0).replace("\t", "    ")
                            .replace("&gt;", ">")
                            .replace("&lt;", "<");
                    String beforeContent = afterAndBefore.get(1).replace("\t", "    ")
                            .replace("&gt;", ">")
                            .replace("&lt;", "<");

                    File file_before = new File("tmp/file_before.java");
                    file_before.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
                        out.print(beforeContent);
                    }
                    ITree src = null;
                    TreeContext treeSrc = null;
                    try {
                        treeSrc = Generators.getInstance().getTree(file_before.getPath());
                        src = treeSrc.getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (treeContext == null) {
                        treeContext = treeSrc;
                    }

                    File file_after = new File("tmp/file_after.java");
                    file_after.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_after.getAbsolutePath())) {
                        out.print(afterContent);
                    }
                    ITree dst = null;
                    TreeContext treeDst = null;
                    try {
                        treeDst = Generators.getInstance().getTree(file_after.getPath());
                        dst = treeDst.getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    ArrayList<String> beforeAndAfterFragments = null;
                    beforeAndAfterFragments = getFragments(content);

                    Indices indicesBefore = getCorrectIndices(beforeContent, beforeAndAfterFragments.get(0), 0);
                    Indices indicesAfter = getCorrectIndices(afterContent, beforeAndAfterFragments.get(1), 0);

                    if (indicesBefore == null) {
                        System.out.println("Before fragment wasn't found in file.");
                        System.out.println("Before fragment:");
                        System.out.println(beforeAndAfterFragments.get(0));
                        //if_break = true;
                        //break;
                        FileWriter fileWriter = new FileWriter("NotFoundFragments.txt", true);
                        PrintWriter printWriter = new PrintWriter(fileWriter);
                        printWriter.print(size_dir.getName() + " " + id_dir.getName() + " " + file.getName() + " " + "before" + "\n");
                        printWriter.close();
                        continue;
                    }
                    if (indicesAfter == null) {
                        System.out.println("After fragment wasn't found in file.");
                        System.out.println("After fragment:");
                        System.out.println(beforeAndAfterFragments.get(1));
                        FileWriter fileWriter = new FileWriter("NotFoundFragments.txt", true);
                        PrintWriter printWriter = new PrintWriter(fileWriter);
                        printWriter.print(size_dir.getName() + " " + id_dir.getName() + " " + file.getName() + " " + "after" + "\n");
                        printWriter.close();
                        continue;
                        //if_break = true;
                        //break;
                    }

                    //System.out.println(beforeContent.substring(indicesBefore.start, indicesBefore.end));

                    ITree beforeNode = findMinCoverNode(src, indicesBefore.start, indicesBefore.end);
                    if (beforeNode == null) {
                        System.out.println("beforeNode is NULL " + file.getName());
                        //if_break = true;
                        //break;
                        continue;
                    }

                    ITree afterNode = findMinCoverNode(dst, indicesAfter.start, indicesAfter.end);
                    if (afterNode == null) {
                        System.out.println("afterNode is NULL " + file.getName());
                        //if_break = true;
                        //break;
                        continue;
                    }

                    try (PrintWriter out = new PrintWriter("tmp/before_node")) {
                        out.print(beforeNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/after_node")) {
                        out.print(afterNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/src_tree")) {
                        out.print(src.toTreeString());
                    }
                    try (PrintWriter out = new PrintWriter("tmp/dst_tree")) {
                        out.print(dst.toTreeString());
                    }

                    //System.out.println(treeContext.getTypeLabel(32));

                    //System.out.println("BEFORE DEPTH: " + beforeNode.getDepth());
                    //System.out.println("AFTER DEPTH: " + afterNode.getDepth());

                    // Match
                    Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
                    try {
                        m.match();
                    } catch (NullPointerException e) {
                        System.out.println("Cannot match: NullPointerException in m.match()");

                        //System.out.println(beforeNode.toTreeString());
                        //System.out.println("BEFORE DEPTH: " + beforeNode.getDepth());
                        //System.out.println("_________");
                        //System.out.println(afterNode.toTreeString());
                        //System.out.println("AFTER DEPTH: " + afterNode.getDepth());

                        if_break = true;
                        break;
                    }

                    ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
                    allFileGen.generate();

                    /*if (id_dir.getName().equals("10") && file.getName().startsWith("sampleChange6")) {
                        System.out.println("CHECKING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        checkingAllTree(src, m, beforeContent, afterContent);
                    }*/

                    //System.out.println(treeSrc.getTypeLabel(42));

                    BeforeAfterNodes nodes = getBeforeAfterNodes(m,
                            indicesBefore.start, indicesBefore.end, indicesAfter.start, indicesAfter.end,
                            beforeContent, afterContent);

                    ITree nodeBeforeRaw = nodes.rawBefore;
                    ITree nodeAfterRaw = nodes.rawAfter;

                    //Matcher fragmentsMatcher = Matchers.getInstance().getMatcher(nodeBefore, nodeAfter);
                    //fragmentsMatcher.match();

                    /*System.out.println(nodeBefore.toTreeString());
                    System.out.println("________________________");
                    System.out.println(nodeAfter.toTreeString());
                    System.out.println("\n_________________________\n");*/

                    //ActionGenerator g = new ActionGenerator(nodeBefore, nodeAfter, m.getMappings());
                    //ActionGenerator g = new ActionGenerator(nodeBefore, nodeAfter, fragmentsMatcher.getMappings());
                    //g.generate();
                    //List<Action> actions = g.getActions();

                    //TreeContext treeContext = new TreeContext();
                    //System.out.println(treeContext.getTypeLabel(42));

                    /*if (actions.size() > NUM_ACTIONS_THRESHOLD) {
                        if_break = true;
                        break;
                        //continue;
                    }*/

                    //ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
                    //allFileGen.generate();
                    List<Action> allActions = allFileGen.getActions();
                    //List<Action> extractedActions = extractActions(allActions, nodeBefore, nodeAfter);
                    List<Action> extractedActionsRaw = extractActions(allActions, nodeBeforeRaw, nodeAfterRaw);

                    System.out.println("______________________________________");
                    System.out.println("ACTIONS SIZE");
                    System.out.println(extractedActionsRaw.size());

                    try {
                        System.out.println("\nActions retrieved in the third (raw) way:");
                        for (Action action : extractedActionsRaw) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.toString());
                            //System.out.println(action.format(treeSrc));
                            //System.out.println(action.getNode().toTreeString());
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }

                    } catch (NullPointerException e) {
                        System.out.println("\nActions retrieved in the third (raw) way:");
                        for (Action action : extractedActionsRaw) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.toString());
                            //System.out.println(action.format(treeSrc));
                            //System.out.println(action.getNode().toTreeString());
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }

                        if_break = true;
                        break;
                        //continue;
                    }

                    num_changes++;
                    addNgramsToSet(extractedActionsRaw, size_dir.getName(), id_dir.getName(), file.getName(), false);
                }
            }

        }

        System.out.println("\n\n\nNUM CHANGES: " + num_changes);

        saveHists(treeContext);
    }


    // TUFANO DATASET
    private static String PATH_TO_WITH_LABELS = "/Users/aliscafo/Downloads/methods_with_label2.csv";
    private static String PATH_TO_ANDROID = "/Users/aliscafo/Downloads/android";
    private static String PATH_TO_GOOGLE = "/Users/aliscafo/Downloads/google";
    private static String PATH_TO_OVIRT = "/Users/aliscafo/Downloads/ovirt";

    private static Map<String, Pair<String, String>> from_abstract_to_real = new HashMap<>();
    private static Map<String, String> from_abstract_to_label = new HashMap<>();

    private static String PATH_TO_TUFANO_DATASET = "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/TufanoDataset";

    private static void putToMapOfRealMethods(String path) {
        String content = readAllBytesJava7(path);
        String[] pairs = content.split("====\n");
        for (int i = 0; i < pairs.length; i++) {
            //System.out.println(i);

            //System.out.println("|" + pairs[i] + "|");

            if (pairs[i].length() == 0)
                continue;

            String[] parts = pairs[i].split("----");
            String[] abstracts = parts[0].split("\n");
            String key = abstracts[0].trim() + " <SEP> " + abstracts[1].trim();
            from_abstract_to_real.put(key, new Pair<>(parts[1], parts[2]));

            /*
            System.out.println(path);

            System.out.println(pairs[i]);

            System.out.println("\nSPLITTED\n");
            System.out.println(parts[0]);
            System.out.println("\nNEXT\n");
            System.out.println(parts[1]);
            System.out.println("\nNEXT\n");
            System.out.println(parts[2]);

            System.out.println("\n\nSEPARATOR\n\n");
            */
        }
    }

    /*
     For Learning-CodeChanges dataset.
     */
    public static void mainLabeledTufano(String[] args) throws IOException {
        Run.initGenerators();

        TreeContext treeContext = null;

        putToMapOfRealMethods(PATH_TO_ANDROID + "/small/android-50-test.txt");
        putToMapOfRealMethods(PATH_TO_ANDROID + "/medium/android-50-100-test.txt");
        putToMapOfRealMethods(PATH_TO_GOOGLE + "/small/google-50-test.txt");
        putToMapOfRealMethods(PATH_TO_GOOGLE + "/medium/google-50-100-test.txt");
        putToMapOfRealMethods(PATH_TO_OVIRT + "/ovirt-50-test.txt");
        putToMapOfRealMethods(PATH_TO_OVIRT + "/medium/ovirt-50-100-test.txt");

        String beginningFile = "class Example {\n" +
                "\n";
        String endingFile = "\n" +
                "}";


        File tufano_dataset = new File(PATH_TO_TUFANO_DATASET);
        if (!tufano_dataset.exists())
            tufano_dataset.mkdir();

        Scanner scanner = new Scanner(new File(PATH_TO_WITH_LABELS));
        int numLine = 1;
        while (scanner.hasNext()) {
            System.out.println("Elem " + numLine);

            List<String> line = CSVUtils.parseLine(scanner.nextLine());
            String key = strip(line.get(0).trim(), '"') + " <SEP> " + strip(line.get(1).trim(), '"');
            String label = strip(line.get(2), '"');
            from_abstract_to_label.put(key, label);

            if (!from_abstract_to_real.containsKey(key)) {
                System.out.println("DOES NOT CONTAIN:");
                System.out.println(key);
            }

            Pair<String, String> realMethods = from_abstract_to_real.get(key);

            if (USE_SAVED_ACTIONS && treeContext != null) {
                addNgramsToSet(null, Integer.toString(numLine), Integer.toString(numLine),
                        "sampleChange1.java", true);
            } else {

                File elemDir = new File(PATH_TO_TUFANO_DATASET + "/" + numLine);
                if (!elemDir.exists()) {
                    elemDir.mkdir();
                }

                File beforeMethod = new File(PATH_TO_TUFANO_DATASET + "/" + numLine + "/before.java");
                if (!beforeMethod.exists()) {
                    try (PrintWriter out = new PrintWriter(beforeMethod.getAbsolutePath())) {
                        out.print(beginningFile);
                        out.print(realMethods.getFirst());
                        out.print(endingFile);
                    }
                }

                File afterMethod = new File(PATH_TO_TUFANO_DATASET + "/" + numLine + "/after.java");
                if (!afterMethod.exists()) {
                    try (PrintWriter out = new PrintWriter(afterMethod.getAbsolutePath())) {
                        out.print(beginningFile);
                        out.print(realMethods.getSecond());
                        out.print(endingFile);
                    }
                }


                File labelFile = new File(PATH_TO_TUFANO_DATASET + "/" + numLine + "/label.txt");
                if (!labelFile.exists()) {
                    try (PrintWriter out = new PrintWriter(labelFile.getAbsolutePath())) {
                        out.print(label);
                    }
                }

                ITree src = null;
                TreeContext treeSrc = null;
                try {
                    treeSrc = Generators.getInstance().getTree(beforeMethod.getPath());
                    src = treeSrc.getRoot();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                if (treeContext == null) {
                    treeContext = treeSrc;
                }

                ITree dst = null;
                TreeContext treeDst = null;
                try {
                    treeDst = Generators.getInstance().getTree(afterMethod.getPath());
                    dst = treeDst.getRoot();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                // Match
                Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
                try {
                    m.match();
                } catch (NullPointerException e) {
                    System.out.println("Cannot match: NullPointerException in m.match()");
                    break;
                }


                ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
                allFileGen.generate();
                List<Action> allActions = allFileGen.getActions();

                addNgramsToSet(allActions, Integer.toString(numLine), Integer.toString(numLine),
                        "sampleChange1.java", false);
            }

            numLine++;
        }
        scanner.close();

        if (CALCULATE_HISTS) {
            saveHists(treeContext);
        }
    }


    private static String PATH_TO_FULL_TUFANO_DATASET =
            "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/TufanoDatasetFull";
    private static int GLOBAL_ELEMENT_ID = 0;

    public static TreeContext processAndAddToFullDataset(String path_to_methods) throws IOException {
        TreeContext treeContext = null;

        System.out.println(path_to_methods);

        String content = readAllBytesJava7(path_to_methods);
        String[] pairs = content.split("====\n");
        for (int i = 0; i < pairs.length; i++) {
            if (pairs[i].length() == 0)
                continue;

            GLOBAL_ELEMENT_ID++;

            if (GLOBAL_ELEMENT_ID % 400 == 0) {
                System.out.println(GLOBAL_ELEMENT_ID);
            }

            String[] parts = pairs[i].split("----");

            /*
            System.out.println(parts[1]);
            System.out.println(parts[2]);
            System.out.println("__________\n\n");
            */

            String methodBefore = parts[1];
            String methodAfter = parts[2];

            if (USE_SAVED_ACTIONS && treeContext != null) {
                addNgramsToSet(null, Integer.toString(GLOBAL_ELEMENT_ID), Integer.toString(GLOBAL_ELEMENT_ID),
                        "sampleChange1.java", true);
                continue;
            } else {

                String beginningFile = "class Example {\n" +
                        "\n";
                String endingFile = "\n" +
                        "}";


                File tufano_dataset = new File(PATH_TO_FULL_TUFANO_DATASET);
                if (!tufano_dataset.exists())
                    tufano_dataset.mkdir();


                File elemDir = new File(PATH_TO_FULL_TUFANO_DATASET + "/" + GLOBAL_ELEMENT_ID);
                if (!elemDir.exists()) {
                    elemDir.mkdir();
                }

                File beforeMethod = new File(PATH_TO_FULL_TUFANO_DATASET + "/" + GLOBAL_ELEMENT_ID + "/before.java");
                if (!beforeMethod.exists()) {
                    try (PrintWriter out = new PrintWriter(beforeMethod.getAbsolutePath())) {
                        out.print(beginningFile);
                        out.print(methodBefore);
                        out.print(endingFile);
                    }
                }

                File afterMethod = new File(PATH_TO_FULL_TUFANO_DATASET + "/" + GLOBAL_ELEMENT_ID + "/after.java");
                if (!afterMethod.exists()) {
                    try (PrintWriter out = new PrintWriter(afterMethod.getAbsolutePath())) {
                        out.print(beginningFile);
                        out.print(methodAfter);
                        out.print(endingFile);
                    }
                }

                File origin = new File(PATH_TO_FULL_TUFANO_DATASET + "/" + GLOBAL_ELEMENT_ID + "/origin.java");
                if (!origin.exists()) {
                    try (PrintWriter out = new PrintWriter(origin.getAbsolutePath())) {
                        out.print(path_to_methods);
                    }
                }

                ITree src = null;
                TreeContext treeSrc = null;
                try {
                    treeSrc = Generators.getInstance().getTree(beforeMethod.getPath());
                    src = treeSrc.getRoot();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                if (treeContext == null) {
                    treeContext = treeSrc;
                }

                ITree dst = null;
                TreeContext treeDst = null;
                try {
                    treeDst = Generators.getInstance().getTree(afterMethod.getPath());
                    dst = treeDst.getRoot();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                // Match
                Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
                try {
                    m.match();
                } catch (NullPointerException e) {
                    System.out.println("Cannot match: NullPointerException in m.match()");
                    break;
                }


                ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
                allFileGen.generate();
                List<Action> allActions = allFileGen.getActions();

                /*
                for (Action action : allActions) {
                    System.out.println(action.toString());
                }
                */

                addNgramsToSet(allActions, Integer.toString(GLOBAL_ELEMENT_ID), Integer.toString(GLOBAL_ELEMENT_ID),
                        "sampleChange1.java", true);
            }
        }

        return treeContext;
    }


    /*
     For full Tufano dataset.
     */
    public static void mainFullTufano(String[] args) throws IOException {
        Run.initGenerators();

        TreeContext treeContext = null;

        treeContext = processAndAddToFullDataset(PATH_TO_ANDROID + "/small/android-50-train.txt");
        processAndAddToFullDataset(PATH_TO_ANDROID + "/small/android-50-test.txt");
        processAndAddToFullDataset(PATH_TO_ANDROID + "/small/android-50-eval.txt");

        processAndAddToFullDataset(PATH_TO_ANDROID + "/medium/android-50-100-train.txt");
        processAndAddToFullDataset(PATH_TO_ANDROID + "/medium/android-50-100-test.txt");
        processAndAddToFullDataset(PATH_TO_ANDROID + "/medium/android-50-100-eval.txt");

        processAndAddToFullDataset(PATH_TO_GOOGLE + "/small/google-50-train.txt");
        processAndAddToFullDataset(PATH_TO_GOOGLE + "/small/google-50-test.txt");
        processAndAddToFullDataset(PATH_TO_GOOGLE + "/small/google-50-eval.txt");

        processAndAddToFullDataset(PATH_TO_GOOGLE + "/medium/google-50-100-train.txt");
        processAndAddToFullDataset(PATH_TO_GOOGLE + "/medium/google-50-100-test.txt");
        processAndAddToFullDataset(PATH_TO_GOOGLE + "/medium/google-50-100-eval.txt");

        processAndAddToFullDataset(PATH_TO_OVIRT + "/ovirt-50-train.txt");
        processAndAddToFullDataset(PATH_TO_OVIRT + "/ovirt-50-test.txt");
        processAndAddToFullDataset(PATH_TO_OVIRT + "/ovirt-50-eval.txt");

        processAndAddToFullDataset(PATH_TO_OVIRT + "/ovirt-50-train.txt");
        processAndAddToFullDataset(PATH_TO_OVIRT + "/medium/ovirt-50-100-test.txt");
        processAndAddToFullDataset(PATH_TO_OVIRT + "/ovirt-50-eval.txt");

        if (CALCULATE_HISTS) {
            saveHists(treeContext);
        }
    }

    private static ITree findMethodNode(ITree node, String content, int startSignatureIndex, int endSignatureIndex) {
        int startPos = node.getPos();
        int endPos = node.getEndPos();

        /*
        if (node.getType() == 31) {
            System.out.println("\n\nMETHOD EXAMPLE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.out.println(content.substring(startPos, endPos));
            System.out.println(startSignatureIndex + " " + endSignatureIndex);
            System.out.println(startPos + " " + endPos);
        }
        */

        if (node.getType() == 31 && startSignatureIndex <= endPos && startPos <= endSignatureIndex)
            return node;

        List<ITree> children = node.getChildren();

        for (ITree child : children) {
            ITree ansTree = findMethodNode(child, content, startSignatureIndex, endSignatureIndex);

            if (ansTree != null)
                return ansTree;
        }

        return null;
    }

    private static void findMethodAndWriteToFile(String filePath, String methodPath, String pathToMethodContent) throws FileNotFoundException {
        ITree tree = null;
        TreeContext context = null;
        try {
            context = Generators.getInstance().getTree(filePath);
            tree = context.getRoot();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        String fileContent = readAllBytesJava7(filePath);
        String methodSignature = readAllBytesJava7(methodPath);

        int startIndex = fileContent.indexOf(methodSignature);

        if (startIndex == -1) {
            System.out.println(filePath);
        }

        int endIndex = startIndex + methodSignature.length();

        //System.out.println(fileContent.substring(startIndex, endIndex));

        ITree methodNode = findMethodNode(tree, fileContent, startIndex, endIndex);

        if (methodNode == null) {
            System.out.println(filePath);
            return;
        }

        String beginningFile = "class Example {\n" +
                "\n";
        String endingFile = "\n" +
                "}";

        //System.out.println("\n\n" + fileContent.substring(methodNode.getPos(), methodNode.getEndPos()) + "\n\n");

        try (PrintWriter out = new PrintWriter(pathToMethodContent)) {
            out.print(beginningFile);
            out.print(fileContent.substring(methodNode.getPos(), methodNode.getEndPos()));
            out.print(endingFile);
        }
    }


    private static String PATH_TO_LASE_DATASET =
            "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/LaseDataset";
    /*
     For Lase dataset.
     */
    public static void mainLase(String[] args) throws IOException {
        Run.initGenerators();

        TreeContext treeContext = null;

        for (int i = 1; i <= 149; i++) {
            System.out.println(i);

            String pathToElement = PATH_TO_LASE_DATASET + "/" + i;

            String beforeFile = pathToElement + "/" + "Before.java";
            String beforeMethodContentPath = pathToElement + "/" + "BeforeMethodContent.java";
            File beforeMethodContentFile = new File(beforeMethodContentPath);
            if (!beforeMethodContentFile.exists())
                findMethodAndWriteToFile(beforeFile, pathToElement + "/" + "method_before",
                    beforeMethodContentPath);

            String afterFile = pathToElement + "/" + "After.java";
            String afterMethodContentPath = pathToElement + "/" + "AfterMethodContent.java";
            File afterMethodContentFile = new File(afterMethodContentPath);
            if (!afterMethodContentFile.exists())
                findMethodAndWriteToFile(afterFile, pathToElement + "/" + "method_after",
                    afterMethodContentPath);

            ITree src = null;
            TreeContext treeSrc = null;
            try {
                treeSrc = Generators.getInstance().getTree(beforeMethodContentPath);
                src = treeSrc.getRoot();
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            if (treeContext == null) {
                treeContext = treeSrc;
            }

            ITree dst = null;
            TreeContext treeDst = null;
            try {
                treeDst = Generators.getInstance().getTree(afterMethodContentPath);
                dst = treeDst.getRoot();
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            // Match
            Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
            try {
                m.match();
            } catch (NullPointerException e) {
                System.out.println("Cannot match: NullPointerException in m.match()");
                break;
            }


            ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
            allFileGen.generate();
            List<Action> allActions = allFileGen.getActions();

            addNgramsToSet(allActions, Integer.toString(i), Integer.toString(i),
                    "sampleChange1.java", USE_SAVED_ACTIONS);
        }

        if (CALCULATE_HISTS) {
            saveHists(treeContext);
        }
    }






    // RAW TUFANO DATASET
    private static String PATH_TO_RAW_ANDROID = "/Users/aliscafo/Downloads/android 2";
    private static String PATH_TO_RAW_GOOGLE = "/Users/aliscafo/Downloads/google 2";
    private static String PATH_TO_RAW_OVIRT = "/Users/aliscafo/Downloads/ovirt 2";

    private static String PATH_TO_RAW_TUFANO_DATASET =
            "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/RawTufanoDataset24k";

    //private static String PATH_TO_RAW_TUFANO_DATASET = "/Volumes/Seagate/Alina/RawTufanoDataset";

    private static Map<String, Integer> labeledDataset = new HashMap<>();
    private static Set<String> foundLabeledDataset = new HashSet<>();

    private static int GLOBAL_DATASET_ID = 1;

    private static void addLabeledMethodsToMap() {
        File labeledTufano = new File(PATH_TO_TUFANO_DATASET);
        File[] dirs = Arrays.stream(labeledTufano.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        for (File dir : dirs) {
            String contentBefore = readAllBytesJava7(dir.getAbsolutePath() + "/before.java");
            String contentAfter = readAllBytesJava7(dir.getAbsolutePath() + "/after.java");

            contentBefore = strip(contentBefore, '\n');
            contentAfter = strip(contentAfter, '\n');

            String pair = contentBefore + " <SEP> " + contentAfter;

            labeledDataset.put(pair, Integer.parseInt(dir.getName()));

            //System.out.println("|" + pair + "|");
        }
    }

    // Saving a part of raw dataset to process.
    private static void processRepo(String pathToRepo) throws FileNotFoundException {
        int processed = 0;
        int MAX_PROCESSED = 8000;

        Set<String> uniqueChanges = new HashSet<>();

        File repo = new File(pathToRepo);
        File[] dirs = Arrays.stream(repo.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        for (File pr : dirs) {

            File prPath = new File(pathToRepo + "/" + pr.getName());
            File[] file_dirs = Arrays.stream(prPath.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
            Arrays.sort(file_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

            for (File fileDir : file_dirs) {

                File filePath = new File(pathToRepo + "/" + pr.getName() + "/" + fileDir.getName());
                File[] method_dirs = Arrays.stream(filePath.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
                Arrays.sort(method_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

                for (File methodDir : method_dirs) {
                    boolean containsLabeled = false;
                    boolean isUnique = true;

                    File beforeFile = new File(methodDir.getAbsolutePath() + "/before.java");
                    File afterFile = new File(methodDir.getAbsolutePath() + "/after.java");

                    if (!beforeFile.exists() || !afterFile.exists()) {
                        continue;
                    }

                    String beginningFile = "class Example {\n" +
                            "\n\n";
                    String endingFile = "\n\n" +
                            "}";

                    String contentBefore = readAllBytesJava7(beforeFile.getAbsolutePath());
                    String contentAfter = readAllBytesJava7(afterFile.getAbsolutePath());
                    contentBefore = beginningFile + strip(contentBefore, '\n') + endingFile;
                    contentAfter = beginningFile + strip(contentAfter, '\n') + endingFile;

                    String pair = contentBefore + " <SEP> " + contentAfter;

                    if (uniqueChanges.contains(pair)) {
                        isUnique = false;
                    } else {
                        uniqueChanges.add(pair);
                    }

                    if (labeledDataset.containsKey(pair)) {
                        foundLabeledDataset.add(pair);
                        containsLabeled = true;
                    }

                    if (isUnique && (processed < MAX_PROCESSED || containsLabeled)) {
                        File file_before = new File(PATH_TO_RAW_TUFANO_DATASET + "/" +
                                GLOBAL_DATASET_ID + "/before.java");

                        if (!file_before.exists()) {
                            file_before.getParentFile().mkdirs();
                        }

                        try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
                            out.print(contentBefore);
                        }

                        File file_after = new File(PATH_TO_RAW_TUFANO_DATASET + "/" +
                                GLOBAL_DATASET_ID + "/after.java");

                        try (PrintWriter out = new PrintWriter(file_after.getAbsolutePath())) {
                            out.print(contentAfter);
                        }

                        File source_file = new File(PATH_TO_RAW_TUFANO_DATASET + "/" +
                                GLOBAL_DATASET_ID + "/source.java");

                        try (PrintWriter out = new PrintWriter(source_file.getAbsolutePath())) {
                            out.print(pathToRepo + "/" + pr.getName() + "/" +
                                    fileDir.getName() + "/" + methodDir.getName());
                        }

                        if (containsLabeled) {
                            File labeledID_file = new File(PATH_TO_RAW_TUFANO_DATASET + "/" +
                                    GLOBAL_DATASET_ID + "/labeledID.java");

                            try (PrintWriter out = new PrintWriter(labeledID_file.getAbsolutePath())) {
                                out.print(labeledDataset.get(pair));
                            }
                        }

                        GLOBAL_DATASET_ID++;
                        processed++;
                    }

                    GLOBAL_ELEMENT_ID++;

                    if (GLOBAL_ELEMENT_ID % 5000 == 0) {
                        System.out.println(GLOBAL_ELEMENT_ID + " " + foundLabeledDataset.size());
                        //System.out.println("|" + pair + "|");
                    }
                }
            }
        }
    }

    private static boolean SAVE_PART_OF_DATA = false;

    /*
     For raw Tufano dataset.
     */
    public static void main/*RawTufano*/(String[] args) throws IOException {
        Run.initGenerators();

        TreeContext treeContext = null;

        if (SAVE_PART_OF_DATA) {
            addLabeledMethodsToMap();

            processRepo(PATH_TO_RAW_ANDROID);
            System.out.println(GLOBAL_ELEMENT_ID);

            processRepo(PATH_TO_RAW_GOOGLE);
            System.out.println(GLOBAL_ELEMENT_ID);

            processRepo(PATH_TO_RAW_OVIRT);
            System.out.println(GLOBAL_ELEMENT_ID);

            System.out.println("FOUND: " + foundLabeledDataset.size());
        }

        File repo = new File(PATH_TO_RAW_TUFANO_DATASET);
        File[] dirs = Arrays.stream(repo.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        for (File dir : dirs) {
            File actionsFile = new File(ACTIONS_DIR_PATH + "/" + dir.getName());

            /*
            if (actionsFile.exists())
                continue;
            */

            System.out.println(dir.getName());

            if (USE_SAVED_ACTIONS && treeContext != null) {
                addNgramsToSet(null, dir.getName(), dir.getName(),
                        "sampleChange1.java", USE_SAVED_ACTIONS);
            } else {
                String beforeMethodContentPath = dir.getAbsolutePath() + "/before.java";

                ITree src = null;
                TreeContext treeSrc = null;
                try {
                    treeSrc = Generators.getInstance().getTree(beforeMethodContentPath);
                    src = treeSrc.getRoot();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                if (treeContext == null) {
                    treeContext = treeSrc;
                }

                String afterMethodContentPath = dir.getAbsolutePath() + "/after.java";

                ITree dst = null;
                TreeContext treeDst = null;
                try {
                    treeDst = Generators.getInstance().getTree(afterMethodContentPath);
                    dst = treeDst.getRoot();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                // Match
                Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
                try {
                    m.match();
                } catch (NullPointerException e) {
                    System.out.println("Cannot match: NullPointerException in m.match()");
                    break;
                }


                ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
                allFileGen.generate();
                List<Action> allActions = allFileGen.getActions();

                for (Action action : allActions)
                    System.out.println(action.toString());

                addNgramsToSet(allActions, dir.getName(), dir.getName(),
                        "sampleChange1.java", USE_SAVED_ACTIONS);
            }
        }

        if (CALCULATE_HISTS) {
            saveHists(treeContext);
        }
    }


    //private static String RESULTS_FOR_HIST_DIR = "/Volumes/Seagate/Alina/result_for_hist_all_data3-50-1gram";
    // TODO: Uncomment
    private static boolean JUST_TESTING = true;

    /*
     This main calculates edit script another way. It extracts each method ("before" and "after") to separate file and
     run gumtree algorithm on these two files instead of running gumtree on original files.
    */

    public static void mainMOST(String[] args) throws IOException, GitAPIException {
        Run.initGenerators();

        TreeContext treeContext = null;

        if (USE_SAVED_ACTIONS) {
            treeContext = getTreeContext();
        }

        File patternsDir = new File(PATTERNS_PATH);
        File[] size_dirs = Arrays.stream(patternsDir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(size_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        boolean if_break = false;

        for (File size_dir : size_dirs) {
            if (if_break) {
                break;
            }

            // TODO: UNCOMMENT WHILE CALCULATING HISTS
            if (CALCULATE_HISTS) {
                if (!(consideredPatternsA <= Integer.parseInt(size_dir.getName()) &&
                        Integer.parseInt(size_dir.getName()) <= consideredPatternsB)) {
                    continue;
                }
            }

            File[] id_dirs = Arrays.stream(size_dir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
            Arrays.sort(id_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

            int num_processed = 0;

            for (File id_dir : id_dirs) {
                // Too long calculation
                /*if (id_dir.getName().equals("6520")) {
                    continue;
                }
                if (id_dir.getName().equals("10518")) {
                    continue;
                }
                if (id_dir.getName().equals("12342")) {
                    continue;
                }*/

                num_processed++;

                if (if_break) {
                    break;
                }

                File[] files = id_dir.listFiles();

                for (File file : files) {
                    if (if_break) {
                        break;
                    }

                    if (!file.getName().startsWith("sampleChange") || file.getName().equals("sampleChange.html")) {
                        continue;
                    }

                    if (size_dir.getName().equals("3") && id_dir.getName().equals("192") &&
                            file.getName().startsWith("sampleChange3")) {
                        continue;
                    }

                    System.out.println("\n\n\n" + size_dir.getName() + " " + id_dir.getName());
                    System.out.println("FILE: " + file.getName());

                    //
                    //
                    //
                    //
                    // TODO: DELETE WHILE CALCULATING HISTS !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                    if (!CALCULATE_HISTS) {
                        String filename = file.getName();
                        File curActionFile = new File(ACTIONS_DIR_PATH + "/" +
                                size_dir.getName() + "/" + id_dir.getName() + "/" + filename.substring(0, filename.length() - 5));
                        if (curActionFile.exists()) {
                            System.out.println("EDIT SCRIPT EXISTS!");
                            continue;
                        }
                    }
                    //
                    //
                    //
                    //
                    //

                    if (USE_SAVED_ACTIONS) {
                        // TODO: make correct calculation of num_changes.
                        num_changes++;
                        addNgramsToSet(null, size_dir.getName(), id_dir.getName(), file.getName(), true);
                        continue;
                    }

                    String content = readAllBytesJava7(file.getAbsolutePath());
                    int startInd = content.indexOf("<html><h3>") + "<html><h3>".length();
                    int endInd = content.indexOf("</h3><h3>");

                    String name = content.substring(startInd, endInd);
                    String[] parts = name.split(",");
                    String repoName = parts[parts.length - 1].trim();
                    String commitName = parts[0];
                    String fileName = parts[1];
                    String methodName = parts[3];

                    if (repoName.equals("apache/pig") && commitName.equals("19c9d91aa66d3b3d102dbf44437958f5b7957098"))
                        commitName = "deb4a30b610806bb1b99bccd454811e77eafab90";
                    if (repoName.equals("apache/pig") && commitName.equals("3adf82bbd8d6c324e5a09554f5064c316af071a3"))
                        commitName = "dc0fa7b44b8ac83d8df4a0dac9f48eab76851148";
                    if (repoName.equals("apache/pig") && commitName.equals("d1ea940063270199e57c5efde9fbf76912773c7a"))
                        commitName = "6dc4f57f4b8592c1804df08891036ce1515b1f9a";
                    if (repoName.equals("apache/pig") && commitName.equals("9e280062373d817221e2466ce1234741ad964f44"))
                        commitName = "e9ff9c27c10c61362e8460b35af4b88b21ef8f15";

                    GitConnector gc = new GitConnector(REPOS_PATH + "/" + repoName + "/.git");
                    System.out.println(REPOS_PATH + "/" + repoName + "/.git");
                    ArrayList<String> afterAndBefore = null;

                    File fileBeforeSaved = new File(COMMIT_FILES_DIR + "/" +
                            repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_before.java");
                    File fileAfterSaved = new File(COMMIT_FILES_DIR + "/" +
                            repoName + "/" + commitName + "/" + fileName.substring(0, fileName.length() - 5) + "_after.java");

                    System.out.println(fileBeforeSaved.getPath());
                    System.out.println(fileAfterSaved.getPath());

                    if (!fileBeforeSaved.exists() || !fileAfterSaved.exists()) {
                        if (gc.connect()) {
                            afterAndBefore = gc.getFileFromCommit(commitName, fileName);
                            gc.close();
                        } else {
                            System.out.println("GitConnector is not connected");
                        }
                    } else {
                        System.out.println("Files were saved.");

                        afterAndBefore = new ArrayList<>();
                        afterAndBefore.add(readAllBytesJava7(fileAfterSaved.getAbsolutePath()));
                        afterAndBefore.add(readAllBytesJava7(fileBeforeSaved.getAbsolutePath()));
                    }

                    String afterContent = afterAndBefore.get(0).replace("\t", "    ")
                            .replace("&gt;", ">")
                            .replace("&lt;", "<");
                    String beforeContent = afterAndBefore.get(1).replace("\t", "    ")
                            .replace("&gt;", ">")
                            .replace("&lt;", "<");

                    File file_before = new File("tmp/file_before.java");
                    file_before.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
                        out.print(beforeContent);
                    }
                    ITree src = null;
                    TreeContext treeSrc = null;
                    try {
                        treeSrc = Generators.getInstance().getTree(file_before.getPath());
                        src = treeSrc.getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (treeContext == null) {
                        treeContext = treeSrc;
                    }

                    File file_after = new File("tmp/file_after.java");
                    file_after.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_after.getAbsolutePath())) {
                        out.print(afterContent);
                    }
                    ITree dst = null;
                    TreeContext treeDst = null;
                    try {
                        treeDst = Generators.getInstance().getTree(file_after.getPath());
                        dst = treeDst.getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }


                    if (size_dir.getName().equals("3") && id_dir.getName().equals("27") &&
                            file.getName().equals("sampleChange3.html")) {
                        try (PrintWriter out = new PrintWriter("tmp/src_tree_important")) {
                            List<ITree> srcList = new ArrayList<>();
                            src.preOrder().forEach(srcList::add);
                            for (ITree iTree : srcList) {
                                out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                                        iTree.toShortString() + "       -> " + iTree.getId());
                            }
                        }
                        try (PrintWriter out = new PrintWriter("tmp/dst_tree_important")) {
                            List<ITree> dstList = new ArrayList<>();
                            dst.preOrder().forEach(dstList::add);
                            for (ITree iTree : dstList) {
                                out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                                        iTree.toShortString() + "       -> " + iTree.getId());
                            }
                        }
                    }



                    // Match
                    Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
                    try {
                        m.match();
                    } catch (NullPointerException e) {
                        System.out.println("Cannot match: NullPointerException in m.match()");
                        if_break = true;
                        break;
                    }


                    Pair<Integer, Integer> startIndsMethodsBeforeAfter =
                            findMethodsStartIndices(methodName, src, dst, m.getMappings());

                    Pair<Integer, Integer> startIndsForMethods = raiseNLinesUp(startIndsMethodsBeforeAfter, 4,
                            beforeContent, afterContent);

                    ArrayList<String> beforeAndAfterFragments = null;
                    beforeAndAfterFragments = getFragments(content);

                    Indices indicesBefore = getCorrectIndices(beforeContent, beforeAndAfterFragments.get(0),
                            startIndsForMethods.getFirst());
                    Indices indicesAfter = getCorrectIndices(afterContent, beforeAndAfterFragments.get(1),
                            startIndsForMethods.getSecond());

                    if (indicesBefore == null) {
                        System.out.println("Before fragment wasn't found in file.");
                        System.out.println("Before fragment:");
                        System.out.println(beforeAndAfterFragments.get(0));
                        //if_break = true;
                        //break;
                        FileWriter fileWriter = new FileWriter("NotFoundFragmentsSecondWay.txt", true);
                        PrintWriter printWriter = new PrintWriter(fileWriter);
                        printWriter.print(size_dir.getName() + " " + id_dir.getName() + " " + file.getName() + " " + "before" + "\n");
                        printWriter.close();
                        continue;
                    }
                    if (indicesAfter == null) {
                        System.out.println("After fragment wasn't found in file.");
                        System.out.println("After fragment:");
                        System.out.println(beforeAndAfterFragments.get(1));
                        FileWriter fileWriter = new FileWriter("NotFoundFragmentsSecondWay.txt", true);
                        PrintWriter printWriter = new PrintWriter(fileWriter);
                        printWriter.print(size_dir.getName() + " " + id_dir.getName() + " " + file.getName() + " " + "after" + "\n");
                        printWriter.close();
                        continue;
                        //if_break = true;
                        //break;
                    }

                    //System.out.println(beforeContent.substring(indicesBefore.start, indicesBefore.end));

                    ITree beforeNode = findMinCoverNode(src, indicesBefore.start, indicesBefore.end);
                    if (beforeNode == null) {
                        System.out.println("beforeNode is NULL " + file.getName());
                        //if_break = true;
                        //break;
                        continue;
                    }

                    ITree afterNode = findMinCoverNode(dst, indicesAfter.start, indicesAfter.end);
                    if (afterNode == null) {
                        System.out.println("afterNode is NULL " + file.getName());
                        //if_break = true;
                        //break;
                        continue;
                    }



                    Pair<ITree, String> methodBeforePair = getCorrespondingMethodDeclarationNode(beforeNode);
                    if (methodBeforePair.getFirst() == null) {
                        System.out.println("methodNodeBefore is null");
                        if_break = true;
                        break;
                    }
                    Pair<ITree, String> methodAfterPair = getCorrespondingMethodDeclarationNode(afterNode);
                    if (methodAfterPair.getFirst() == null) {
                        System.out.println("methodNodeAfter is null");
                        if_break = true;
                        break;
                    }

                    System.out.println("Method before: " + methodBeforePair.getSecond());
                    System.out.println("Method after: " + methodAfterPair.getSecond());
                    System.out.println("Real method name: " + methodName);

                    if (!methodBeforePair.getSecond().equals(methodAfterPair.getSecond())) {
                        System.out.println("\nmethod BEFORE != method AFTER !!!!!!");
                        //if_break = true;
                        //break;
                    }
                    if (!methodBeforePair.getSecond().equals(methodName)) {
                        System.out.println("\nmethod BEFORE != REAL methodName !!!!!!");
                        //if_break = true;
                        //break;
                    }


                    if (JUST_TESTING) {
                        continue;
                    }


                    try (PrintWriter out = new PrintWriter("tmp/before_node")) {
                        out.print(beforeNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/after_node")) {
                        out.print(afterNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/src_tree")) {
                        out.print(src.toTreeString());
                    }
                    try (PrintWriter out = new PrintWriter("tmp/dst_tree")) {
                        out.print(dst.toTreeString());
                    }


                    ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
                    allFileGen.generate();


                    BeforeAfterNodes nodes = getBeforeAfterNodes(m,
                            indicesBefore.start, indicesBefore.end, indicesAfter.start, indicesAfter.end,
                            beforeContent, afterContent);

                    ITree nodeBeforeRaw = nodes.rawBefore;
                    ITree nodeAfterRaw = nodes.rawAfter;

                    List<Action> allActions = allFileGen.getActions();
                    List<Action> extractedActionsRaw = extractActions(allActions, nodeBeforeRaw, nodeAfterRaw);

                    System.out.println("______________________________________");
                    System.out.println("ACTIONS SIZE");
                    System.out.println(extractedActionsRaw.size());


                    try {
                        System.out.println("\nActions retrieved in the third (raw) way:");
                        for (Action action : extractedActionsRaw) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.toString());
                            //System.out.println(action.format(treeSrc));
                            //System.out.println(action.getNode().toTreeString());
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }

                    } catch (NullPointerException e) {
                        System.out.println("\nActions retrieved in the third (raw) way:");
                        for (Action action : extractedActionsRaw) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.toString());
                            //System.out.println(action.format(treeSrc));
                            //System.out.println(action.getNode().toTreeString());
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }

                        if_break = true;
                        break;
                        //continue;
                    }

                    num_changes++;
                    addNgramsToSet(extractedActionsRaw, size_dir.getName(), id_dir.getName(), file.getName(), false);
                }
            }

        }

        System.out.println("\n\n\nNUM CHANGES: " + num_changes);

        if (CALCULATE_HISTS) {
            saveHists(treeContext);
        }
    }

    // TODO!!!
    private static Pair<Integer, Integer> raiseNLinesUp(Pair<Integer, Integer> startIndsMethodsBeforeAfter, int n,
                                                        String beforeContent, String afterContent) {
        String[] linesBefore = beforeContent.split("\n");
        String[] linesAfter = afterContent.split("\n");

        List<Integer> linesStartIndsBefore = new ArrayList<>();
        List<Integer> linesStartIndsAfter = new ArrayList<>();

        int curLineStartInd = 0;
        int i;
        for (i = 0; i < linesBefore.length; i++) {
            linesStartIndsBefore.add(curLineStartInd);
            if (startIndsMethodsBeforeAfter.getFirst() <= curLineStartInd + linesBefore[i].length()) {
                break;
            }
            curLineStartInd += linesBefore[i].length() + 1;
        }

        curLineStartInd = 0;
        int j;
        for (j = 0; j < linesAfter.length; j++) {
            linesStartIndsAfter.add(curLineStartInd);
            if (startIndsMethodsBeforeAfter.getSecond() <= curLineStartInd + linesAfter[j].length()) {
                break;
            }
            curLineStartInd += linesAfter[j].length() + 1;
        }

        Integer finalIndBefore = linesStartIndsBefore.get(Math.max(0, linesStartIndsBefore.size() - 1 - n));
        Integer finalIndAfter = linesStartIndsAfter.get(Math.max(0, linesStartIndsAfter.size() - 1 - n));

        return new Pair<>(finalIndBefore, finalIndAfter);
    }

    /*
        Finds method BEFORE by name and then tries to find mapping to corresponding method AFTER.
     */
    private static Pair<Integer, Integer> findMethodsStartIndices(String methodName, ITree src, ITree dst, MappingStore m) throws FileNotFoundException {
        List<ITree> treesBefore = src.getTrees();
        ITree beforeMethodNode = null;
        ITree simpleNameNodeBefore = null;
        ITree afterMethodNode = null;
        ITree simpleNameNodeAfter = null;

        for (ITree tree : treesBefore) {
            if (tree.getType() == 31) {
                ITree simpleNameNode =
                        tree.getChildren().stream().filter(c -> c.getType() == 42).collect(Collectors.toList()).get(0);
                if (simpleNameNode.getLabel().equals(methodName)) {
                    simpleNameNodeBefore = simpleNameNode;
                    beforeMethodNode = tree;
                    break;
                }
            }
        }

        afterMethodNode = m.getDst(beforeMethodNode);

        if (afterMethodNode == null) {
            simpleNameNodeAfter = m.getDst(simpleNameNodeBefore);

            if (simpleNameNodeAfter == null) {
                List<ITree> treesAfter = dst.getTrees();

                for (ITree tree : treesAfter) {
                    if (tree.getType() == 31) {
                        ITree simpleNameNode =
                                tree.getChildren().stream().filter(c -> c.getType() == 42).collect(Collectors.toList()).get(0);
                        if (simpleNameNode.getLabel().equals(methodName)) {
                            simpleNameNodeAfter = simpleNameNode;
                            break;
                        }
                    }
                }
            }
            afterMethodNode = simpleNameNodeAfter.getParent();
        }

        if (beforeMethodNode == null) {
            System.out.println("BEFORE NODE IS NULL !!!");
        }

        if (afterMethodNode == null) {
            System.out.println("Before ID: " + beforeMethodNode.getId());

            System.out.println("\nMAPPINGS:");
            for (Mapping mapping : m.asSet()) {
                System.out.println(mapping.first.getId() + " -> " + mapping.second.getId());
            }
            System.out.println("\n");

            try (PrintWriter out = new PrintWriter("tmp/src_tree_preorder_last")) {
                List<ITree> srcList = new ArrayList<>();
                src.preOrder().forEach(srcList::add);
                for (ITree iTree : srcList) {
                    out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                            iTree.toShortString() + "       -> " + iTree.getId());
                }
            }
            try (PrintWriter out = new PrintWriter("tmp/dst_tree_preorder_last")) {
                List<ITree> dstList = new ArrayList<>();
                dst.preOrder().forEach(dstList::add);
                for (ITree iTree : dstList) {
                    out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                            iTree.toShortString() + "       -> " + iTree.getId());
                }
            }
        }


        ITree simpleNameAfter =
                afterMethodNode.getChildren().stream().filter(c -> c.getType() == 42).collect(Collectors.toList()).get(0);

        System.out.println("METHOD BEFORE NAME: " + methodName);
        System.out.println("METHOD AFTER NAME: " + simpleNameAfter.getLabel());

        return new Pair<>(beforeMethodNode.getPos(), afterMethodNode.getPos());
    }

    /*
        Returns corresponding MethodDeclaration node and the name of the method.
     */
    private static Pair<ITree, String> getCorrespondingMethodDeclarationNode(ITree beforeNode) {
        ITree methodDeclarationNode = beforeNode;

        while (methodDeclarationNode != null && methodDeclarationNode.getType() != 31) {
            methodDeclarationNode = methodDeclarationNode.getParent();
        }

        if (methodDeclarationNode == null) {
            return new Pair<>(null, null);
        }

        String methodName = null;

        for (ITree child : methodDeclarationNode.getChildren()) {
            if (child.getType() == 42) {
                methodName = child.getLabel();
                break;
            }
        }

        return new Pair<>(methodDeclarationNode, methodName);
    }

    public static void main3(String[] args) throws IOException, GitAPIException {
        Run.initGenerators();

        TreeContext treeContext = null;

        File patternsDir = new File(PATTERNS_PATH);
        File[] size_dirs = Arrays.stream(patternsDir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(size_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        //int num_changes = 0;
        boolean if_break = false;

        for (File size_dir : size_dirs) {
            if (if_break) {
                break;
            }

            if (size_dir.getName().equals("50")) {
                break;
            }

            File[] id_dirs = Arrays.stream(size_dir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
            Arrays.sort(id_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

            int num_processed = 0;

            for (File id_dir : id_dirs) {
                if (num_processed >= 50) {
                    break;
                }
                num_processed++;


                if (if_break) {
                    break;
                }

                File[] files = id_dir.listFiles();

                for (File file : files) {
                    if (if_break) {
                        break;
                    }

                    /*if (file.getName().startsWith("sampleChange3")) {
                        if_break = true;
                        break;
                    }*/

                    if (!file.getName().startsWith("sampleChange") || file.getName().equals("sampleChange.html")) {
                        continue;
                    }

                    System.out.println("\n\n\n" + size_dir.getName() + " " + id_dir.getName());
                    System.out.println("FILE: " + file.getName());

                    String content = readAllBytesJava7(file.getAbsolutePath());
                    int startInd = content.indexOf("<html><h3>") + "<html><h3>".length();
                    int endInd = content.indexOf("</h3><h3>");

                    String name = content.substring(startInd, endInd);
                    String[] parts = name.split(",");
                    String repoName = parts[parts.length - 1].trim();
                    String commitName = parts[0];
                    String fileName = parts[1];

                    GitConnector gc = new GitConnector(REPOS_PATH + "/" + repoName + "/.git");
                    System.out.println(REPOS_PATH + "/" + repoName + "/.git");
                    ArrayList<String> afterAndBefore = null;

                    File fileBeforeSaved = new File(COMMIT_FILES_DIR + "/" +
                            repoName + "/" + commitName + "/" + fileName.split("\\.")[0] + "_before.java");
                    File fileAfterSaved = new File(COMMIT_FILES_DIR + "/" +
                            repoName + "/" + commitName + "/" + fileName.split("\\.")[0] + "_after.java");

                    if (!fileBeforeSaved.exists() || !fileAfterSaved.exists()) {
                        if (gc.connect()) {
                            afterAndBefore = gc.getFileFromCommit(commitName, fileName);
                            gc.close();
                        } else {
                            System.out.println("GitConnector is not connected");
                        }
                    } else {
                        afterAndBefore = new ArrayList<>();
                        afterAndBefore.add(readAllBytesJava7(fileAfterSaved.getAbsolutePath()));
                        afterAndBefore.add(readAllBytesJava7(fileBeforeSaved.getAbsolutePath()));
                    }

                    String afterContent = afterAndBefore.get(0).replace("\t", "    ");
                    String beforeContent = afterAndBefore.get(1).replace("\t", "    ");

                    File file_before = new File("tmp/file_before.java");
                    file_before.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
                        out.print(beforeContent);
                    }
                    ITree src = null;
                    TreeContext treeSrc = null;
                    try {
                        treeSrc = Generators.getInstance().getTree(file_before.getPath());
                        src = treeSrc.getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (treeContext == null) {
                        treeContext = treeSrc;
                    }

                    File file_after = new File("tmp/file_after.java");
                    file_after.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_after.getAbsolutePath())) {
                        out.print(afterContent);
                    }
                    ITree dst = null;
                    TreeContext treeDst = null;
                    try {
                        treeDst = Generators.getInstance().getTree(file_after.getPath());
                        dst = treeDst.getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    ArrayList<String> beforeAndAfterFragments = null;
                    beforeAndAfterFragments = getFragments(content);

                    Indices indicesBefore = getCorrectIndices(beforeContent, beforeAndAfterFragments.get(0), 0);
                    Indices indicesAfter = getCorrectIndices(afterContent, beforeAndAfterFragments.get(1), 0);

                    if (indicesBefore == null || indicesAfter == null) {
                        if_break = true;
                        break;
                    }

                    //System.out.println(beforeContent.substring(indicesBefore.start, indicesBefore.end));

                    ITree beforeNode = findMinCoverNode(src, indicesBefore.start, indicesBefore.end);
                    if (beforeNode == null) {
                        System.out.println("beforeNode is NULL " + file.getName());
                        //if_break = true;
                        //break;
                        continue;
                    }

                    ITree afterNode = findMinCoverNode(dst, indicesAfter.start, indicesAfter.end);
                    if (afterNode == null) {
                        System.out.println("afterNode is NULL " + file.getName());
                        //if_break = true;
                        //break;
                        continue;
                    }

                    try (PrintWriter out = new PrintWriter("tmp/before_node")) {
                        out.print(beforeNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/after_node")) {
                        out.print(afterNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/src_tree")) {
                        out.print(src.toTreeString());
                    }
                    try (PrintWriter out = new PrintWriter("tmp/dst_tree")) {
                        out.print(dst.toTreeString());
                    }

                    //System.out.println(treeContext.getTypeLabel(32));

                    //System.out.println("BEFORE DEPTH: " + beforeNode.getDepth());
                    //System.out.println("AFTER DEPTH: " + afterNode.getDepth());

                    // Match
                    Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
                    try {
                        m.match();
                    } catch (NullPointerException e) {
                        System.out.println("Cannot match: NullPointerException in m.match()");

                        //System.out.println(beforeNode.toTreeString());
                        //System.out.println("BEFORE DEPTH: " + beforeNode.getDepth());
                        //System.out.println("_________");
                        //System.out.println(afterNode.toTreeString());
                        //System.out.println("AFTER DEPTH: " + afterNode.getDepth());

                        if_break = true;
                        break;
                    }

                    ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
                    allFileGen.generate();

                    /*if (id_dir.getName().equals("10") && file.getName().startsWith("sampleChange6")) {
                        System.out.println("CHECKING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        checkingAllTree(src, m, beforeContent, afterContent);
                    }*/

                    //System.out.println(treeSrc.getTypeLabel(42));

                    BeforeAfterNodes nodes = getBeforeAfterNodes(m,
                            indicesBefore.start, indicesBefore.end, indicesAfter.start, indicesAfter.end,
                            beforeContent, afterContent);

                    ITree nodeBefore = nodes.before;
                    ITree nodeAfter = nodes.after;
                    ITree nodeBeforeRaw = nodes.rawBefore;
                    ITree nodeAfterRaw = nodes.rawAfter;

                    Matcher fragmentsMatcher = Matchers.getInstance().getMatcher(nodeBefore, nodeAfter);
                    fragmentsMatcher.match();

                    System.out.println(nodeBefore.toTreeString());
                    System.out.println("________________________");
                    System.out.println(nodeAfter.toTreeString());
                    System.out.println("\n_________________________\n");

                    //ActionGenerator g = new ActionGenerator(nodeBefore, nodeAfter, m.getMappings());
                    ActionGenerator g = new ActionGenerator(nodeBefore, nodeAfter, fragmentsMatcher.getMappings());
                    g.generate();
                    List<Action> actions = g.getActions();

                    //TreeContext treeContext = new TreeContext();
                    //System.out.println(treeContext.getTypeLabel(42));

                    System.out.println("______________________________________");
                    System.out.println("ACTIONS SIZE");
                    System.out.println(actions.size());

                    /*if (actions.size() > NUM_ACTIONS_THRESHOLD) {
                        if_break = true;
                        break;
                        //continue;
                    }*/

                    //ActionGenerator allFileGen = new ActionGenerator(src, dst, m.getMappings());
                    //allFileGen.generate();
                    List<Action> allActions = allFileGen.getActions();
                    List<Action> extractedActions = extractActions(allActions, nodeBefore, nodeAfter);
                    List<Action> extractedActionsRaw = extractActions(allActions, nodeBeforeRaw, nodeAfterRaw);

                    try {
                        System.out.println("\nActions retrieved in the first way:");
                        for (Action action : actions) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.toString());
                            //System.out.println(action.format(treeSrc));
                            //System.out.println(action.getNode().toTreeString());
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }

                        System.out.println("\nActions retrieved in the second way:");
                        for (Action action : extractedActions) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.toString());
                            //System.out.println(action.format(treeSrc));
                            //System.out.println(action.getNode().toTreeString());
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }

                        System.out.println("\nActions retrieved in the third (raw) way:");
                        for (Action action : extractedActionsRaw) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.toString());
                            //System.out.println(action.format(treeSrc));
                            //System.out.println(action.getNode().toTreeString());
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }

                    } catch (NullPointerException e) {
                        System.out.println("NullPointerException in printing actions");

                        System.out.println("\nActions retrieved in the second way:");
                        for (Action action : extractedActions) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.toString());
                            //System.out.println(action.format(treeSrc));
                            //System.out.println(action.getNode().toTreeString());
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }

                        System.out.println("\nActions retrieved in the third (raw) way:");
                        for (Action action : extractedActionsRaw) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.toString());
                            //System.out.println(action.format(treeSrc));
                            //System.out.println(action.getNode().toTreeString());
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }

                        if_break = true;
                        break;
                        //continue;
                    }

                    num_changes++;
                    addNgramsToSet(extractedActions, size_dir.getName(), id_dir.getName(), file.getName(), false);

                    /*if (size_dir.getName().equals("3") && id_dir.getName().equals("1") && file.getName().equals("sampleChange3.html")) {
                        System.out.println(nodeBefore.toTreeString());
                        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        System.out.println(nodeAfter.toTreeString());
                        if_break = true;
                        break;
                    }*/


                    /*if (id_dir.getName().equals("10") && file.getName().startsWith("sampleChange6")) {
                        if_break = true;
                        break;
                    }*/
                }
            }

        }

        System.out.println("\n\n\nNUM CHANGES: " + num_changes);

        //printHist(treeContext);
        saveHists(treeContext);
    }

    private static List<Action> extractActions(List<Action> allActions, ITree nodeBefore, ITree nodeAfter) {
        List<Action> resultActions = new ArrayList<>();

        Set<ITree> setBefore = new HashSet<>(nodeBefore.getTrees());
        Set<ITree> setAfter = new HashSet<>(nodeAfter.getTrees());

        //System.out.println("DESCENDANTS: \n" + nodeAfter.getTrees().size());

        for (Action action : allActions) {
            //System.out.println("ACTION: " + action.toString());
            //System.out.println(action.getNode().toTreeString());

            ITree nodeOfAction = action.getNode();
            /*if (nodeBefore.getPos() <= nodeOfAction.getPos() && nodeOfAction.getEndPos() <= nodeBefore.getEndPos()) {
                resultActions.add(action);
            }*/
            if (setBefore.contains(nodeOfAction) || setAfter.contains(nodeOfAction)) {
                resultActions.add(action);
            }
        }

        return resultActions;
    }



    private static List<Action> extractActionsWithOrdering(List<Action> allActions, ITree nodeBefore, ITree nodeAfter) {
        List<Action> resultActions = new ArrayList<>();

        Set<ITree> setBefore = new HashSet<>(nodeBefore.getTrees());
        Set<ITree> setAfter = new HashSet<>(nodeAfter.getTrees());



        System.out.println("\nBefore preOrder");
        List<ITree> beforeList = new ArrayList<>();
        //nodeBefore.breadthFirst().forEach(list::add);
        nodeBefore.preOrder().forEach(beforeList::add);
        for (ITree iTree : beforeList) {
            System.out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                    iTree.toShortString() + "       -> " + iTree.getId());
        }
        System.out.println("\n");


        System.out.println("\nAfter preOrder");
        List<ITree> afterList = new ArrayList<>();
        //nodeBefore.breadthFirst().forEach(list::add);
        nodeAfter.preOrder().forEach(afterList::add);
        for (ITree iTree : afterList) {
            System.out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                    iTree.toShortString() + "       -> " + iTree.getId());
        }
        System.out.println("\n");


/*
        System.out.println("\n\nBefore trees:");

        for (ITree iTree : setBefore) {
            System.out.println(iTree.toShortString() + "       -> " + iTree.getId());
        }

        System.out.println("\nAfter trees:");

        for (ITree iTree : setAfter) {
            System.out.println(iTree.toShortString() + "       -> " + iTree.getId());
        }

        System.out.println("\n\n");
*/
        //Map<ITree, ArrayList<Action>> from

        //System.out.println("DESCENDANTS: \n" + nodeAfter.getTrees().size());

        for (Action action : allActions) {
            //System.out.println("ACTION: " + action.toString());
            //System.out.println(action.getNode().toTreeString());
            
            ITree nodeOfAction = action.getNode();

            if (nodeOfAction.getLabel().equals("public")) {
                System.out.println(nodeOfAction.getParent().toTreeString());
            }

            System.out.println(action.toString() + " -> " + action.getNode().getLabel());

            if (setBefore.contains(nodeOfAction)) {
                // Can only be MOV, UPD, DEL
                //System.out.println(("BEFORE CONTAINS: " + action.toString()));
                resultActions.add(action);
            }

            if (setAfter.contains(nodeOfAction)) {
                // Can only be INS
                //System.out.println(("AFTER CONTAINS: " + action.toString()));
                resultActions.add(action);
            }
        }

        return resultActions;
    }

    public static void mainCheckGetOnlyBlackChanges(String[] args) {
        String s = "\t\tif (recipeType == null) {\n" +
                "\t\t\treturn false;\n" +
                "\t\t}\n" +
                "\t\tfor (ItemStack <a id=\"change\">input</a> : recipeType.getInputs()) {\n" +
                "\t\t\tBoolean hasItem = false;\n" +
                "\t\t\tfor (int inputslot : inputSlots) {\n" +
                "\t\t\t\tif (ItemUtils.<a id=\"change\">isItemEqual(</a>input, inventory.getStackInSlot(inputslot), true, true,\n" +
                "\t\t\t\t\trecipeType.<a id=\"change\">useOreDic())</a> && inventory.getStackInSlot(inputslot).getCount() &gt;= input.getCount()) {\n" +
                "\t\t\t\t\thasItem = true;\n" +
                "\t\t\t\t}\n" +
                "\t\t\t}";

        System.out.println(getOnlyBlackChanges2(s));
    }

    static class BeforeAfterNodes {
        ITree before;
        ITree after;

        ITree rawBefore;
        ITree rawAfter;

        BeforeAfterNodes(ITree b, ITree a, ITree rb, ITree ra) {
            before = b;
            after = a;
            rawBefore = rb;
            rawAfter = ra;
        }
    }

    /*public static MappingStore getMappingStore(ITree nodeBefore, ITree nodeAfter, MappingStore store) {
        MappingStore newStore = new MappingStore();

        for
    }*/


    public static BeforeAfterNodes getBeforeAfterNodes(Matcher m, int start_ind_before, int end_ind_before,
                                                       int start_ind_after, int end_ind_after, String file_before, String file_after) {
        ITree nodeBeforeFile = m.getSrc();
        ITree nodeAfterFile = m.getDst();

        MappingStore store = m.getMappings();
        //MappingStore newStore = new MappingStore();

        ITree nodeBefore = findMinCoverNode(nodeBeforeFile, start_ind_before, end_ind_before);
        ITree nodeAfter = findMinCoverNode(nodeAfterFile, start_ind_after, end_ind_after);

        /*System.out.println("NODE BEFORE");
        System.out.println(nodeBefore.toTreeString());
        System.out.println("NODE AFTER");
        System.out.println(nodeAfter.toTreeString());
        System.out.println("____________________________");*/

        // TODO: (JUST A NOTE) currently I don't need to match roots. That's why I return nulls.
        //return new BeforeAfterNodes(null, null, nodeBefore, nodeAfter);

        //System.out.println("\nINDICES AND NODE INDICES:");
        //System.out.println(start_ind_before + " " + end_ind_before);

        //System.out.println("NODE BEFORE");
        //System.out.println(nodeBefore.toTreeString());
        //System.out.println("NODE AFTER");
        //System.out.println(nodeAfter.toTreeString());

        /*if (!store.has(nodeBefore, nodeAfter)) {
            System.out.println("NOT EXIST :(");
        }*/

        //System.out.println("MAPPED");
        //System.out.println(store.getDst(nodeBefore).toTreeString());

        //System.out.println("IF IN STORE");
        //System.out.println(store.has(nodeBeforeFile, nodeAfterFile));


        //System.out.println("HASHES");
        //System.out.println(store.getDst(nodeBefore).getHash());
        //System.out.println(nodeAfter.getHash());

        //System.out.println("MAPPED");
        //System.out.println(store.getSrc(nodeAfter).toTreeString());

        //System.out.println("FIRST MAPPED PARENT");
        //System.out.println(store.firstMappedSrcParent(nodeBefore).toTreeString());

        int cur_num = 0;

        for (ITree i = nodeBefore; i != null; i = i.getParent()) {
            //System.out.println("CURRENT\n " + i.toTreeString());
            //System.out.println("HASHCODE: " + i.hashCode());

            if (store.getDst(i) == null) {
                continue;
            }

            //System.out.println("CURRENT MAPPING POS!!!!!!: " + store.getDst(i).getPos() + " " + store.getDst(i).getEndPos());

            int steps = 0;

            for (ITree j = nodeAfter; j != null; j = j.getParent()) {
                /*
                if (cur_num < 2 && steps < 2) {
                    System.out.println("J:");
                    System.out.println(j.toTreeString());
                    System.out.println(j.getDepth());
                    System.out.println(j.getPos() + " " + j.getEndPos());
                    ITree parent = j.getParent().getParent().getParent().getParent().getParent();
                    //System.out.println("PARENTS TREE: \n" + parent.toTreeString());
                    System.out.println("PARENTS POS: " + parent.getPos() + " " + parent.getEndPos());
                    System.out.println("HASHCODE: " + j.hashCode());
                    System.out.println("FRAGMENT - 200:\n" + file_after.substring(j.getPos() - 200, j.getEndPos()));
                    System.out.println("\n");

                    System.out.println("STORE.GETDST(I):");
                    System.out.println(store.getDst(i).toTreeString());
                    System.out.println(store.getDst(i).getDepth());
                    System.out.println(store.getDst(i).getPos());
                    ITree parent2 = store.getDst(i).getParent().getParent();
                    //System.out.println("PARENTS TREE: \n" + store.getDst(i).getParent().getParent().getParent().getParent().getParent().toTreeString());
                    System.out.println("PARENTS POS: " + parent2.getPos() + " " + parent2.getEndPos());
                    System.out.println("PARENTS POS [2]: " + parent2.getParent().getPos() + " " + parent2.getParent().getEndPos());
                    System.out.println("PARENTS POS [3]: " + parent2.getParent().getParent().getPos() + " " + parent2.getParent().getParent().getEndPos());
                    System.out.println("PARENTS POS [4]: " + parent2.getParent().getParent().getParent().getPos() + " " + parent2.getParent().getParent().getParent().getEndPos());
                    System.out.println("HASHCODE: " + store.getDst(i).hashCode());
                    System.out.println("FRAGMENT - 200:\n" + file_after.substring(store.getDst(i).getPos() - 200, store.getDst(i).getEndPos()));
                    System.out.println("\n");
                }
                */

                steps++;

                if (store.getDst(i).toTreeString().equals(j.toTreeString())) {
                    //System.out.println("FINAL HASHCODES: " + store.getDst(i).hashCode() + " " + j.hashCode());
                    //System.out.println("FINAL MAPPING POSITIONS: " + store.getDst(i).getPos() + " " + store.getDst(i).getEndPos());
                    //System.out.println("FINAL J POSITIONS: " + j.getPos() + " " + j.getEndPos());
                    return new BeforeAfterNodes(i, store.getDst(i), nodeBefore, nodeAfter);
                }
            }

            cur_num ++;
        }


        /*for (ITree i = nodeBefore; i != null; i = i.getParent()) {
            for (ITree j = nodeAfter; j != null; j = j.getParent()) {
                if (store.has(i, j)) {
                    return new BeforeAfterNodes(i, j);
                }
            }
        }*/

        /*for (ITree i = nodeBefore; i != null; i = i.getParent()) {
            if (store.getDst(i) != null) {
                System.out.println("EQUALS");
                System.out.println(store.getDst(i).equals(nodeAfter));
                return new BeforeAfterNodes(i, store.getDst(i));
            }
        }*/

        //return null;

        return new BeforeAfterNodes(null, null, nodeBefore, nodeAfter);
    }




















    public static void main2(String[] args) throws IOException, GitAPIException {
        Run.initGenerators();

        TreeContext treeContext = null;

        File patternsDir = new File(PATTERNS_PATH);
        File[] size_dirs = Arrays.stream(patternsDir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(size_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        //int num_changes = 0;
        boolean if_break = false;

        for (File size_dir : size_dirs) {
            if (if_break) {
                break;
            }

            if (size_dir.getName().equals("40")) {
                break;
            }

            File[] id_dirs = Arrays.stream(size_dir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
            Arrays.sort(id_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

            for (File id_dir : id_dirs) {
                /*if (id_dir.getName().equals("16")) {
                    if_break = true;
                    break;
                }*/

                if (if_break) {
                    break;
                }

                File[] files = id_dir.listFiles();

                for (File file : files) {
                    if (if_break) {
                        break;
                    }

                    /*if (file.getName().startsWith("sampleChange3")) {
                        if_break = true;
                        break;
                    }*/

                    if (!file.getName().startsWith("sampleChange") || file.getName().equals("sampleChange.html")) {
                        continue;
                    }

                    System.out.println(size_dir.getName() + " " + id_dir.getName());
                    System.out.println("FILE: " + file.getName());

                    String content = readAllBytesJava7(file.getAbsolutePath());
                    int startInd = content.indexOf("<html><h3>") + "<html><h3>".length();
                    int endInd = content.indexOf("</h3><h3>");

                    String name = content.substring(startInd, endInd);
                    String[] parts = name.split(",");
                    String repoName = parts[parts.length - 1].trim();
                    String commitName = parts[0];
                    String fileName = parts[1];

                    GitConnector gc = new GitConnector(REPOS_PATH + "/" + repoName + "/.git");
                    System.out.println(REPOS_PATH + "/" + repoName + "/.git");
                    ArrayList<String> afterAndBefore = null;

                    if (gc.connect()) {
                        afterAndBefore = gc.getFileFromCommit(commitName, fileName);
                        gc.close();
                    } else {
                        System.out.println("GitConnector is not connected");
                    }

                    String afterContent = afterAndBefore.get(0).replace("\t", "    ");
                    String beforeContent = afterAndBefore.get(1).replace("\t", "    ");

                    File file_before = new File("tmp/file_before.java");
                    file_before.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
                        out.print(beforeContent);
                    }
                    ITree src = null;
                    TreeContext treeSrc = null;
                    try {
                        treeSrc = Generators.getInstance().getTree(file_before.getPath());
                        src = treeSrc.getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (treeContext == null) {
                        treeContext = treeSrc;
                    }

                    File file_after = new File("tmp/file_after.java");
                    file_after.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_after.getAbsolutePath())) {
                        out.print(afterContent);
                    }
                    ITree dst = null;
                    TreeContext treeDst = null;
                    try {
                        treeDst = Generators.getInstance().getTree(file_after.getPath());
                        dst = treeDst.getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    ArrayList<String> beforeAndAfterFragments = null;
                    beforeAndAfterFragments = getFragments(content);
                    String fragmentBefore = getOnlyBlackChanges1(beforeAndAfterFragments.get(0)).trim();
                    String fragmentAfter = getOnlyBlackChanges1(beforeAndAfterFragments.get(1)).trim();

                    //System.out.println("BEFORE");
                    //System.out.println(fragmentBefore);
                    //System.out.println("AFTER");
                    //System.out.println(fragmentAfter);

                    int start_ind = beforeContent.indexOf(fragmentBefore);
                    if (start_ind == -1) {
                        System.out.println("NOT FOUND before " + file.getName());
                        System.out.println(fragmentBefore);
                        //if_break = true;
                        //break;
                        continue;
                    }
                    int end_ind = start_ind + fragmentBefore.length();
                    ITree beforeNode = findMinCoverNode(src, start_ind, end_ind);
                    if (beforeNode == null) {
                        System.out.println("beforeNode is NULL " + file.getName());
                        //if_break = true;
                        //break;
                        continue;
                    }

                    int start_ind2 = afterContent.indexOf(fragmentAfter);
                    if (start_ind2 == -1) {
                        System.out.println("NOT FOUND after " + file.getName());
                        //if_break = true;
                        //break;
                        continue;
                    }
                    int end_ind2 = start_ind2 + fragmentAfter.length();
                    ITree afterNode = findMinCoverNode(dst, start_ind2, end_ind2);
                    if (afterNode == null) {
                        System.out.println("afterNode is NULL " + file.getName());
                        //if_break = true;
                        //break;
                        continue;
                    }

                    try (PrintWriter out = new PrintWriter("tmp/before_node")) {
                        out.print(beforeNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/after_node")) {
                        out.print(afterNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/src_tree")) {
                        out.print(src.toTreeString());
                    }
                    try (PrintWriter out = new PrintWriter("tmp/dst_tree")) {
                        out.print(dst.toTreeString());
                    }


                    //System.out.println("BEFORE DEPTH: " + beforeNode.getDepth());
                    //System.out.println("AFTER DEPTH: " + afterNode.getDepth());

                    // Match
                    Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
                    try {
                        m.match();
                    } catch (NullPointerException e) {
                        System.out.println("Cannot match: NullPointerException in m.match()");

                        //System.out.println(beforeNode.toTreeString());
                        //System.out.println("BEFORE DEPTH: " + beforeNode.getDepth());
                        //System.out.println("_________");
                        //System.out.println(afterNode.toTreeString());
                        //System.out.println("AFTER DEPTH: " + afterNode.getDepth());

                        if_break = true;
                        break;
                    }

                    //System.out.println(treeSrc.getTypeLabel(42));

                    BeforeAfterNodes nodes = getBeforeAfterNodes(m, start_ind, end_ind, start_ind2, end_ind2,
                            beforeContent, afterContent);

                    ITree nodeBefore = nodes.before;
                    ITree nodeAfter = nodes.after;

                    Matcher fragmentsMatcher = Matchers.getInstance().getMatcher(nodeBefore, nodeAfter);
                    fragmentsMatcher.match();

                    System.out.println(nodeBefore.toTreeString());
                    System.out.println("________________");
                    System.out.println(nodeAfter.toTreeString());
                    System.out.println("\n________ANOTHER REPRESENTATION________\n");
                    System.out.println(nodeBefore.toPrettyString(treeContext));
                    System.out.println("________________");
                    System.out.println(nodeAfter.toPrettyString(treeContext));

                    //ActionGenerator g = new ActionGenerator(nodeBefore, nodeAfter, m.getMappings());
                    ActionGenerator g = new ActionGenerator(nodeBefore, nodeAfter, fragmentsMatcher.getMappings());
                    g.generate();
                    List<Action> actions = g.getActions();

                    //TreeContext treeContext = new TreeContext();
                    //System.out.println(treeContext.getTypeLabel(42));

                    System.out.println("______________________________________");
                    System.out.println("ACTIONS SIZE");
                    System.out.println(actions.size());

                    if (actions.size() > NUM_ACTIONS_THRESHOLD) {
                        if_break = true;
                        break;
                        //continue;
                    }

                    try {
                        for (Action action : actions) {
                            /*System.out.println(action.getNode().toPrettyString(treeDst) + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.format(treeSrc));*/
                            System.out.println(action.format(treeSrc));
                            //System.out.println(action.getName() + " " + action.getNode().toShortString());
                        }
                    } catch (NullPointerException e) {
                        System.out.println("NullPointerException in printing actions");
                        if_break = true;
                        break;
                        //continue;
                    }

                    num_changes++;
                    addNgramsToSet(actions, size_dir.getName(), id_dir.getName(), file.getName(), false);

                    /*if (size_dir.getName().equals("3") && id_dir.getName().equals("1") && file.getName().equals("sampleChange3.html")) {
                        System.out.println(nodeBefore.toTreeString());
                        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        System.out.println(nodeAfter.toTreeString());
                        if_break = true;
                        break;
                    }*/
                }
            }

        }

        System.out.println("\n\n\nNUM CHANGES: " + num_changes);

        printHist(treeContext);
    }

    private static void checkingAllTree(ITree tree, Matcher m, String fileBefore, String fileAfter) {
        MappingStore store = m.getMappings();

        String treeAsStr = "\t\t\t\t\t\t\t\t32@@\n" +
                "\t\t\t\t\t\t\t\t\t32@@\n" +
                "\t\t\t\t\t\t\t\t\t\t42@@binary\n" +
                "\t\t\t\t\t\t\t\t\t\t32@@\n" +
                "\t\t\t\t\t\t\t\t\t\t\t42@@getTmpDir\n" +
                "\t\t\t\t\t\t\t\t\t42@@testBlockSize";
        treeAsStr = treeAsStr.replace("\t", "    ");


        String stripped = tree.toTreeString().trim()
                .replace(" ", "")
                .replace("\t", "")
                .replace("\n", "");

        if (stripped.endsWith("42@@testBlockSize") && stripped.contains("binary") && stripped.contains("getTmpDir")) {
            System.out.println("\n\nSTART:");
            System.out.println(treeAsStr);
            System.out.println("__________________________________DELIMITER__________________________________");
            System.out.println(tree.toTreeString());
            System.out.println("END\n\n");
        }

        String strippedPattern = treeAsStr
                .replace(" ", "")
                .replace("\t", "")
                .replace("\n", "");

        if (strippedPattern.equals(stripped)) {
            System.out.println("HASH: " + tree.getHash());
            System.out.println("HASHCODE: " + tree.hashCode());
            System.out.println("POSITIONS: " + tree.getPos() + " " + tree.getEndPos());
            System.out.println("FRAGMENT - 300:\n" + fileBefore.substring(tree.getPos() - 300, tree.getEndPos()));
            System.out.println("TREE:");
            System.out.println(tree.toTreeString());
            System.out.println("MAPPING HASHCODE: " + store.getDst(tree).hashCode());
            System.out.println("MAPPING POSITIONS: " + store.getDst(tree).getPos() + " " + store.getDst(tree).getEndPos());
            System.out.println("MAPPING FRAGMENT - 300:\n" + fileAfter.substring(store.getDst(tree).getPos() - 300, store.getDst(tree).getEndPos()));
            System.out.println("MAPPING:");
            System.out.println(store.getDst(tree).toTreeString());
        }

        int num_children = tree.getChildren().size();
        for (int i = 0; i < num_children; i++) {
            checkingAllTree(tree.getChild(i), m, fileBefore, fileAfter);
        }
    }


    public static String PATTERNS_PATH = "/Users/aliscafo/Downloads/CPatMiner-master 2/SemanticChangeGraphMiner/output/patterns/repos-hybrid/1";
    public static String REPOS_PATH = "/Volumes/Transcend/Alina/repos";
    //public static String REPOS_PATH = "/Volumes/Seagate/Alina/repos";

    public static void main1(String[] args) throws IOException, GitAPIException {
        Run.initGenerators();

        File patternsDir = new File(PATTERNS_PATH);
        File[] size_dirs = Arrays.stream(patternsDir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(size_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        int num_changes = 0;
        boolean if_break = false;

        for (File size_dir : size_dirs) {
            if (if_break) {
                break;
            }

            if (size_dir.getName().equals("4")) {
                break;
            }

            File[] id_dirs = Arrays.stream(size_dir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
            Arrays.sort(id_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

            for (File id_dir : id_dirs) {
                if (if_break) {
                    break;
                }

                System.out.println(size_dir.getName() + " " + id_dir.getName());
                File[] files = id_dir.listFiles();

                for (File file : files) {
                    if (if_break) {
                        break;
                    }

                    if (!file.getName().startsWith("sampleChange") || file.getName().equals("sampleChange.html")) {
                        continue;
                    }

                    num_changes++;

                    System.out.println("FILE: " + file.getName());

                    String content = readAllBytesJava7(file.getAbsolutePath());
                    int startInd = content.indexOf("<html><h3>") + "<html><h3>".length();
                    int endInd = content.indexOf("</h3><h3>");

                    String name = content.substring(startInd, endInd);
                    String[] parts = name.split(",");
                    String repoName = parts[parts.length - 1].trim();
                    String commitName = parts[0];
                    String fileName = parts[1];

                    GitConnector gc = new GitConnector(REPOS_PATH + "/" + repoName + "/.git");
                    System.out.println(REPOS_PATH + "/" + repoName + "/.git");
                    ArrayList<String> afterAndBefore = null;

                    if (gc.connect()) {
                        afterAndBefore = gc.getFileFromCommit(commitName, fileName);
                        gc.close();
                    } else {
                        System.out.println("GitConnector is not connected");
                    }

                    String afterContent = afterAndBefore.get(0).replace("\t", "    ");
                    String beforeContent = afterAndBefore.get(1).replace("\t", "    ");

                    File file_before = new File("tmp/file_before.java");
                    file_before.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
                        out.print(beforeContent);
                    }
                    ITree src = null;
                    try {
                        src = Generators.getInstance().getTree(file_before.getPath()).getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    File file_after = new File("tmp/file_after.java");
                    file_after.createNewFile();
                    try (PrintWriter out = new PrintWriter(file_after.getAbsolutePath())) {
                        out.print(afterContent);
                    }
                    ITree dst = null;
                    try {
                        dst = Generators.getInstance().getTree(file_after.getPath()).getRoot();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    ArrayList<String> beforeAndAfterFragments = null;
                    beforeAndAfterFragments = getFragments(content);
                    String fragmentBefore = getOnlyBlackChanges1(beforeAndAfterFragments.get(0));
                    String fragmentAfter = getOnlyBlackChanges1(beforeAndAfterFragments.get(1));

                    int start_ind = beforeContent.indexOf(fragmentBefore);
                    if (start_ind == -1) {
                        System.out.println("NOT FOUND before " + file.getName());
                        System.out.println(fragmentBefore);
                        if_break = true;
                        break;
                    }
                    int end_ind = start_ind + fragmentBefore.length();
                    ITree beforeNode = findMinCoverNode(src, start_ind, end_ind);
                    if (beforeNode == null) {
                        System.out.println("beforeNode is NULL " + file.getName());
                        if_break = true;
                        break;
                    }

                    int start_ind2 = afterContent.indexOf(fragmentAfter);
                    if (start_ind2 == -1) {
                        System.out.println("NOT FOUND after " + file.getName());
                        if_break = true;
                        break;
                    }
                    int end_ind2 = start_ind2 + fragmentAfter.length();
                    ITree afterNode = findMinCoverNode(dst, start_ind2, end_ind2);
                    if (afterNode == null) {
                        System.out.println("afterNode is NULL " + file.getName());
                        if_break = true;
                        break;
                    }

                    try (PrintWriter out = new PrintWriter("tmp/before_node")) {
                        out.print(beforeNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/after_node")) {
                        out.print(afterNode.toTreeString());
                    }


                    System.out.println("BEFORE DEPTH: " + beforeNode.getDepth());
                    System.out.println("AFTER DEPTH: " + afterNode.getDepth());

                    // Match
                    Matcher m = Matchers.getInstance().getMatcher(beforeNode, afterNode); // retrieve the default matcher
                    try {
                        m.match();
                    } catch (NullPointerException e) {
                        System.out.println("NullPointerException in m.match()");

                        //System.out.println(beforeNode.toTreeString());
                        System.out.println("BEFORE DEPTH: " + beforeNode.getDepth());
                        System.out.println("_________");
                        //System.out.println(afterNode.toTreeString());
                        System.out.println("AFTER DEPTH: " + afterNode.getDepth());

                        if_break = true;
                        break;
                    }


                    if (beforeNode.getDepth() == 7 && afterNode.getDepth() == 3) {
                        System.out.println(beforeNode.toTreeString());
                        System.out.println("_________");
                        System.out.println(afterNode.toTreeString());
                    }


                    /*
                    ActionGenerator g = new ActionGenerator(beforeNode, afterNode, m.getMappings());
                    g.generate();
                    List<Action> actions = g.getActions();

                    System.out.println("______________________________________");
                    try {
                        for (Action action : actions) {
                            System.out.println(action.getNode().toShortString() + " | " +
                                    action.getNode().getId() + " | " + action.getNode().getType() + " | " + action.toString());
                        }
                    } catch (NullPointerException e) {
                        System.out.println("NullPointerException in printing actions");
                        continue;
                    }

                    addNgramsToSet(actions);
                    */
                }
            }

        }

        //System.out.println(num_changes);

        //printHist();
    }

    static String mapWithContext(String action, TreeContext treeContext) {
        String str = action.toString();
        String[] tokens = str.split(" ");
        int n = tokens.length;
        List<String> result = new ArrayList<>();
        result.add(tokens[0]);

        for (int i = 1; i < n; i++) {
            String token = tokens[i];

            if (token.endsWith("@@")) {
                String[] splitted = token.split("@@");
                if (isNumeric(splitted[0])) {
                    int type = Integer.parseInt(splitted[0]);
                    result.add(treeContext.getTypeLabel(type) + "@@");
                }
            } else {
                result.add(token);
            }
        }

        return String.join(" ", result);
    }

    public static void printHist(TreeContext treeContext) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter("final_results.txt"));

        LinkedHashMap<String, Integer> sorted = ngrams_stats
                .entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .limit(80)
                .collect(
                        toMap(e -> e.getKey(), e -> e.getValue(), (e1, e2) -> e2,
                                LinkedHashMap::new));

        int num = 1;
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            String key = mapWithContext(entry.getKey(), treeContext);
            Integer value = entry.getValue();
            writer.write(num + ". " + key + " = " + value + "\n");
            num++;
        }

        writer.write("\nLOCATIONS AS SUBSEQ:\n");
        num = 1;
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            String key = entry.getKey();

            if (!ngrams_locations_as_sub.containsKey(key)) {
                continue;
            }

            ArrayList<String> locs = ngrams_locations_as_sub.get(key);
            writer.write(num + ". ");

            for (String loc : locs) {
                writer.write(loc + " | ");
            }
            writer.write("\n");
            num++;
        }

        writer.write("\nFULL LOCATIONS:\n");
        num = 1;
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            String key = entry.getKey();

            if (!ngrams_locations_full.containsKey(key)) {
                continue;
            }

            ArrayList<String> locs = ngrams_locations_full.get(key);
            writer.write(num + ". ");

            for (String loc : locs) {
                writer.write(loc + " | ");
            }
            writer.write("\n");
            num++;
        }

        writer.write("\nNUM CHANGES:\n");
        writer.write(num_changes);

        writer.close();
    }

    public static void saveHists(TreeContext treeContext) throws IOException {
        LinkedHashMap<String, Integer> sorted = ngrams_stats
                .entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                //.sorted(Map.Entry.comparingByKey())
                //.limit(80)
                .collect(
                        toMap(e -> e.getKey(), e -> e.getValue(), (e1, e2) -> e2,
                                LinkedHashMap::new));

        List<Map.Entry<String, Integer>> commonStats = new ArrayList<>(sorted.entrySet());
        Map<String, Integer> editScriptToInd = new HashMap<>();
        for (int i = 0; i < commonStats.size(); i++) {
            String editScript = commonStats.get(i).getKey();
            System.out.println("Putting editScript to editScriptToInd... " + i + "/" + commonStats.size());
            editScriptToInd.put(editScript, i);
        }

        File editScriptsFileUnmapped = new File(RESULTS_FOR_HIST_DIR + "/" + "edit_scripts_" + NGRAM + "grams_unmapped.txt");
        editScriptsFileUnmapped.getParentFile().mkdirs();
        editScriptsFileUnmapped.createNewFile();
        BufferedWriter editScriptsWriterUnmapped = new BufferedWriter(new FileWriter(editScriptsFileUnmapped.getAbsolutePath()));
        editScriptsWriterUnmapped.write("\nNUM CHANGES: " + num_changes + "\n");
        int num = 0;
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            System.out.println("Writing unmapped es to file... " + num);
            editScriptsWriterUnmapped.write(num + ") " + key + " = " + value + "\n");
            num++;
        }
        editScriptsWriterUnmapped.close();

        File editScriptsFileMapped = new File(RESULTS_FOR_HIST_DIR + "/" + "edit_scripts_" + NGRAM + "grams_mapped.txt");
        editScriptsFileMapped.getParentFile().mkdirs();
        editScriptsFileMapped.createNewFile();
        BufferedWriter editScriptsWriterMapped = new BufferedWriter(new FileWriter(editScriptsFileMapped.getAbsolutePath()));
        editScriptsWriterMapped.write("\nNUM CHANGES: " + num_changes + "\n");
        num = 0;
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            String key = mapWithContext(entry.getKey(), treeContext);
            Integer value = entry.getValue();
            System.out.println("Writing mapped es to file... " + num);
            editScriptsWriterMapped.write(num + ") " + key + " = " + value + "\n");
            num++;
        }
        editScriptsWriterMapped.close();




        File patternsListFile = new File(RESULTS_FOR_HIST_DIR + "/" + "all_patterns_to30.txt");
        patternsListFile.getParentFile().mkdirs();
        patternsListFile.createNewFile();
        BufferedWriter patternsListWriter = new BufferedWriter(new FileWriter(patternsListFile.getAbsolutePath()));
        patternsListWriter.write("NUM PATTERNS: " + all_patterns_list.size() + "\n");
        num = 0;
        for (String pattern : all_patterns_list) {
            System.out.println("Writing list of patterns to file... " + num);
            patternsListWriter.write(num + ") " + pattern + "\n");
            num++;
        }
        patternsListWriter.close();



        /*
        for (Map.Entry<String, Map<String, Integer>> entry : from_pattern_to_hist.entrySet()) {
            String pattern = entry.getKey();

            File pattern_hist_file = new File(RESULTS_FOR_HIST_DIR + "/" + pattern + "/" + NGRAM + "gram" + "/" + "hist.txt");
            pattern_hist_file.getParentFile().mkdirs();
            pattern_hist_file.createNewFile();
            BufferedWriter histWriter = new BufferedWriter(new FileWriter(pattern_hist_file.getAbsolutePath()));

            Map<String, Integer> hist = entry.getValue();

            //for (Map.Entry<String, Integer> commonStatsEntry: commonStats) {
            //    String ngram = commonStatsEntry.getKey();
            //    histWriter.write(hist.getOrDefault(ngram, 0) + " ");
            //}

            List<Pair<Integer, Integer>> sparseHist = new ArrayList<>();

            for (Map.Entry<String, Integer> histElem : hist.entrySet()) {
                Integer editScriptInd = editScriptToInd.getOrDefault(histElem.getKey(), -1);
                Integer height = histElem.getValue();
                sparseHist.add(new Pair<>(editScriptInd, height));
            }

            sparseHist.sort(Comparator.comparingInt(Pair::getFirst));

            System.out.println("Writing hist to file... Pattern: " + pattern);

            for (Pair pair : sparseHist) {
                histWriter.write(pair.getFirst() + " " + pair.getSecond() + "\n");
            }

            histWriter.close();
        }
        */




        /*
        for (Map.Entry<String, Map<String, Integer>> entry : from_pattern_to_hist.entrySet()) {
            String pattern = entry.getKey();

            File pattern_hist_file = new File(RESULTS_FOR_HIST_DIR + "/" + pattern + "/" + NGRAM + "gram" + "/" + "hist.txt");
            pattern_hist_file.getParentFile().mkdirs();
            pattern_hist_file.createNewFile();
            BufferedWriter histWriter = new BufferedWriter(new FileWriter(pattern_hist_file.getAbsolutePath()));

            Map<String, Integer> hist = entry.getValue();
            for (Map.Entry<String, Integer> commonStatsEntry: commonStats) {
                String ngram = commonStatsEntry.getKey();
                histWriter.write(hist.getOrDefault(ngram, 0) + " ");
            }

            histWriter.close();
        }
        */

        int processed = 0;

        for (Map.Entry<String, Map<String, Integer>> entry : from_sample_to_hist.entrySet()) {
            System.out.println("Processed: " + processed + "/" + from_sample_to_hist.entrySet().size());
            processed++;

            String sample = entry.getKey();
            String[] parts = sample.split(" ");
            String pattern = parts[0] + " " + parts[1];
            String sample_name = parts[2];

            File sample_hist_file = new File(RESULTS_FOR_HIST_DIR + "/" + pattern +
                    "/" + NGRAM + "gram" + "/" + sample_name + "_hist.txt");

            System.out.println("Sample: " + sample + " to file " + sample_hist_file.getAbsolutePath());

            sample_hist_file.getParentFile().mkdirs();
            sample_hist_file.createNewFile();
            BufferedWriter histWriter = new BufferedWriter(new FileWriter(sample_hist_file.getAbsolutePath()));

            Map<String, Integer> hist = entry.getValue();

            /*for (Map.Entry<String, Integer> commonStatsEntry: commonStats) {
                String ngram = commonStatsEntry.getKey();
                histWriter.write(hist.getOrDefault(ngram, 0) + " ");
            }*/

            List<Pair<Integer, Integer>> sparseHist = new ArrayList<>();

            for (Map.Entry<String, Integer> histElem : hist.entrySet()) {
                Integer editScriptInd = editScriptToInd.getOrDefault(histElem.getKey(), -1);
                Integer height = histElem.getValue();
                sparseHist.add(new Pair<>(editScriptInd, height));
            }

            sparseHist.sort(Comparator.comparingInt(Pair::getFirst));

            for (Pair pair : sparseHist) {
                histWriter.write(pair.getFirst() + " " + pair.getSecond() + "\n");
            }

            histWriter.close();
        }


/*
        for (Map.Entry<String, Map<String, Integer>> entry : from_edit_script_to_hist.entrySet()) {
            String seq = entry.getKey();

            File seq_hist_file = new File(RESULTS_FOR_HIST_DIR + "/" + seq + "/" + "hist.txt");
            seq_hist_file.getParentFile().mkdirs();
            seq_hist_file.createNewFile();
            BufferedWriter histWriter = new BufferedWriter(new FileWriter(seq_hist_file.getAbsolutePath()));

            Map<String, Integer> hist = entry.getValue();
            //editScriptsWriter.write(num + ") " + key + " = " + value + "\n");
            for (String pattern : all_patterns_list) {
                histWriter.write(hist.getOrDefault(pattern, 0) + " ");
            }

            histWriter.close();
        }*/
    }


    public static String clean(String action) {
        String str = action;
        String[] tokens = str.split(" ");
        int n = tokens.length;
        List<String> result = new ArrayList<>();
        result.add(tokens[0]);

        for (int i = 1; i < n; i++) {
            String token = tokens[i];

            if (token.contains("@@")) {
                String[] splitted = token.split("@@");

                if (splitted.length == 0)
                    continue;

                if (isNumeric(splitted[0])) {
                    result.add(splitted[0] + "@@");
                }
            } else {
                if (tokens[0].equals("UPD") || tokens[0].equals("DEL")) {
                    continue;
                }

                if (i == n - 1 || i == n - 2) {
                    result.add(token);
                }
            }
        }

        return String.join(" ", result);
    }

    //private static String ACTIONS_DIR_PATH = "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/saved_actions_new_way";

    public static void saveActionsToFiles(List<String> actions_raw_str, List<String> actions_str,
                                          String size_dir, String id_dir, String filename) throws IOException {
        filename = filename.substring(0, filename.length() - 5);

        File actionsFile = new File(ACTIONS_DIR_PATH + "/" + size_dir + "/" + id_dir + "/" + filename);
        actionsFile.getParentFile().mkdirs();
        actionsFile.createNewFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(actionsFile.getAbsolutePath()));
        for (String action : actions_str) {
            writer.write(action + "\n");
        }
        writer.close();

        File actionsFileRaw = new File(ACTIONS_DIR_PATH + "/" + size_dir + "/" + id_dir + "/" + filename + "_raw");
        actionsFileRaw.getParentFile().mkdirs();
        actionsFileRaw.createNewFile();
        BufferedWriter writerRaw = new BufferedWriter(new FileWriter(actionsFileRaw.getAbsolutePath()));
        for (String action : actions_raw_str) {
            writerRaw.write(action + "\n");
        }
        writerRaw.close();
    }

    public static List<String> getSavedActions(String size_dir, String id_dir, String filename) {
        filename = filename.substring(0, filename.length() - 5);
        File actionsFile = new File(ACTIONS_DIR_PATH + "/" + size_dir + "/" + id_dir + "/" + filename);

        if (!actionsFile.exists()) {
            return null;
        }

        String fileContent = readAllBytesJava7(actionsFile.getAbsolutePath());
        String[] actionStr = fileContent.split("\n");

        return new ArrayList<String>(Arrays.asList(actionStr));
    }

    public static void addNgramsToSet(List<Action> actions, String size_dir, String id_dir, String filename,
                                      boolean getSaved) throws IOException {
        //String location = size_dir + " " + id_dir + " " + filename;

        String pattern = size_dir + " " + id_dir;

        List<String> actions_str_raw = new ArrayList<>();
        List<String> actions_str = new ArrayList<>();

        // Saving raw and cleaned actions and getting cleaned actions (and cleaning later)
        if (getSaved) {
            actions_str = getSavedActions(size_dir, id_dir, filename);
            if (actions_str == null) {
                return;
            }
        } else {

            for (Action action : actions) {
                if (USE_PARENTS && (action.getName().equals("DEL") || action.getName().equals("UPD") )) {
                    actions_str_raw.add(action.toString() + " " + action.getNode().getParent().getType() + "@@");
                    actions_str.add(clean(action.toString()) + " " + action.getNode().getParent().getType() + "@@");
                } else {
                    actions_str_raw.add(action.toString());
                    actions_str.add(clean(action.toString()));
                }
            }

            saveActionsToFiles(actions_str_raw, actions_str, size_dir, id_dir, filename);
        }

        if (!CALCULATE_HISTS) {
            return;
        }

        /*List<String> actions_str = new ArrayList<>();
        // Cleaning
        for (String action_raw : actions_str_raw) {
            actions_str.add(clean(action_raw));
        }*/

        if (!all_patterns_list.contains(pattern)) {
            all_patterns_list.add(pattern);
        }

        int num_actions = actions_str.size();
        int n = NGRAM;

        while (n <= NGRAM) {
            int i = 0;

            // Adding small edit scripts
            if (i + n > num_actions && TREAT_SMALL_SCRIPTS_AS_NGRAM) {
                String seq = String.join(" ", actions_str.subList(i, num_actions));
                if (!ngrams_stats.containsKey(seq)) {
                    ngrams_stats.put(seq, 0);
                }
                int count = ngrams_stats.get(seq);
                ngrams_stats.replace(seq, count + 1);

                if (true) {
                    //Map<String, Integer> hist = from_pattern_to_hist.getOrDefault(pattern, new HashMap<>());
                    //hist.merge(seq, 1, Integer::sum);
                    //from_pattern_to_hist.put(pattern, hist);

                    String sample = pattern + " " + filename.substring(0, filename.length() - 5);
                    Map<String, Integer> histOfSample = from_sample_to_hist.getOrDefault(sample, new HashMap<>());
                    histOfSample.merge(seq, 1, Integer::sum);
                    from_sample_to_hist.put(sample, histOfSample);
                }

                if (consideredEditScripts.contains(seq)) {
                    Map<String, Integer> hist = from_edit_script_to_hist.getOrDefault(seq, new HashMap<>());
                    hist.merge(pattern, 1, Integer::sum);
                    from_edit_script_to_hist.put(seq, hist);
                }
            }

            while (i + n <= num_actions) {
                String seq = String.join(" ", actions_str.subList(i, i + n));
                if (!ngrams_stats.containsKey(seq)) {
                    ngrams_stats.put(seq, 0);
                }
                int count = ngrams_stats.get(seq);
                ngrams_stats.replace(seq, count + 1);

                //if (consideredPatterns.contains(pattern)) {
                if (true) {
                    //Map<String, Integer> hist = from_pattern_to_hist.getOrDefault(pattern, new HashMap<>());
                    //hist.merge(seq, 1, Integer::sum);
                    //from_pattern_to_hist.put(pattern, hist);

                    String sample = pattern + " " + filename.substring(0, filename.length() - 5);
                    Map<String, Integer> histOfSample = from_sample_to_hist.getOrDefault(sample, new HashMap<>());
                    histOfSample.merge(seq, 1, Integer::sum);
                    from_sample_to_hist.put(sample, histOfSample);
                }

                if (consideredEditScripts.contains(seq)) {
                    Map<String, Integer> hist = from_edit_script_to_hist.getOrDefault(seq, new HashMap<>());
                    hist.merge(pattern, 1, Integer::sum);
                    from_edit_script_to_hist.put(seq, hist);
                }
                /*if (i == 0 && n == num_actions) {
                    if (!ngrams_locations_full.containsKey(seq)) {
                        ngrams_locations_full.put(seq, new ArrayList<>());
                    }
                    ArrayList<String> locations = ngrams_locations_full.get(seq);
                    if (!locations.contains(location))
                        locations.add(location);
                    ngrams_locations_full.replace(seq, locations);
                } else {
                    if (!ngrams_locations_as_sub.containsKey(seq)) {
                        ngrams_locations_as_sub.put(seq, new ArrayList<>());
                    }
                    ArrayList<String> locations = ngrams_locations_as_sub.get(seq);
                    if (!locations.contains(location))
                        locations.add(location);
                    ngrams_locations_as_sub.replace(seq, locations);
                }*/

                i++;
            }

            n++;
        }
    }

    // Removes \n from start and end.
    public static String strip(String str, Character ch) {
        int i = 0;
        while (str.charAt(i) == ch) {
            i++;
        }

        int j = str.length() - 1;
        while (str.charAt(j) == ch) {
            j--;
        }

        return str.substring(i, j + 1);
    }

    private static ArrayList<String> getFragments(String content) {
        ArrayList<String> fragments = new ArrayList<>();

        int start1 = content.indexOf("Before Change</h3><pre><code class='java'>") +
                "Before Change</h3><pre><code class='java'>".length();
        int end1 = content.indexOf("</code></pre><h3>After Change");

        int start2 = content.indexOf("After Change</h3><pre><code class='java'>") +
                "After Change</h3><pre><code class='java'>".length();
        int end2 = content.indexOf("</code></pre>", start2);

        String before = content.substring(start1, end1)
                //.replace("<a id=\"change\">", "")
                //.replace("</a>", "")
                .replace("\t", "    ")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
        before = strip(before, '\n');

        String after = content.substring(start2, end2)
                //.replace("<a id=\"change\">", "")
                //.replace("</a>", "")
                .replace("\t", "    ")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
        after = strip(after, '\n');

        fragments.add(before);
        fragments.add(after);

        return fragments;
    }

    public static void mainExperiment2(String[] args) throws IOException, GitAPIException {
        GitConnector gc = new GitConnector(REPOS_PATH + "/" + "iddi/oocsi" + "/.git");
        ArrayList<String> afterAndBefore = null;

        if (gc.connect()) {
            afterAndBefore = gc.getFileFromCommit("f9110cc0aa89a7cd7f96dca6434d4e28e18f29a5",
                    "server/src/nl/tue/id/oocsi/server/services/SocketClient.java");
            gc.close();
        }

        //System.out.println(afterAndBefore.get(0));
        //System.out.println("__________________________________________");
        //System.out.println(afterAndBefore.get(1));

        String afterStr = afterAndBefore.get(0);
        String beforeStr = afterAndBefore.get(1);

        File file_before = new File("tmp/file_before.java");
        file_before.createNewFile();
        try (PrintWriter out = new PrintWriter(file_before.getAbsolutePath())) {
            out.print(beforeStr);
        }

        File file_after = new File("tmp/file_after.java");
        file_after.createNewFile();
        try (PrintWriter out = new PrintWriter(file_after.getAbsolutePath())) {
            out.print(afterStr);
        }


    }
}
