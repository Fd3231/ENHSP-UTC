import com.carrotsearch.hppc.DoubleArrayList;
import com.google.common.collect.Sets.SetView;
import com.hstairs.ppmajal.conditions.BoolPredicate;
import com.hstairs.ppmajal.conditions.Condition;
import com.hstairs.ppmajal.conditions.PDDLObject;
import com.hstairs.ppmajal.domain.ActionParameter;
import com.hstairs.ppmajal.domain.PDDLDomain;
import com.hstairs.ppmajal.expressions.NumFluent;
import com.hstairs.ppmajal.expressions.PDDLNumber;
import com.hstairs.ppmajal.pddl.heuristics.PDDLHeuristic;
import com.hstairs.ppmajal.problem.PDDLObjects;
import com.hstairs.ppmajal.problem.PDDLProblem;
import com.hstairs.ppmajal.problem.PDDLSearchEngine;
import com.hstairs.ppmajal.problem.PDDLState;
import com.hstairs.ppmajal.problem.State;
import com.hstairs.ppmajal.search.SearchEngine;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.text.html.HTMLDocument.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.Pair;
import com.hstairs.ppmajal.search.SearchHeuristic;
import com.hstairs.ppmajal.transition.TransitionGround;

import models.Configuration;
import models.Flow;
import models.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
// import heuristic_bin.*;

public class ENHSP {
    private int plateau;
    private static final int PLATEAU = 10000;
    private Boolean exploration = false;
    private String domainFile;
    private String problemFile;
    private String searchEngineString;
    private String hw;
    private String heuristic = "aibr";
    private String gw;
    private boolean saving_json = false;
    private String deltaExecution;
    private float depthLimit;
    private String savePlan;
    private boolean printTrace;
    private String tieBreaking;
    private String planner;
    private String deltaHeuristic;
    private String deltaPlanning;
    private String deltaValidation;
    private boolean helpfulActionsPruning;
    private Integer numSubdomains;
    private SearchHeuristic heuristicFunction;
    private PDDLProblem problem;
    private boolean pddlPlus;
    private PDDLDomain domain;
    private PDDLDomain domainHeuristic;
    private PDDLProblem heuristicProblem;
    private long overallStart;
    private boolean copyOfTheProblem;
    private boolean anyTime;
    private long timeOut;
    private boolean aibrPreprocessing;
    private SearchHeuristic h;
    private long overallPlanningTime;
    private float endGValue;
    private boolean helpfulTransitions;
    private boolean internalValidation = false;
    private int planLength;
    private String redundantConstraints;
    private String groundingType;
    private boolean naiveGrounding;
    private boolean stopAfterGrounding;
    private boolean printEvents;
    private boolean sdac;
    private boolean onlyPlan;
    private boolean ignoreMetric;

    private Map<String, Integer> goals = new HashMap<>();
    private Map<String, Configuration> configurations = new HashMap<>();
    private Map<String, Stage> stages = new HashMap<>();
    private Map<String, Float> feeders = new HashMap<>();
    private Map<String, Float> linksCapacity = new HashMap<>();
    private Integer maxCounterValue = 0;
    private Map<String, Double> expectedEnter = new HashMap<>();
    private Map<String, Double> expectedExit = new HashMap<>();
    private Map<String, String> flows = new HashMap<>();

    public ENHSP(boolean copyProblem) {
        copyOfTheProblem = copyProblem;
    }
    
    public int getPlanLength() {
        return planLength;
    }
    
