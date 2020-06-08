import math
import sys
import os
from tqdm import tqdm
import collections
import sklearn.cluster
import numpy as np

def get_intersections(hist1, hist2):
    len1 = len(hist1)
    len2 = len(hist2)
    intersections = []
    i = 0
    j = 0

    while i < len1 and j < len2:
        if hist1[i][0] < hist2[j][0]:
            i += 1
        elif hist1[i][0] == hist2[j][0]:
            intersections.append((hist1[i][0], hist1[i][1], hist2[j][1]))
            i += 1
            j += 1
        elif hist1[i][0] > hist2[j][0]:
            j += 1

    return intersections


def get_union_inds(hist1, hist2):
    len1 = len(hist1)
    len2 = len(hist2)
    union = []
    i = 0
    j = 0

    while i < len1 or j < len2:
        if i >= len1:
            union.append(hist2[j][0])
            j += 1
        elif j >= len2:
            union.append(hist1[i][0])
            i += 1
        elif hist1[i][0] < hist2[j][0]:
            union.append(hist1[i][0])
            i += 1
        elif hist1[i][0] == hist2[j][0]:
            union.append(hist1[i][0])
            i += 1
            j += 1
        elif hist1[i][0] > hist2[j][0]:
            union.append(hist2[j][0])
            j += 1

    return union


def jaccard_metric(hist1, hist2, hist_len=None):
    intersections = get_intersections(hist1, hist2)

    metric = 0

    for ind, v1, v2 in intersections:
        mx = max(v1, v2)
        mn = min(v1, v2)
        metric += mn / mx

    len_intersections = len(intersections)

    if (len_intersections != 0):
        metric = metric / len(intersections)
    else:
        metric = 0

    return 1 - metric


def canberra_metric(hist1, hist2, hist_len=None):
    metric = 0
    i = 0
    j = 0
    n = len(hist1)
    m = len(hist2)

    union_len = 0

    while i < n or j < m:
        if i >= n:
            metric += abs(hist2[j][1]) / abs(hist2[j][1])  # 1
            j += 1
        elif j >= m:
            metric += abs(hist1[i][1]) / abs(hist1[i][1])  # 1
            i += 1
        elif hist1[i][0] == hist2[j][0]:
            metric += abs(hist1[i][1] - hist2[j][1]) / (abs(hist1[i][1]) + abs(hist2[j][1]))
            i += 1
            j += 1
        elif hist1[i][0] < hist2[j][0]:
            metric += abs(hist1[i][1]) / abs(hist1[i][1])  # 1
            i += 1
        elif hist1[i][0] > hist2[j][0]:
            metric += abs(hist2[j][1]) / abs(hist2[j][1])  # 1
            j += 1

    union_len = len(get_union_inds(hist1, hist2))
    if union_len == 0:
        return 1

    return metric / union_len


def canberra_metric_optimized(hist1, hist2, hist_len=None):
    metric = 0
    i = 0
    j = 0
    n = len(hist1)
    m = len(hist2)

    union_len = 0

    while i < n or j < m:
        if i >= n:
            metric += 1.0
            j += 1
        elif j >= m:
            metric += 1.0
            i += 1
        elif hist1[i][0] == hist2[j][0]:
            metric += abs(hist1[i][1] - hist2[j][1]) / (abs(hist1[i][1]) + abs(hist2[j][1]))
            i += 1
            j += 1
        elif hist1[i][0] < hist2[j][0]:
            metric += 1.0
            i += 1
        elif hist1[i][0] > hist2[j][0]:
            metric += 1.0
            j += 1

        union_len += 1

    if union_len == 0:
        return 1

    return metric / union_len

def cos_distance(hist1, hist2, hist_len=None):
    intersections = get_intersections(hist1, hist2)

    top = 0

    for ind, v1, v2 in intersections:
        top += v1 * v2

    bottom1 = (sum([pair[1] ** 2 for pair in hist1]))
    bottom2 = (sum([pair[1] ** 2 for pair in hist2]))

    return 1 - abs(top / np.sqrt(bottom1 * bottom2))


