import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.Generators;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class Main {

    public static Map<String, Integer> ngrams_stats = new HashMap<>();
    public static Map<String, ArrayList<String>> ngrams_locations_as_sub = new HashMap<>();
    public static Map<String, ArrayList<String>> ngrams_locations_full = new HashMap<>();
    public static int num_changes = 0;
    public static int NUM_ACTIONS_THRESHOLD = 400;

    public static void dfs(ITree node) {
        List<ITree> children = node.getChildren();

        System.out.println(node.toShortString() + " " + node.getPos() + " " + node.getEndPos());

        for (ITree child : children) {
            System.out.println("Parent: " + node.toShortString() + " Child: " + child.toShortString());

            dfs(child);
        }
    }

    private static String readAllBytesJava7(String filePath)
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
    public static ITree findMinCoverNode(ITree node, int start, int end) {
        int node_start = node.getPos();
        int node_end = node.getEndPos();

        if (!(node_start <= start && end <= node_end)) {
            return null;
        }

        //List<ITree> children = node.getChildren();


        for (ITree child : node.getChildren()) {
            ITree res = findMinCoverNode(child, start, end);
            if (res != null) {
                return res;
            }
        }

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

    public static String getOnlyBlackChanges(String fragment) {
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

    public static void mainCheckGetOnlyBlackChanges(String[] args) {
        String s = "            ExcerptTailer sourceTailer = s.createTailer();\n" +
                "\n" +
                "            final RollingChronicleQueue t =\n" +
                "                    <a id=\"change\">binary(</a>target<a id=\"change\">)</a>\n" +
                "                            .<a id=\"change\">testBlockSize()</a>\n" +
                "                            .build();\n" +
                "\n" +
                "            ExcerptAppender appender = t.acquireAppender();";

        System.out.println(getOnlyBlackChanges(s));
    }

    static class BeforeAfterNodes {
        ITree before;
        ITree after;

        BeforeAfterNodes(ITree b, ITree a) {
            before = b;
            after = a;
        }
    }

    /*public static MappingStore getMappingStore(ITree nodeBefore, ITree nodeAfter, MappingStore store) {
        MappingStore newStore = new MappingStore();

        for
    }*/


    public static BeforeAfterNodes getBeforeAfterNodes(Matcher m, int start_ind_before, int end_ind_before,
                                                       int start_ind_after, int end_ind_after) {
        ITree nodeBeforeFile = m.getSrc();
        ITree nodeAfterFile = m.getDst();

        MappingStore store = m.getMappings();
        //MappingStore newStore = new MappingStore();

        ITree nodeBefore = findMinCoverNode(nodeBeforeFile, start_ind_before, end_ind_before);
        ITree nodeAfter = findMinCoverNode(nodeAfterFile, start_ind_after, end_ind_after);

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

        for (ITree i = nodeBefore; i != null; i = i.getParent()) {
            for (ITree j = nodeAfter; j != null; j = j.getParent()) {
                if (store.getDst(i) == null) {
                    continue;
                }

                if (store.getDst(i).toTreeString().equals(j.toTreeString())) {
                    return new BeforeAfterNodes(i, store.getDst(i));
                }
            }
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

        return null;
    }

    public static void main(String[] args) throws IOException, GitAPIException {
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

                    String afterContent = afterAndBefore.get(0).replace("\t", "");
                    String beforeContent = afterAndBefore.get(1).replace("\t", "");

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
                    String fragmentBefore = getOnlyBlackChanges(beforeAndAfterFragments.get(0)).trim();
                    String fragmentAfter = getOnlyBlackChanges(beforeAndAfterFragments.get(1)).trim();

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


                    //System.out.println("BEFORE DEPTH: " + beforeNode.getDepth());
                    //System.out.println("AFTER DEPTH: " + afterNode.getDepth());

                    // Match
                    Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
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

                    //System.out.println(treeSrc.getTypeLabel(42));

                    BeforeAfterNodes nodes = getBeforeAfterNodes(m, start_ind, end_ind, start_ind2, end_ind2);

                    ITree nodeBefore = nodes.before;
                    ITree nodeAfter = nodes.after;

                    Matcher fragmentsMatcher = Matchers.getInstance().getMatcher(nodeBefore, nodeAfter);
                    fragmentsMatcher.match();

                    //System.out.println(nodeBefore.toTreeString());
                    //System.out.println("________________");
                    //System.out.println(nodeAfter.toTreeString());

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
                        continue;
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
                        continue;
                    }

                    num_changes++;
                    addNgramsToSet(actions, size_dir.getName() + " " + id_dir.getName() + " " + file.getName());

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


    public static String PATTERNS_PATH = "/Users/aliscafo/Downloads/CPatMiner-master 2/SemanticChangeGraphMiner/output/patterns/repos-hybrid/1";
    public static String REPOS_PATH = "/Volumes/Transcend/Alina/repos";

    public static void mainMain(String[] args) throws IOException, GitAPIException {
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

                    String afterContent = afterAndBefore.get(0).replace("\t", "");
                    String beforeContent = afterAndBefore.get(1).replace("\t", "");

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
                    String fragmentBefore = getOnlyBlackChanges(beforeAndAfterFragments.get(0));
                    String fragmentAfter = getOnlyBlackChanges(beforeAndAfterFragments.get(1));

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

            if (token.contains("@@")) {
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

    public static String clean(String action) {
        String str = action.toString();
        String[] tokens = str.split(" ");
        int n = tokens.length;
        List<String> result = new ArrayList<>();
        result.add(tokens[0]);

        for (int i = 1; i < n; i++) {
            String token = tokens[i];

            if (token.contains("@@")) {
                String[] splitted = token.split("@@");
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

    public static void addNgramsToSet(List<Action> actions, String location) {
        List<String> actions_str = new ArrayList<>();

        for (Action action : actions) {
            actions_str.add(clean(action.toString()));
        }

        int num_actions = actions.size();
        int n = 4;

        while (n <= num_actions && n <= 15) {
            int i = 0;

            while (i + n <= num_actions) {
                String seq = String.join(" ", actions_str.subList(i, i + n));
                if (!ngrams_stats.containsKey(seq)) {
                    ngrams_stats.put(seq, 0);
                }
                int count = ngrams_stats.get(seq);
                ngrams_stats.replace(seq, count + 1);

                if (i == 0 && n == num_actions) {
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
                }

                i++;
            }

            n++;
        }
    }

    // Removes \n from start and end.
    public static String strip(String str) {
        int i = 0;
        while (str.charAt(i) == '\n') {
            i++;
        }

        int j = str.length() - 1;
        while (str.charAt(j) == '\n') {
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
                .replace("\t", "")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
        before = strip(before);

        String after = content.substring(start2, end2)
                //.replace("<a id=\"change\">", "")
                //.replace("</a>", "")
                .replace("\t", "")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
        after = strip(after);

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
