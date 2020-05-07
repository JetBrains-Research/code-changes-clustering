import me.tongfei.progressbar.ProgressBar;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;


public class GetDists {

    private static String RESULTS_RAW_TUFANO = "/Volumes/Seagate/Alina/result_for_raw_tufano60k";
    private static String ACTIONS_PATH = "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/RawTufanoActions60k";
    private static String DATASET_PATH = "/Users/aliscafo/Documents/ALINA/WORK/SPbAU/thesis/CodeDiffEditScripts/RawTufanoDataset60k";

    static ArrayList<ArrayList<Pair<Integer, Integer>>> get_hists(List<String> validElems, String path, String gram) {
        ArrayList<ArrayList<Pair<Integer, Integer>>> hists = new ArrayList<>();

        ProgressBar pb = new ProgressBar("Get hists " + gram, validElems.size());
        pb.start();

        for (String elem : validElems) {
            String hist_path = path + "/" + elem + " " + elem + "/" + gram + "/" + "/sampleChange1_hist.txt";
            File hist_file = new File(hist_path);

            if (hist_file.exists()) {
                ArrayList<Pair<Integer, Integer>> hist = new ArrayList<>();

                String content = Main.readAllBytesJava7(hist_path);
                String[] lines = content.split("\n");

                for (String line : lines) {
                    if (line.equals(""))
                        continue;

                    String[] splitted = line.split(" ");
                    Pair<Integer, Integer> pair = new Pair<>(Integer.parseInt(splitted[0]),
                                                             Integer.parseInt(splitted[1]));
                    hist.add(pair);
                    //System.out.println("PAIR: " + pair.getFirst() + " " + pair.getSecond());
                }

                hists.add(hist);

            } else {
                hists.add(new ArrayList<>());
            }

            pb.step();
        }
        pb.stop();

        return hists;
    }

    static Integer get_hist_len(String path, String gram) {
        String es_path = path + "/" + "edit_scripts_" + gram + "s_mapped.txt";
        String[] lines = Main.readAllBytesJava7(es_path).split("\n");

        String tmp = lines[lines.length - 1].split(" ")[0];
        Integer hist_len = Integer.parseInt(tmp.substring(0, tmp.length() - 1)) + 1;

        return hist_len;
    }

    static Double canberra_matric(ArrayList<Pair<Integer, Integer>> hist1, ArrayList<Pair<Integer, Integer>> hist2) {
        Double metric = 0.0;
        int i = 0, j = 0;
        int n = hist1.size();
        int m = hist2.size();
        int union_len = 0;

        while (i < n || j < m) {
            if (i >= n) {
                metric += 1.0;
                j++;
            } else if (j >= m) {
                metric += 1.0;
                i++;
            } else if (hist1.get(i).getFirst().equals(hist2.get(j).getFirst())) {
                metric += 1.0 * Math.abs(hist1.get(i).getSecond() - hist2.get(j).getSecond()) /
                        (Math.abs(hist1.get(i).getSecond()) + Math.abs(hist2.get(j).getSecond()));
                i++;
                j++;
            } else if (hist1.get(i).getFirst() < hist2.get(j).getFirst()) {
                metric += 1.0;
                i++;
            } else if (hist1.get(i).getFirst() > hist2.get(j).getFirst()) {
                metric += 1.0;
                j++;
            }

            union_len++;
        }

        if (union_len == 0)
            return 1.0;

        return metric / union_len;
    }


    static ArrayList<ArrayList<Double>> get_dists(ArrayList<ArrayList<Pair<Integer, Integer>>> hists) {
        int n = hists.size();

        ArrayList<ArrayList<Double>> dists = new ArrayList();
        //Collections.nCopies(n, new ArrayList(Collections.nCopies(n, 0.0))).forEach((list) -> dists.add(new ArrayList(list)));

        ProgressBar pbAllocate = new ProgressBar("Allocating", n);
        pbAllocate.start();

        for (int i = 0; i < n; i++) {
            dists.add(new ArrayList(Collections.nCopies(n, 0.0)));
            pbAllocate.step();
        }
        pbAllocate.stop();


        ProgressBar pb = new ProgressBar("Get dists", n);
        pb.start();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i <= j)
                    break;

