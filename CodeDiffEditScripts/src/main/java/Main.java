import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.Generators;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class Main {

    public static Map<String, Integer> ngrams_stats = new HashMap<>();

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

        List<ITree> children = node.getChildren();

        for (ITree child : children) {
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

    public static String PATTERNS_PATH = "/Users/aliscafo/Downloads/CPatMiner-master 2/SemanticChangeGraphMiner/output/patterns/repos-hybrid/1";
    public static String REPOS_PATH = "/Volumes/Transcend/Alina/repos";

    public static void main(String[] args) throws IOException, GitAPIException {
        Run.initGenerators();

        File patternsDir = new File(PATTERNS_PATH);
        File[] size_dirs = Arrays.stream(patternsDir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
        Arrays.sort(size_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        for (File size_dir : size_dirs) {
            if (size_dir.getName().equals("40")) {
                break;
            }

            File[] id_dirs = Arrays.stream(size_dir.listFiles()).filter(file -> isNumeric(file.getName())).toArray(File[]::new);
            Arrays.sort(id_dirs, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

            for (File id_dir : id_dirs) {
                System.out.println(size_dir.getName() + " " + id_dir.getName());
                File[] files = id_dir.listFiles();

                for (File file : files) {
                    if (!file.getName().startsWith("sampleChange") || file.getName().equals("sampleChange.html")) {
                        continue;
                    }

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
                    String fragmentBefore = beforeAndAfterFragments.get(0);
                    String fragmentAfter = beforeAndAfterFragments.get(1);

                    int start_ind = beforeContent.indexOf(fragmentBefore);
                    if (start_ind == -1) {
                        System.out.println("NOT FOUND before " + file.getName());
                        System.out.println(fragmentBefore);
                        continue;
                    }
                    int end_ind = start_ind + fragmentBefore.length();
                    ITree beforeNode = findMinCoverNode(src, start_ind, end_ind);
                    if (beforeNode == null) {
                        System.out.println("beforeNode is NULL " + file.getName());
                        return;
                    }

                    int start_ind2 = afterContent.indexOf(fragmentAfter);
                    if (start_ind2 == -1) {
                        System.out.println("NOT FOUND after " + file.getName());
                        continue;
                    }
                    int end_ind2 = start_ind2 + fragmentAfter.length();
                    ITree afterNode = findMinCoverNode(dst, start_ind2, end_ind2);
                    if (afterNode == null) {
                        System.out.println("afterNode is NULL " + file.getName());
                        return;
                    }

                    try (PrintWriter out = new PrintWriter("tmp/before_node")) {
                        out.print(beforeNode.toTreeString());
                    }

                    try (PrintWriter out = new PrintWriter("tmp/after_node")) {
                        out.print(afterNode.toTreeString());
                    }


                    // Match
                    Matcher m = Matchers.getInstance().getMatcher(beforeNode, afterNode); // retrieve the default matcher
                    try {
                        m.match();
                    } catch (NullPointerException e) {
                        System.out.println("NullPointerException in m.match()");
                        continue;
                    }
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
                }
            }

        }

        printHist();
    }

    public static void printHist() {
        LinkedHashMap<String, Integer> sorted = ngrams_stats
                .entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .limit(40)
                .collect(
                        toMap(e -> e.getKey(), e -> e.getValue(), (e1, e2) -> e2,
                                LinkedHashMap::new));

        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            System.out.println(key + " = " + value);
        }
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
                result.add(splitted[0] + "@@");
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

    public static void addNgramsToSet(List<Action> actions) {
        List<String> actions_str = new ArrayList<>();

        for (Action action : actions) {
            actions_str.add(clean(action.toString()));
        }

        int num_actions = actions.size();
        int n = 3;

        while (n <= num_actions && n <= 7) {
            int i = 0;

            while (i + n <= num_actions) {
                String seq = String.join(" ", actions_str.subList(i, i + n));
                if (!ngrams_stats.containsKey(seq)) {
                    ngrams_stats.put(seq, 0);
                }
                int count = ngrams_stats.get(seq);
                ngrams_stats.replace(seq, count + 1);

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
                .replace("<a id=\"change\">", "")
                .replace("</a>", "")
                .replace("\t", "")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
        before = strip(before);

        String after = content.substring(start2, end2)
                .replace("<a id=\"change\">", "")
                .replace("</a>", "")
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
