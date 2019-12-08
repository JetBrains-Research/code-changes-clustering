import os
import subprocess
from subprocess import call, run
import time
import nltk

dir_path = "/Users/aliscafo/Downloads/CPatMiner-master 2/patterns survey corpus 2017-03-03/survey corpus 2017-03-03/patterns"

def clean(str):
    lines_list = str.split("\n")

    start = -1
    counter = 0
    end = -1

    for line in lines_list:
        if '<a id="change">' in line or '</a>' in line:
            if start == -1:
                start = counter
            end = counter
        counter += 1

    #print(start, end)

    str = '\n'.join(lines_list[start:end + 1])
    str = str.strip("\n") + "\n"

    str = str.replace('<a id="change">', '')
    str = str.replace('</a>', '')
    return str

def process_diff(diff, filename):
    res_diff = ""

    diff = diff.strip("\n")
    diff = diff.replace("+++", "ppp").replace("---", "mmm")
    diff = diff.replace("before.txt", filename)
    diff = diff.replace("after.txt", filename)

    lines = diff.split("\n")[2:]

    for line in lines:
        stripped = line[1:].strip()
        if stripped.startswith("//") or (line.startswith("@@") and line.endswith("@@")) or (stripped.startswith("/*") and stripped.endswith("*/")):
            continue

        #print("BEFORE", line)
        raw_tokenized = nltk.word_tokenize(line)
        #print("AFTER", raw_tokenized)

        tokenized = []

        for raw_token in raw_tokenized:
            token = raw_token.replace("``", '"').replace("''", '"')
            token = " . ".join(token.split('.'))
            token = " / ".join(token.split('/'))
            token = " \\ ".join(token.split('\\'))
            token = " - ".join(token.split('-'))
            token = " : ".join(token.split(':'))
            token = token.strip()

            token = token.lower()

            tokenized.append(token)


        #print("RESULT", " ".join(tokenized), "\n")
        res_diff += " ".join(tokenized)
        res_diff += " <nl> "

    return res_diff


if __name__ == "__main__":
    res_dataset = ""
    num_added = 1

    dataset_id_to_pattern_id = ""

    dir = os.fsencode(dir_path)

    sorted_size_dirs = [int(os.fsdecode(el)) for el in os.listdir(dir) if os.fsdecode(el).isnumeric()]
    sorted_size_dirs.sort()

    for size_dir in sorted_size_dirs:
        size_dir_str = str(size_dir)
        sorted_id_dirs = [int(os.fsdecode(el)) for el in os.listdir(dir_path + os.sep + size_dir_str) if os.fsdecode(el).isnumeric()]
        sorted_id_dirs.sort()

        processed_num = 0

        for id_dir in sorted_id_dirs:
            if processed_num == 100:
                break
            processed_num += 1

            path = dir_path + os.sep + size_dir_str + os.sep + str(id_dir) + os.sep + "sampleChange.html"

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

            try:
                s1 = content.index("</h3><h3>Before Change</h3><pre><code class='java'>") + len("</h3><h3>Before Change</h3><pre><code class='java'>")
            except:
                print("NOT FOUND", size_dir_str, str(id_dir))
                print(content)

            e1 = content.index("</code></pre><h3>After Change</h3><pre><code class='java'>")
            s2 = e1 + len("</code></pre><h3>After Change</h3><pre><code class='java'>")
            e2 = content.index("</code></pre>", s2)

            before = clean(content[s1:e1].strip("\n") + "\n")
            after = clean(content[s2:e2].strip("\n") + "\n")

            #print(before)
            #print("_______________")
            #print(after)

            fb = open("before.txt", "w+")
            fb.write(before)
            fb.close()

            fa = open("after.txt", "w+")
            fa.write(after)
            fa.close()

            #call('git diff before.txt after.txt', shell=True)

            cmd = ['git', 'diff', 'before.txt', 'after.txt']

            result = run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
            #print("ERROR", result.stderr)
            #print("NUM", processed_num)

            res_diff = result.stdout

            #print(res_diff)
            final_diff = process_diff(res_diff, filename)

            if len(final_diff.split(" ")) < 100:
                #print(len(final_diff.split(" ")))
                res_dataset += final_diff + "\n"
                dataset_id_to_pattern_id += str(num_added) + ": " + size_dir_str + " " + str(id_dir) + "\n"
                num_added += 1

            '''
            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            o, e = proc.communicate()

            proc.wait()

            print(o.decode('ascii'))
            print(e.decode('ascii'))'''

            '''print("\n\n\nPAIR")
            print("_______________")
            print(before)
            print("_______________")
            print(after)
            print("_______________")
            '''

    out = open("dataset_lowercase_w_context.txt", "w+")
    out.write(res_dataset)
    out.close()

    mapping = open("mapping_w_context.txt", "w+")
    mapping.write(dataset_id_to_pattern_id)
    mapping.close()

    print(num_added)