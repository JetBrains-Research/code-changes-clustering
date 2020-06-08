import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.Generators;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ForCheckingExamples {
    public static void main(String[] args) throws FileNotFoundException {
        Run.initGenerators();

        File fileBeforeSaved = new File("tmp/ExampleClassBefore.java");
        File fileAfterSaved = new File("tmp/ExampleClassAfter.java");

        ITree src = null;
        TreeContext treeSrc = null;
        try {
            treeSrc = Generators.getInstance().getTree(fileBeforeSaved.getPath());
            src = treeSrc.getRoot();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }


        ITree dst = null;
        TreeContext treeDst = null;
        try {
            treeDst = Generators.getInstance().getTree(fileAfterSaved.getPath());
            dst = treeDst.getRoot();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }


        try (PrintWriter out = new PrintWriter("tmp/src_tree_preorder" + "_for_example")) {
            List<ITree> srcList = new ArrayList<>();
            src.preOrder().forEach(srcList::add);
            for (ITree iTree : srcList) {
                out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                        iTree.toShortString() + "       -> " + iTree.getId());
            }
        }
        try (PrintWriter out = new PrintWriter("tmp/dst_tree_preorder" + "_for_example")) {
            List<ITree> dstList = new ArrayList<>();
            dst.preOrder().forEach(dstList::add);
            for (ITree iTree : dstList) {
                out.println(new String(new char[iTree.getDepth()]).replace('\0', '\t') +
                        iTree.toShortString() + "       -> " + iTree.getId());
            }
        }


        Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
        try {
            m.match();
        } catch (NullPointerException e) {
            System.out.println("Cannot match: NullPointerException in m.match()");
        }


        ActionGenerator gen = new ActionGenerator(src, dst, m.getMappings());
        gen.generate();
        List<Action> actions = gen.getActions();

        /*
        for (Action action : actions) {
            System.out.println(action.toString() + "       -> " + action.getNode().getId());
        }*/


        for (Action action : actions) {
            System.out.println(action.toString());
        }
    }
}
