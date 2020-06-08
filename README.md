# code-changes-clustering

Подход к представлению и кластеризации изменений кода, основанный на N-граммах сценариев редактирования.

## Сборка

Создание исполняемого JAR-файла:

```console
foo@bar:~$ mvn clean package
```

Исполняемый JAR-файл: target/CodeChangesRepresentation-1.0-SNAPSHOT-jar-with-dependencies.jar

## Использование

- **represent**

Построение представлений.

```console
foo@bar:~$ java -jar target/CodeChangesRepresentation-1.0-SNAPSHOT-jar-with-dependencies.jar represent <dataset path> <path for representations> <representation type> <N> <if use context> [<if save or use saved edit scripts>] [<path for edit scripts>]
```

| | |
|-|-|
| dataset path | Путь к директории с исходными изменениями кода. Каждое изменение расположено в отдельной директории. Изменения кода представлены в виде методов <<до>> (файл before.txt) и <<после>> (after.txt). |
| path for representations | Путь к директории, в которую будут сохранены итоговые представления. |
| representation type | Тип представления: _short_as_ngram_ (сценарии редактирования, меньшие N, считаются отдельными N-граммами) или _concat_ (конкатенация представлений от 1 до N). |
| N | Параметр N. |
| if use context | Использование контекста. true/false. |
| if save or use saved edit scripts | Сохранение сценариев редактирования или использование сохранённых (если директория не пуста) для оптимизации времени работы. |
| path for edit scripts | Путь для сохранения сценариев редактирования (или к сохранённым сценариям редактирования). |

- **cluster**

Кластеризация представлений.

```console
foo@bar:~$ python3 cluster <path to representations> <algorithm> <parameter> <distance function>
```

| | |
|-|-|
| path to representations | Путь к директории с полученными представлениями. |
| algorithm | Алгоритм кластеризации. dbscan/hac_average/hac_complete. |
| parameter | Параметр для алгоритма кластеризации. |
| distance function | Функция расстояния. jaccard/canberra/cosine/pearson. |

## Требования
- Java 8
- JDK 1.8
- Python 3