def pearsons_correlation_mean(hist1, hist2, hist_len):
    union_len = len(get_union_inds(hist1, hist2))

    top = 0
    left = 0
    right = 0

    n = len(hist1)
    m = len(hist2)

    mean1 = sum([pair[1] for pair in hist1]) / hist_len
    mean2 = sum([pair[1] for pair in hist2]) / hist_len

    i = 0
    j = 0

    while i < n or j < m:
        if i >= n:
            top += (- mean1) * (hist2[j][1] - mean2)
            left += (- mean1) ** 2
            right += (hist2[j][1] - mean2) ** 2
            j += 1
        elif j >= m:
            top += (hist1[i][1] - mean1) * (- mean2)
            left += (hist1[i][1] - mean1) ** 2
            right += (- mean2) ** 2
            i += 1
        elif hist1[i][0] == hist2[j][0]:
            top += (hist1[i][1] - mean1) * (hist2[j][1] - mean2)
            left += (hist1[i][1] - mean1) ** 2
            right += (hist2[j][1] - mean2) ** 2
            i += 1
            j += 1
        elif hist1[i][0] < hist2[j][0]:
            top += (hist1[i][1] - mean1) * (- mean2)
            left += (hist1[i][1] - mean1) ** 2
            right += (- mean2) ** 2
            i += 1
        elif hist1[i][0] > hist2[j][0]:
            top += (- mean1) * (hist2[j][1] - mean2)
            left += (- mean1) ** 2
            right += (hist2[j][1] - mean2) ** 2
            j += 1

    bottom = math.sqrt(left * right)
    return 1 - top / bottom

def get_dists(hists, dist_metric, hist_len):
    n = len(hists)
    dists = np.zeros((n, n))

    for i in tqdm(range(n)):
        for j in range(n):
            if i <= j:
                break

            distance = dist_metric(hists[i], hists[j], hist_len)
            dists[i][j] = distance
            dists[j][i] = distance

    return dists


def get_hists(hists_path, dirs):
    hists = []

    for num in tqdm(dirs):
        hist_path = os.path.join(hists_path, num, "sparse_hist.txt")

        if os.path.exists(hist_path):
            hist_file = open(hist_path, "r")
            lines = hist_file.read().split("\n")
            hist_data = [(int(line.split(" ")[0]), int(line.split(" ")[1])) for line in lines if line != '']
            hists.append(hist_data)
            hist_file.close()
        else:
            hists.append([])

    return hists


def get_hists_len(hists_path):
    hists_len = None

    es_path = os.path.join(hists_path, "ngrams_ids.txt")
    es_file = open(es_path, "r")
    es_lines = es_file.read().split("\n")
    hists_len = int(es_lines[-2].split(" ")[0][:-1]) + 1
    es_file.close()

    #print(hists_len)

    return hists_len



if __name__ == "__main__":
    if len(sys.argv) != 5:
        print("Expected: cluster <path to code changes representations> <clustering algorithm> " +
              "<parameter for algorithm> <distance function>")
        sys.exit()

    hists_path = sys.argv[1]
    clustering_algorithm_str = sys.argv[2]
    clustering_algorithm = None
    parameter = float(sys.argv[3])
    distance_function_str = sys.argv[4]
    distance_function = None

    if clustering_algorithm_str == "dbscan":
        clustering_algorithm = sklearn.cluster.DBSCAN(eps=parameter, min_samples=2, metric='precomputed')
    elif clustering_algorithm_str == "hac_average":
        clustering_algorithm = sklearn.cluster.AgglomerativeClustering(n_clusters=None, affinity='precomputed',
                                            linkage="average", compute_full_tree=True, distance_threshold=parameter)
    elif clustering_algorithm_str == "hac_complete":
        clustering_algorithm = sklearn.cluster.AgglomerativeClustering(n_clusters=None, affinity='precomputed',
                                            linkage="complete", compute_full_tree=True, distance_threshold=parameter)
    else:
        print("Unknown clustering algorithm. Expected: dbscan/hac_average/hac_complete.")
        sys.exit()

    if distance_function_str == "jaccard":
        distance_function = jaccard_metric
    elif distance_function_str == "canberra":
        distance_function = canberra_metric_optimized
    elif distance_function_str == "cosine":
        distance_function = cos_distance
    elif distance_function_str == "pearson":
        distance_function = pearsons_correlation_mean

    all_dirs = [os.fsdecode(el) for el in os.listdir(hists_path)
                if os.path.isdir(os.path.join(hists_path, os.fsdecode(el)))]

    hists = get_hists(hists_path, all_dirs)
    hists_len = get_hists_len(hists_path)

    dists = get_dists(hists, distance_function, hists_len)
    clustering_algorithm.fit(dists)

    clusters = collections.defaultdict(list)

    for i in range(len(clustering_algorithm.labels_)):
        label = clustering_algorithm.labels_[i]
        clusters[label].append(all_dirs[i])

    results_file = open("clustering_results.txt", "w")

    results_file.write("Outliers:\n")
    results_file.write(str(clusters[-1]))
    results_file.write("\n\n")

    results_file.write("Clusters:\n\n")

    for cluster in clusters:
        if cluster == -1:
            continue
        results_file.write("Cluster " + str(cluster) + "\n")
        results_file.write(str(clusters[cluster]))
        results_file.write("\n\n")

    results_file.close()