                Double distance = canberra_matric(hists.get(i), hists.get(j));
                dists.get(i).set(j, distance);
                dists.get(j).set(i, distance);
            }
            pb.step();
        }
        pb.stop();

        return dists;
    }


    public static void main(String[] args) throws IOException {

        int n = 54831;
        ArrayList<ArrayList<Double>> dists = new ArrayList();
        //Collections.nCopies(n, new ArrayList(Collections.nCopies(n, 0.0))).forEach((list) -> dists.add(new ArrayList(list)));

        ProgressBar pbAllocate = new ProgressBar("Allocating", n);
        pbAllocate.start();

        for (int i = 0; i < n; i++) {
            dists.add(new ArrayList(Collections.nCopies(n, 0.0)));
            pbAllocate.step();
        }
        pbAllocate.stop();


        /*
        File resultsDir = new File(RESULTS_RAW_TUFANO);
        String[] elems = Arrays.stream(resultsDir.listFiles()).filter(file -> file.isDirectory())
                .map(file -> file.getName().split(" ")[0]).toArray(String[]::new);
        Arrays.sort(elems, Comparator.comparingInt(Integer::parseInt));

        System.out.println(elems.length);

        List<String> validElems = new ArrayList<>();

        ProgressBar pb = new ProgressBar("Test", elems.length);
        pb.start();

        for (String elem : elems) {
            String es = Main.readAllBytesJava7( ACTIONS_PATH + "/" + elem + "/" + elem + "/sampleChange1");
            if (es.length() != 0)
                validElems.add(elem);

            pb.step();
        }
        pb.stop();

        System.out.println(validElems.size());

        //validElems = validElems.subList(0, 20);

        ArrayList<ArrayList<Pair<Integer, Integer>>> hists_raw_1gram = get_hists(validElems, RESULTS_RAW_TUFANO, "1gram");
        ArrayList<ArrayList<Pair<Integer, Integer>>> hists_raw_2gram = get_hists(validElems, RESULTS_RAW_TUFANO, "2gram");
        ArrayList<ArrayList<Pair<Integer, Integer>>> hists_raw_3gram = get_hists(validElems, RESULTS_RAW_TUFANO, "3gram");
        ArrayList<ArrayList<Pair<Integer, Integer>>> hists_raw_4gram = get_hists(validElems, RESULTS_RAW_TUFANO, "4gram");
        ArrayList<ArrayList<Pair<Integer, Integer>>> hists_raw_5gram = get_hists(validElems, RESULTS_RAW_TUFANO, "5gram");

        Integer hist_len_1gram_raw = get_hist_len(RESULTS_RAW_TUFANO, "1gram");
        Integer hist_len_2gram_raw = get_hist_len(RESULTS_RAW_TUFANO, "2gram");
        Integer hist_len_3gram_raw = get_hist_len(RESULTS_RAW_TUFANO, "3gram");
        Integer hist_len_4gram_raw = get_hist_len(RESULTS_RAW_TUFANO, "4gram");
        Integer hist_len_5gram_raw = get_hist_len(RESULTS_RAW_TUFANO, "5gram");

        System.out.println(hist_len_1gram_raw);
        System.out.println(hist_len_2gram_raw);
        System.out.println(hist_len_3gram_raw);
        System.out.println(hist_len_4gram_raw);
        System.out.println(hist_len_5gram_raw);

        ArrayList<ArrayList<Pair<Integer, Integer>>> concat_hists_to_5gram_raw = new ArrayList<>();
        int numChanges = hists_raw_1gram.size();

        ProgressBar pbConcat = new ProgressBar("Concatting", numChanges);
        pbConcat.start();

        for (int i = 0; i < numChanges; i++) {
            ArrayList<Pair<Integer, Integer>> concatHist = new ArrayList<>();

            concatHist.addAll(hists_raw_1gram.get(i));

            for (int j = 0; j < hists_raw_2gram.get(i).size(); j++) {
                Integer ind = hists_raw_2gram.get(i).get(j).getFirst();
                Integer amount = hists_raw_2gram.get(i).get(j).getSecond();
                concatHist.add(new Pair<>(ind + hist_len_1gram_raw, amount));
            }

            for (int j = 0; j < hists_raw_3gram.get(i).size(); j++) {
                Integer ind = hists_raw_3gram.get(i).get(j).getFirst();
                Integer amount = hists_raw_3gram.get(i).get(j).getSecond();
                concatHist.add(new Pair<>(ind + hist_len_1gram_raw + hist_len_2gram_raw, amount));
            }

            for (int j = 0; j < hists_raw_4gram.get(i).size(); j++) {
                Integer ind = hists_raw_4gram.get(i).get(j).getFirst();
                Integer amount = hists_raw_4gram.get(i).get(j).getSecond();
                concatHist.add(new Pair<>(ind + hist_len_1gram_raw + hist_len_2gram_raw + hist_len_3gram_raw, amount));
            }

            for (int j = 0; j < hists_raw_5gram.get(i).size(); j++) {
                Integer ind = hists_raw_5gram.get(i).get(j).getFirst();
                Integer amount = hists_raw_5gram.get(i).get(j).getSecond();
                concatHist.add(new Pair<>(ind + hist_len_1gram_raw + hist_len_2gram_raw +
                        hist_len_3gram_raw + hist_len_4gram_raw, amount));
            }

            concat_hists_to_5gram_raw.add(concatHist);

            pbConcat.step();
        }
        pbConcat.stop();


        ArrayList<ArrayList<Double>> dists_raw_to_5gram = get_dists(concat_hists_to_5gram_raw);

        DecimalFormat df = new DecimalFormat("#.########");
        df.setRoundingMode(RoundingMode.CEILING);

        String distsPath = "/Volumes/Seagate/Alina/dists_raw_tufano60k";
        BufferedWriter distsWriter = new BufferedWriter(new FileWriter(distsPath));

        int n = dists_raw_to_5gram.size();

        ProgressBar pbSave = new ProgressBar("Saving dists", n);
        pbSave.start();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i <= j)
                    break;

                distsWriter.write(df.format(dists_raw_to_5gram.get(i).get(j)) + " ");
            }

            distsWriter.write("\n");

            pbSave.step();
        }
        pbSave.stop();

        distsWriter.close();
        */

        /*
        System.out.println(dists_raw_to_5gram.get(0));
        System.out.println(dists_raw_to_5gram.get(1));
        System.out.println(dists_raw_to_5gram.get(2));
        System.out.println(dists_raw_to_5gram.get(3));
        System.out.println(dists_raw_to_5gram.get(4));
        System.out.println(dists_raw_to_5gram.get(5));
        System.out.println(dists_raw_to_5gram.get(6));
        System.out.println(dists_raw_to_5gram.get(7));
        System.out.println(dists_raw_to_5gram.get(8));
        System.out.println(dists_raw_to_5gram.get(9));
        System.out.println(dists_raw_to_5gram.get(10));
        System.out.println(dists_raw_to_5gram.get(11));
        System.out.println(dists_raw_to_5gram.get(12));
        System.out.println(dists_raw_to_5gram.get(13));
        System.out.println(dists_raw_to_5gram.get(14));
        System.out.println(dists_raw_to_5gram.get(15));
        System.out.println(dists_raw_to_5gram.get(16));
        System.out.println(dists_raw_to_5gram.get(17));
        System.out.println(dists_raw_to_5gram.get(18));
        System.out.println(dists_raw_to_5gram.get(19));
        */
    }
}
