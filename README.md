This folder contains a modified version of the [ENHSP planner](https://github.com/hstairs/enhsp) that includes an implementation of the Configuration-Aware Flow Estimation (CAFE) heuristic.

## Requirements

Java 15 ([Download](https://openjdk.java.net/projects/jdk/15/))

## Compilation, Running, and Execution

### Compilation
To compile the software, go to the root folder and run:
```bash
./compile
```
This will generate a JAR file in the enhsp-dist/ folder.

### Running the Planner
The planner can be executed from the root folder using:
```
java -jar enhsp-dist/enhsp.jar -o <domain_file> -f <problem_file>
```
- **<domain_file>**: path to the PDDL domain file
- **<problem_file>**: path to the PDDL problem file

### PDDL Instances
Sample domain and problem files are provided in the examples/ folder:
- Domains: **examples/domains/**
- Problems: **examples/problems/**

### Heuristics

```bash
# Run with hadd (default)
java -jar enhsp-dist/enhsp.jar -o <domain_file> -f <problem_file>
```

```bash
# Run with hmax
java -jar enhsp-dist/enhsp.jar -o <domain_file> -f <problem_file> -h hmax
```

```bash
# Run with CAFE
java -jar enhsp-dist/enhsp.jar -o <domain_file> -f <problem_file> -planner cafe
```

## Dependencies

- [Antlr 3.4](http://www.antlr3.org/download/antlr-3.4-complete.jar)
- [JGraphT](http://jgrapht.org)
- [ojAlgo v40](http://ojalgo.org)
- [JSON Simple](https://github.com/fangyidong/json-simple)
- [Apache Commons CLI](https://commons.apache.org/proper/commons-cli/)

