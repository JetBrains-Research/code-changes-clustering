import os
import subprocess
from subprocess import call, run
import time
import nltk

dir_path = "/Users/aliscafo/Downloads/CPatMiner-master 2/patterns survey corpus 2017-03-03/survey corpus 2017-03-03/patterns"

if __name__ == "__main__":
    res_dataset = ""
    num_added = 1

    dataset_id_to_pattern_id = ""

    dir = os.fsencode(dir_path)

    sorted_size_dirs = [int(os.fsdecode(el)) for el in os.listdir(dir) if os.fsdecode(el).isnumeric()]
    sorted_size_dirs.sort()

    num_changes = 0

    for size_dir in sorted_size_dirs:
        size_dir_str = str(size_dir)
        sorted_id_dirs = [int(os.fsdecode(el)) for el in os.listdir(dir_path + os.sep + size_dir_str) if os.fsdecode(el).isnumeric()]
        sorted_id_dirs.sort()

        for id_dir in sorted_id_dirs:
            path = dir_path + os.sep + size_dir_str + os.sep + str(id_dir) + os.sep + "details.html"

            if not os.path.exists(path):
                continue

            with open(path, 'r', encoding="latin-1") as file:
                content = file.read()

            #print(id_dir)
            #print(content)
            #print("\n\n\n\n\n")

            f1 = content.index("<html><h3>") + len("<html><h3>")
            f2 = content.index("</h3><h3>")

            filename = content[f1:f2].replace("\n", "").split(",")[1]
            #print("FILENAME", filename)

            s1 = None