    public Pair<PDDLDomain, PDDLProblem> parseDomainProblem(String domainFile, String problemFile, String delta,
            PrintStream out) {
        try {
            final PDDLDomain localDomain = new PDDLDomain(domainFile);
            //domain.substituteEqualityConditions();
            pddlPlus = !localDomain.getProcessesSchema().isEmpty() || !localDomain.getEventsSchema().isEmpty();
            out.println("Domain parsed");
            final PDDLProblem localProblem = new PDDLProblem(problemFile, localDomain.getConstants(),
                    localDomain.getTypes(), localDomain, out, groundingType, sdac, ignoreMetric);
            if (!localDomain.getProcessesSchema().isEmpty()) {
                localProblem.setDeltaTimeVariable(delta);
            }
            //this second model is the one used in the heuristic. This can potentially be different from the one used in the execution model. Decoupling it
            //allows us to a have a finer control on the machine
            //the third one is the validation model, where, also in this case we test our plan against a potentially more accurate description
            out.println("Problem parsed");
            out.println("Grounding..");
            localProblem.prepareForSearch(aibrPreprocessing, stopAfterGrounding);
            if (stopAfterGrounding) {
                System.exit(1);
            }
            return Pair.of(localDomain, localProblem);
        } catch (Exception ex) {
            Logger.getLogger(ENHSP.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public void parsingDomainAndProblem(String[] args) {
        try {
            overallStart = System.currentTimeMillis();
            Pair<PDDLDomain, PDDLProblem> res = parseDomainProblem(domainFile, problemFile, deltaExecution, System.out);
            domain = res.getKey();
            problem = res.getRight();
            if (pddlPlus) {
                res = parseDomainProblem(domainFile, problemFile, deltaHeuristic, new PrintStream(new OutputStream() {
                    public void write(int b) {
                    }
                }));
                domainHeuristic = res.getKey();
                heuristicProblem = res.getRight();
                copyOfTheProblem = true;
            } else {
                heuristicProblem = problem;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void configurePlanner() {
        if (planner != null) {
            setPlanner();
        }
    }
    
    @SuppressWarnings("unchecked")
    public void preprocess() throws IOException { 

        // Parse configurations and stages
        PDDLObjects objects = problem.getProblemObjects();
        for(Object obj : objects.toArray()) {
            PDDLObject pddlObject = (PDDLObject) obj;
            String name = pddlObject.getName();
            String type = pddlObject.getType().getName();
            
            if (type.equals("configuration")) {
                this.configurations.put(name, new Configuration(type));
                continue;
            }
    
            if (type.equals("stage")) {
                this.stages.put(name, new Stage(type));
                continue;
            }
    
        }
    
        // Parse goals
        String goals = problem.getGoals().pddlPrint(aibrPreprocessing);
        Pattern pattern = Pattern.compile("\\(counter\\s+(\\S+?)\\).*?(-\\d+\\.?\\d*)");
        Matcher matcher = pattern.matcher(goals);
        while (matcher.find()) {
            String counterName = matcher.group(1);
            String value = matcher.group(2);
            Integer counterValue = (int)(Math.abs(Double.parseDouble(value)));
            this.goals.put(counterName, counterValue);
            if (counterValue > maxCounterValue) {
                maxCounterValue = counterValue;
            }
        }

        Map<NumFluent, PDDLNumber> map = problem.getInitNumFluentsValues();
        for (Map.Entry<NumFluent, PDDLNumber> entry : map.entrySet()) {
            NumFluent fluent = entry.getKey();
            PDDLNumber value = entry.getValue();
            
            String fluentName = fluent.getName();
            List<ActionParameter> terms = fluent.getTerms();
            Float fluentValue = value.getNumber();

            if (fluentName.equals("confgreentime")) {
                String stage = terms.get(0).toString().split(" ")[0];
                String configuration = terms.get(1).toString().split(" ")[0];
                if (this.configurations.get(configuration).getStages().get(stage) == null) {
                    this.configurations.get(configuration).addStage(stage);
                }
                this.configurations.get(configuration).addGreentime(stage, fluentValue.intValue());
                continue;
            }

            if (fluentName.equals("interlimit")) {
                String stage = terms.get(0).toString().split(" ")[0];
                this.stages.get(stage).setInterlimit(fluentValue.intValue());
                continue;
            }

            if (fluentName.equals("contains")) {
                String junction = terms.get(0).toString().split(" ")[0];
                String stage = terms.get(1).toString().split(" ")[0];
                this.stages.get(stage).setJuncId(junction);
                continue;
            }


            if (fluentName.equals("capacity")) {
                String link = terms.get(0).toString().split(" ")[0];
                this.linksCapacity.put(link, fluentValue);
                continue;
            }

            if (fluentName.equals("turnrate")) {
                String stage = terms.get(0).toString().split(" ")[0];
                String sourceLink = terms.get(1).toString().split(" ")[0];
                String targetLink = terms.get(2).toString().split(" ")[0];
                if (stage.equals("fake")) {
                    this.feeders.put(targetLink, fluentValue);
                } else {
                    this.stages.get(stage).addFlow(new Flow(sourceLink, targetLink, fluentValue));
                    this.flows.putIfAbsent(sourceLink, targetLink);
                }
                continue;
            }
        }

        Map<BoolPredicate, Boolean> boolMap = problem.getInitBoolFluentsValues();
        for (Map.Entry<BoolPredicate, Boolean> entry : boolMap.entrySet()) {
            BoolPredicate predicate = entry.getKey();

            String predicateName = predicate.getPredicateName();
            List<ActionParameter> terms =  predicate.getTerms();
            
            if (predicateName.equals("availableconf")) {
                String junction = terms.get(0).toString().split(" ")[0];
                String configuration = terms.get(1).toString().split(" ")[0];
                this.configurations.get(configuration).setJuncId(junction);
                continue;

            }

            if (predicateName.equals("endcycle")) {
                String stage = terms.get(1).toString().split(" ")[0];
                this.stages.get(stage).setEndcycle(true);
                continue;

            }

        }

    }

    public void planning() {

        try {
            printStats();
            setHeuristic();
            do {
                LinkedList<?> sp = search();
                if (sp == null) {
                    return;
                }
                depthLimit = endGValue;
                if (anyTime) {
                    System.out.println(
                            "NEW COST ==================================================================================>"
                                    + depthLimit);
                }
                sp = null;
                System.gc();
            } while (anyTime);
        } catch (Exception ex) {
            Logger.getLogger(ENHSP.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void parseInput(String[] args) {
        Options options = new Options();
        options.addRequiredOption("o", "domain", true, "PDDL domain file");
        options.addRequiredOption("f", "problem", true, "PDDL problem file");
        options.addOption("planner", true,
                "Fast Preconfgured Planner. For available options look into the code. This overrides all other parameters but domain and problem specs.");
        options.addOption("h", true, "heuristic: options (default is AIBR):\n"
                + "aibr, Additive Interval Based relaxation heuristic\n"
                + "hadd, Additive version of subgoaling heuristic\n"
                + "hradd, Additive version of subgoaling heuristic plus redundant constraints\n"
                + "hmax, Hmax for Numeric Planning\n"
                + "hrmax, Hmax for Numeric Planning with redundant constraints\n"
                + "hmrp, heuristic based on MRP extraction\n"
                + "blcost, goal sensitive heuristic (1 to non goal-states, 0 to goal-states)"
                + "blind, full blind heuristic (0 to all states)");
        options.addOption("s", true, "allows to select search strategy (default is WAStar):\n"
                + "gbfs, Greedy Best First Search (f(n) = h(n))\n"
                + "WAStar, WA* (f(n) = g(n) + h_w*h(n))\n"
                + "wa_star_4, WA* (f(n) = g(n) + 4*h(n))\n");
        options.addOption("ties", true, "tie-breaking (default is arbitrary): larger_g, smaller_g, arbitrary");
        options.addOption("dp", "delta_planning", true, "planning decision executionDelta: float");
        options.addOption("de", "delta_execuction", true, "planning execution executionDelta: float");
        options.addOption("dh", "delta_heuristic", true, "planning heuristic executionDelta: float");
        options.addOption("dv", "delta_validation", true, "validation executionDelta: float");
        options.addOption("d", "delta", true,
                "Override other delta_<planning,execuction,validation,heuristic> configurations: float");
        options.addOption("epsilon", true, "epsilon separation: float");
        options.addOption("wg", true, "g-values weight: float");
        options.addOption("wh", true, "h-values weight: float");
        options.addOption("sjr", false, "save state space explored in json file");
        options.addOption("ha", "helpful-actions", true, "activate helpful actions pruning");
        options.addOption("pe", "print-events-plan", false, "activate printing of events");

        options.addOption("ht", "helpful-transitions", true, "activate up-to-macro actions");
        options.addOption("sp", true, "Save plan. Argument is filename");
        options.addOption("pt", false, "print state trajectory (Experimental)");
        //        options.addOption("im", false, "Ignore Metric in the heuristic");
        options.addOption("dap", false, "Disable Aibr Preprocessing");
        options.addOption("red", "redundant_constraints", true,
                "Choose mechanism for redundant constraints generation among, "
                        + "no, brute and smart. No redundant constraints generation is the default");
        options.addOption("gro", "grounding", true,
                "Activate grounding via internal mechanism, fd or metricff or internal or naive (default is internal)");

        options.addOption("dl", true, "bound on plan-cost: float (Experimental)");
        options.addOption("k", true, "maximal number of subdomains. This works in combination with haddabs: integer");
        options.addOption("anytime", false,
                "Run in anytime modality. Incrementally tries to find an upper bound. Does not stop until the user decides so");
        options.addOption("timeout", true, "Timeout for anytime modality");
        options.addOption("stopgro", false, "Stop After Grounding");
        options.addOption("ival", false, "Internal Validation");
        options.addOption("sdac", false, "Activate State Dependent Action Cost (Very Experimental!)");
        options.addOption("onlyplan", false, "Print only the plan without waiting");

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            domainFile = cmd.getOptionValue("o");
            problemFile = cmd.getOptionValue("f");
            planner = cmd.getOptionValue("planner");
            heuristic = cmd.getOptionValue("h");
            if (heuristic == null) {
                heuristic = "hadd";
            }
            searchEngineString = cmd.getOptionValue("s");
            if (searchEngineString == null) {
                searchEngineString = "gbfs";
            }
            tieBreaking = cmd.getOptionValue("ties");
            deltaPlanning = cmd.getOptionValue("dp");
            if (deltaPlanning == null) {
                deltaPlanning = "1.0";
            }
            String optionValue = cmd.getOptionValue("red");
            if (optionValue == null) {
                redundantConstraints = "no";
            } else {
                redundantConstraints = optionValue;
            }
            optionValue = cmd.getOptionValue("gro");
            if (optionValue != null) {
                groundingType = optionValue;
            } else {
                groundingType = "internal";
            }
            internalValidation = cmd.hasOption("ival");

            deltaExecution = cmd.getOptionValue("de");
            if (deltaExecution == null) {
                deltaExecution = "1.0";
            }
            deltaHeuristic = cmd.getOptionValue("dh");
            if (deltaHeuristic == null) {
                deltaHeuristic = "1.0";
            }
            deltaValidation = cmd.getOptionValue("dv");
            if (deltaValidation == null) {
                deltaValidation = "1";
            }
            String temp = cmd.getOptionValue("dl");
            if (temp != null) {
                depthLimit = Float.parseFloat(temp);
            } else {
                depthLimit = Float.NaN;
            }

            String timeOutString = cmd.getOptionValue("timeout");
            if (timeOutString != null) {
                timeOut = Long.parseLong(timeOutString) * 1000;
            } else {
                timeOut = Long.MAX_VALUE;
            }

            String delta = cmd.getOptionValue("delta");
            if (delta != null) {
                deltaHeuristic = delta;
                deltaValidation = delta;
                deltaPlanning = delta;
                deltaExecution = delta;
            }

            String k = cmd.getOptionValue("k");
            if (k != null) {
                numSubdomains = Integer.parseInt(k);
            } else {
                numSubdomains = 2;
            }

            gw = cmd.getOptionValue("wg");
            hw = cmd.getOptionValue("wh");
            saving_json = cmd.hasOption("sjr");
            sdac = cmd.hasOption("sdac");
            helpfulActionsPruning = cmd.getOptionValue("ha") != null && "true".equals(cmd.getOptionValue("ha"));
            printEvents = cmd.hasOption("pe");
            printTrace = cmd.hasOption("pt");
            savePlan = cmd.getOptionValue("sp");
            onlyPlan = cmd.hasOption("onlyplan");
            anyTime = cmd.hasOption("anytime");
            aibrPreprocessing = !cmd.hasOption("dap");
            stopAfterGrounding = cmd.hasOption("stopgro");
            helpfulTransitions = cmd.getOptionValue("ht") != null && "true".equals(cmd.getOptionValue("ht"));
            ignoreMetric = cmd.hasOption("im");
        } catch (ParseException exp) {
            //            Logger.getLogger(ENHSP.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("enhsp", options);
            System.exit(-1);
        }

    }

    /**
     * @return the problem
     */
    public PDDLProblem getProblem() {
        return problem;
    }

    public void printStats() {
        //        System.out.println("Grounding and Simplification finished");
        System.out.println("|A|:" + getProblem().getActions().size());
        System.out.println("|P|:" + getProblem().getProcessesSet().size());
        System.out.println("|E|:" + getProblem().getEventsSet().size());
        if (pddlPlus) {
            System.out.println("Delta time heuristic model:" + deltaHeuristic);
            System.out.println("Delta time planning model:" + deltaPlanning);
            System.out.println("Delta time search-execution model:" + deltaExecution);
            System.out.println("Delta time validation model:" + deltaValidation);
        }
    }

    private void setPlanner() {
        helpfulTransitions = false;
        helpfulActionsPruning = false;
        tieBreaking = "arbitrary";
        switch (planner) {
            case "sat-hmrp":
                heuristic = "hmrp";
                searchEngineString = "gbfs";
                tieBreaking = "arbitrary";
                break;
            case "sat-hmrph":
                heuristic = "hmrp";
                helpfulActionsPruning = true;
                searchEngineString = "gbfs";
                tieBreaking = "arbitrary";
                break;
            case "sat-hmrphj":
                heuristic = "hmrp";
                helpfulActionsPruning = true;
                helpfulTransitions = true;
                searchEngineString = "gbfs";
                tieBreaking = "arbitrary";
                break;
            case "sat-hmrpff":
                heuristic = "hmrp";
                helpfulActionsPruning = false;
                redundantConstraints = "brute";
                helpfulTransitions = false;
                searchEngineString = "gbfs";
                tieBreaking = "arbitrary";
                break;
            case "sat-hadd":
                heuristic = "hadd";
                searchEngineString = "gbfs";
                tieBreaking = "smaller_g";
                break;
            case "sat-aibr":
                heuristic = "aibr";
                searchEngineString = "WAStar";
                tieBreaking = "arbitrary";
                break;
            case "sat-hradd":
                heuristic = "hradd";
                searchEngineString = "gbfs";
                tieBreaking = "smaller_g";
                break;
            case "opt-hmax":
                heuristic = "hmax";
                searchEngineString = "WAStar";
                tieBreaking = "larger_g";
                break;
            case "opt-hlm":
                heuristic = "hlm-lp";
                searchEngineString = "WAStar";
                tieBreaking = "larger_g";
                break;
            case "opt-hlmrd":
                heuristic = "hlm-lp";
                redundantConstraints = "brute";
                searchEngineString = "WAStar";
                tieBreaking = "larger_g";
                break;
            case "opt-hrmax":
                heuristic = "hrmax";
                searchEngineString = "WAStar";
                tieBreaking = "larger_g";
                break;
            case "opt-blind":
                heuristic = "blind";
                searchEngineString = "WAStar";
                tieBreaking = "larger_g";
                aibrPreprocessing = false;
                break;
            case "sat-blind":
                heuristic = "blind";
                searchEngineString = "gbfs";
                tieBreaking = "larger_g";
                aibrPreprocessing = false;
                break;
            case "cafe":
                heuristic = "cafe";
                searchEngineString = "gbfs";
                tieBreaking = "larger_g";
                aibrPreprocessing = false;
                break;
            default:
                System.out.println(
                        "! ====== ! Warning: Unknown planner configuration. Going with default: gbfs with hadd ! ====== !");
                heuristic = "hadd";
                searchEngineString = "gbfs";
                tieBreaking = "smaller_g";
                break;
        }

    }

    private void setHeuristic() {

        if (heuristic.equals("cafe")) {
            try {
                preprocess();
            } catch (IOException e) {
                e.printStackTrace();
            }
            h = new CafeHeuristic();
        } else {
            h = PDDLHeuristic.getHeuristic(heuristic, heuristicProblem, redundantConstraints, helpfulActionsPruning,
                    helpfulTransitions);
        }
    }

    private LinkedList<Pair<BigDecimal, Object>> search() throws Exception {

        LinkedList<Pair<BigDecimal, Object>> rawPlan = null;//raw list of actions returned by the search strategies

        final PDDLSearchEngine searchEngine = new PDDLSearchEngine(problem, h); //manager of the search strategies
        Runtime.getRuntime().addShutdownHook(new Thread() {//this is to save json also when the planner is interrupted
            @Override
            public void run() {
                if (saving_json) {
                    searchEngine.searchSpaceHandle.print_json(getProblem().getPddlFileReference() + ".sp_log");
                }
            }
        });
        if (pddlPlus) {
            searchEngine.executionDelta = new BigDecimal(deltaExecution);
            searchEngine.processes = true;
            searchEngine.planningDelta = new BigDecimal(deltaPlanning);
        }

        searchEngine.saveSearchTreeAsJson = saving_json;

        if (tieBreaking != null) {
            switch (tieBreaking) {
                case "smaller_g":
                    searchEngine.tbRule = SearchEngine.TieBreaking.LOWERG;
                    break;
                case "larger_g":
                    searchEngine.tbRule = SearchEngine.TieBreaking.HIGHERG;
                    break;
                default:
                    System.out.println("Wrong setting for break-ties. Arbitrary tie breaking");
                    break;
            }
        } else {//the following is the arbitrary setting
            tieBreaking = "arbitrary";
            searchEngine.tbRule = SearchEngine.TieBreaking.ARBITRARY;

        }

        if (hw != null) {
            searchEngine.setWH(Float.parseFloat(hw));
            System.out.println("w_h set to be " + hw);
        } else {
            searchEngine.setWH(1);
        }
        if (gw != null) {
            searchEngine.setWG(Float.parseFloat(gw));
            System.out.println("g_h set to be " + gw);
        } else {
            searchEngine.setWG(1);

        }

        if (depthLimit != Float.NaN) {
            searchEngine.depthLimit = depthLimit;
            System.out.println("Setting horizon to:" + depthLimit);
        } else {
            searchEngine.depthLimit = Float.POSITIVE_INFINITY;
        }

        System.out.println("Helpful Action Pruning Activated");
        searchEngine.helpfulActionsPruning = helpfulActionsPruning;

        if ("WAStar".equals(searchEngineString)) {
            System.out.println("Running WA-STAR");
            rawPlan = searchEngine.WAStar(getProblem(), timeOut);
        } else if ("wa_star_4".equals(searchEngineString)) {
            System.out.println("Running greedy WA-STAR with hw = 4");
            searchEngine.setWH(4);
            rawPlan = searchEngine.WAStar();
        } else if ("gbfs".equals(searchEngineString)) {
            System.out.println("Running Greedy Best First Search");
            if (gw == null) {
                searchEngine.setWG(0);
            }
            rawPlan = searchEngine.greedy_best_first_search(getProblem(), timeOut);
        } else if ("gbfs_ha".equals(searchEngineString)) {
            System.out.println("Running Greedy Best First Search with Helpful Actions");
            if (gw == null) {
                searchEngine.setWG(0);
            }
            rawPlan = searchEngine.greedy_best_first_search(getProblem(), timeOut);
        } else if ("ida".equals(searchEngineString)) {
            System.out.println("Running IDAStar");
            rawPlan = searchEngine.idastar(getProblem(), true);
        } else if ("ucs".equals(searchEngineString)) {
            System.out.println("Running Pure Uniform Cost Search");
            rawPlan = searchEngine.UCS(getProblem());
        } else {
            throw new RuntimeException("Search strategy is not correct");
        }
        endGValue = searchEngine.currentG;

        overallPlanningTime = (System.currentTimeMillis() - overallStart);
        //SimplePlan sp = validate(searchEngine, rawPlan);
        //        if (savePlan != null) {
        //            enhspUtil.ENHSPUtils.savePlan(new LinkedList<Pair<Float,TransitionGround>>(), problem, savePlan);
        //        }
        boolean valid = true;
        if (printTrace) {
            String fileName = getProblem().getPddlFileReference() + "_search_" + searchEngineString + "_h_" + heuristic
                    + "_break_ties_" + tieBreaking + ".npt";
            valid = searchEngine.validate(rawPlan, new BigDecimal(this.deltaExecution), new BigDecimal(deltaExecution),
                    fileName);
            System.out.println("Numeric Plan Trace saved to " + fileName);
        } else if (internalValidation) {
            Pair<PDDLDomain, PDDLProblem> res = parseDomainProblem(domainFile, problemFile, deltaValidation,
                    new PrintStream(new OutputStream() {
                        public void write(int b) {
                        }
                    }));
            PDDLSearchEngine validator = new PDDLSearchEngine(res.getRight(), h);
            valid = validator.validate(rawPlan, new BigDecimal(this.deltaExecution), new BigDecimal(deltaValidation),
                    "/tmp/temp_trace.pddl");
            if (valid) {
                System.out.println("Plan is valid");
            } else {
                System.out.println("Plan is not valid");
            }
        }
        printInfo(rawPlan, searchEngine);
        return rawPlan;
    }

    private void printInfo(LinkedList<Pair<BigDecimal, Object>> sp, PDDLSearchEngine searchEngine)
            throws CloneNotSupportedException {

        PDDLState s = (PDDLState) searchEngine.getLastState();
        if (pddlPlus && sp != null) {
        }
        if (sp != null) {
            System.out.println("Problem Solved\n");
            System.out.println("Found Plan:");
            printPlan(sp, pddlPlus, s, savePlan);
            System.out.println("\nPlan-Length:" + sp.size());
            planLength = sp.size();
        } else {
            System.out.println("Problem unsolvable");
        }
        if (pddlPlus && sp != null) {
            System.out.println("Elapsed Time: " + s.time);
        }
        System.out.println("Metric (Search):" + searchEngine.currentG);
        System.out.println("Planning Time (msec): " + overallPlanningTime);
        System.out.println("Heuristic Time (msec): " + searchEngine.getHeuristicCpuTime());
        System.out.println("Search Time (msec): " + searchEngine.getOverallSearchTime());
        System.out.println("Expanded Nodes:" + searchEngine.getNodesExpanded());
        System.out.println("States Evaluated:" + searchEngine.getNumberOfEvaluatedStates());
        System.out.println(
                "Fixed constraint violations during search (zero-crossing):" + searchEngine.constraintsViolations);
        System.out.println("Number of Dead-Ends detected:" + searchEngine.deadEndsDetected);
        System.out.println("Number of Duplicates detected:" + searchEngine.duplicatesNumber);
        //        if (searchEngine.getHeuristic() instanceof quasi_hm) {
        //            System.out.println("Number of LP invocations:" + ((quasi_hm) searchEngine.getHeuristic()).n_lp_invocations);
        //        }
        if (saving_json) {
            searchEngine.searchSpaceHandle.print_json(getProblem().getPddlFileReference() + ".sp_log");
        }
    }

    private void printPlan(LinkedList<Pair<BigDecimal, Object>> plan, boolean temporal, PDDLState par,
            String fileName) {
        float i = 0f;
        Pair<BigDecimal, Object> previous = null;
        List<String> fileContent = new ArrayList<String>();
        boolean startProcess = false;
        int size = plan.size();
        int j = 0;
        for (Pair<BigDecimal, Object> ele : plan) {
            j++;
            if (!temporal) {
                System.out.print(i + ": " + ele.getRight() + "\n");
                if (fileName != null) {
                    TransitionGround t = (TransitionGround) ele.getRight();
                    fileContent.add(t.toString());
                }
                i++;
            } else {
                TransitionGround t = (TransitionGround) ele.getRight();
                if (t.getSemantics() == TransitionGround.Semantics.PROCESS) {
                    if (!startProcess) {
                        previous = ele;
                        startProcess = true;
                    }
                    if (j == size) {
                        if (!onlyPlan) {
                            System.out.println(previous.getLeft() + ": -----waiting---- " + "[" + par.time + "]");
                        }
                    }
                } else {
                    if (t.getSemantics() != TransitionGround.Semantics.EVENT || printEvents) {
                        if (startProcess) {
                            startProcess = false;
                            if (!onlyPlan) {
                                System.out.println(
                                        previous.getLeft() + ": -----waiting---- " + "[" + ele.getLeft() + "]");
                            }
                        }
                        System.out.print(ele.getLeft() + ": " + ele.getRight() + "\n");
                        if (fileName != null) {
                            fileContent.add(ele.getLeft() + ": " + t.toString());
                        }
                    } else {
                        if (j == size) {
                            if (!onlyPlan) {
                                System.out.println(
                                        previous.getLeft() + ": -----waiting---- " + "[" + ele.getLeft() + "]");
                            }
                        }
                    }
                }
            }
        }

        if (fileName != null) {
            try {
                if (temporal) {
                    fileContent.add(par.time + ": @PlanEND ");
                }
                Files.write(Path.of(fileName), fileContent);

            } catch (IOException ex) {
                Logger.getLogger(ENHSP.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public class CafeHeuristic implements SearchHeuristic {

        @SuppressWarnings("unchecked")
        @Override
        public float computeEstimate(State state) {
            float totalHeuristicValue = 0;

            for (Map.Entry<String, Integer> entry : goals.entrySet()) {
                String enterConfiguration = null;
                String exitConfiguration = null;
                float heuristicValue = 0;
                String goalLink = entry.getKey();
                Integer goalValue = entry.getValue();
                String[] junctions = goalLink.split("_");
                String enterJunction = junctions[0];
                String exitJunction = junctions[2];
                
                // Parse the active configurations
                Integer found = 0;
                HashSet<BoolPredicate> boolFluents = PDDLProblem.booleanFluents;
                for (BoolPredicate boolFluent: boolFluents) {
                    if (found == 2)
                        break;
                    String predicateName = boolFluent.getPredicateName();
                    Boolean satisfied = boolFluent.isSatisfied(state);
                    List<ActionParameter> terms = boolFluent.getTerms();
                    
                    if (predicateName.equals("activeconf") && satisfied) {
                        String junction = terms.get(0).toString().split(" ")[0];
                        String configuration = terms.get(1).toString().split(" ")[0];

                        if (junction.equals(enterJunction)) {
                            enterConfiguration = configuration;
                            found += 1;
                            continue;
                        }

                        if (junction.equals(exitJunction)) {
                            exitConfiguration = configuration;
                            found += 1;
                            continue;
                        }
                    
                    }

                }

                // Parse current counter value of the link
                Double counter = 0.0;
                List<NumFluent> numFluents = problem.getNumFluents();
                for (NumFluent numFluent : numFluents) {
                    String name = numFluent.getName();
                    Double value = numFluent.eval(state);
                    List<ActionParameter> terms = numFluent.getTerms();

                    if (name.equals("counter")) {
                        String link = terms.get(0).toString().split(" ")[0];
                        if (link.equals(goalLink)) {
                            counter = value;
                            break;
                        }
                    }
                }
                
                // Calculate the expected vehicles entering the link
                Configuration configuration = configurations.get(enterConfiguration);
                Set<String> stagesId = configuration.getStagesId();
                String key = enterConfiguration+goalLink;
                Double expectedEnterCounter = 0.0;
                if (expectedEnter.containsKey(key)) {
                    expectedEnterCounter = expectedEnter.get(key);
                } else {
                    for (String stageId : stagesId) {
                        Stage stage = stages.get(stageId);
                        Integer totalGreentime = configuration.getGreentime(stageId);

                        List<Flow> flows = stage.getFlows();
                        // Flows that go to the goalLink
                        List<Flow> flowsToGoal = flows.stream().filter(flow -> flow.getTargetLink().equals(goalLink))
                                .collect(Collectors.toList());

                        for (Flow f : flowsToGoal) {

                            // Sum all flows values coming out of this sourceLink
                            double sumOfSource = flows.stream()
                                    .filter(flow -> flow.getSourceLink().equals(f.getSourceLink()))
                                    .mapToDouble(Flow::getValue)
                                    .sum();

                            // Get the value of the flow from this sourceLink to the goalLink
                            double valueToGoal = flows.stream()
                                    .filter(flow -> flow.getSourceLink().equals(f.getSourceLink()))
                                    .filter(flow -> flow.getTargetLink().equals(goalLink))
                                    .mapToDouble(Flow::getValue)
                                    .findFirst()
                                    .orElse(0.0);

                            double ratio = sumOfSource > 0 ? valueToGoal / sumOfSource : 0.0;
                            double result = 0.0;

                            if (feeders.containsKey(f.getSourceLink())) {
                                result = ratio * feeders.get(f.getSourceLink());
                            } else {
                                result = valueToGoal;
                            }

                            expectedEnterCounter += result * totalGreentime;
                        }
                    }
                    expectedEnter.put(key, expectedEnterCounter);
                }
                
                // Calculate the expected vehicles exiting the link
                Configuration configurationExit = configurations.get(exitConfiguration);
                stagesId = configurationExit.getStagesId();
                key = exitConfiguration+goalLink;
                Double expectedExitCounter = 0.0;
                if (expectedExit.containsKey(key)) {
                    expectedExitCounter = expectedExit.get(key);
                } else {
                    for (String stageId : stagesId) {
                        Stage stage = stages.get(stageId);
                        Integer totalGreentime = configurationExit.getGreentime(stageId);

                        List<Flow> flows = stage.getFlows();
                        
                        // Flows that exit from the goalLink
                        List<Flow> flowsFromGoal = flows.stream().filter(flow -> flow.getSourceLink().equals(goalLink))
                                .collect(Collectors.toList());

                        for (Flow f : flowsFromGoal) {
                            expectedExitCounter += f.getValue() * totalGreentime;
                        }

                    }
                    expectedExit.put(key, expectedExitCounter);
                }
                
                Double expectedCounter = Math.min(expectedEnterCounter, expectedExitCounter);
                heuristicValue = (float) Math.max(0, goalValue - counter - expectedCounter);
                totalHeuristicValue+=heuristicValue;
            }
            return totalHeuristicValue;
        }

        @Override
        public Collection<TransitionGround> getAllTransitions() {
            return problem.getTransitions();
        }

        @Override
        public Object[] getTransitions(boolean arg0) {
            Set<String> allowedNames = Set.of("changeConfiguration", "changeLimit");
        
            return problem.getTransitions().stream()
                    .filter(t -> allowedNames.contains(t.getName()))
                    .toArray(TransitionGround[]::new);
        }
        
    }

}