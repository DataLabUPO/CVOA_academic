package core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;

import fitness.FitnessFunction;
import java.text.DecimalFormat;

public class CVOA implements Callable<Individual> {

    /**
     * Common resources for multi-threading execution.
     */
    // Lists shared by all concurrent strains
    public static volatile Set<Individual> recovered, deaths, isolated;
    // Best solution shared by all concurrent strains
    public static volatile Individual bestSolution;

    /**
     * Common experiment properties.
     */
    private static FitnessFunction fitnessFunction;
    private int size; // size stands for number of bits

    /**
     * Specific properties for each strain.
     */
    // Inputs
    private int max_time; // max_time stands for iterations
    private String strainID;
    private Random rnd;
    public static final DecimalFormat DF = new DecimalFormat("#.##");

    // Modify these values to simulate other pandemics
    private int MAX_SPREAD = 5;
    private int MIN_SUPERSPREAD = 6;
    private int MAX_SUPERSPREAD = 15;
    private int SOCIAL_DISTANCING = 10; // Iterations without social distancing
    private double P_ISOLATION = 0.7;
    private double P_TRAVEL = 0.1;
    private double P_REINFECTION = 0.001;
    private double SUPERSPREADER_PERC = 0.1;
    private double DEATH_PERC = 0.15;

    // Auxiliary properties
    private int time;
    private int stoppingIteration = -1;
    private Set<Individual> infectedStrain;
    private Set<Individual> deathStrain;
    private Set<Individual> superSpreaderStrain;
    private Individual bestSolutionStrain;
    private Individual bestDeadIndividualStrain;
    private Individual worstSuperSpreaderIndividualStrain;

    /**
     * Constructors.
     */
    public CVOA(int size, int max_time, String id, int seed, int minSpread, int maxSpread, int minSuperSpread,
            int maxSuperSpread, double pTravel, double pInfection, double superSpreaderPerc, double deathPerc,
            double pIsolation, int socialDistancing) {

        initializeCommon(size, max_time, id, seed);

        this.MAX_SPREAD = maxSpread;
        this.MIN_SUPERSPREAD = minSuperSpread;
        this.MAX_SUPERSPREAD = maxSuperSpread;
        this.P_TRAVEL = pTravel;
        this.P_REINFECTION = pInfection;
        this.SUPERSPREADER_PERC = superSpreaderPerc;
        this.DEATH_PERC = deathPerc;
        this.P_ISOLATION = pIsolation;
        this.SOCIAL_DISTANCING = socialDistancing;
    }

    public CVOA(int size, int max_time, String id, int seed) {
        initializeCommon(size, max_time, id, seed);
    }

    private void initializeCommon(int size, int max_time, String id, int seed) {

        this.size = size;
        this.rnd = new Random(System.currentTimeMillis() + seed);
        this.max_time = max_time;
        this.strainID = id;

        this.infectedStrain = new HashSet<Individual>();
        this.superSpreaderStrain = new HashSet<Individual>();
        this.deathStrain = new HashSet<Individual>();

    }

    public static void initializePandemic(Individual best, FitnessFunction function) {
        bestSolution = best;
        fitnessFunction = function;
        deaths = Collections.synchronizedSet(new HashSet<Individual>());
        recovered = Collections.synchronizedSet(new HashSet<Individual>());
        isolated = Collections.synchronizedSet(new HashSet<Individual>());
    }

    /**
     * Multithreading method.
     */
    @Override
    public Individual call() throws Exception {
        Individual res = this.run();
        return res;
    }

    /**
     * Algorithm operators.
     */
    /**
     * Run CVOA.
     */
    public Individual run() {

        Individual pz;
        boolean epidemic = true;

        // Step 1. Infect patient zero (PZ)
        pz = infectPZ();

        // Step 2. Initialize strain: infected and best solution.
        infectedStrain.add(pz);
        bestSolutionStrain = pz;
        superSpreaderStrain.add(pz);
        worstSuperSpreaderIndividualStrain = Individual.getExtremeIndividual(true);

        bestDeadIndividualStrain = Individual.getExtremeIndividual(false);

        System.out.println("Patient Zero (" + strainID + "): " + pz);

        // Step 3. The main loop for the disease propagation
        time = 0;

        while (epidemic && time < max_time) {

            // (Un)comment this line to hide/show intermediate information
            //System.out.print("\n[" + strainID + "] - Iteration #" + time);
            //System.out.print("\n\tBest global = " + bestSolution);
            //System.out.print(", #Recovered = " + recovered.size());
            //System.out.print(", #Deaths=" + deaths.size());
            //System.out.print(" - Best local = " + bestSolutionStrain);
            //System.out.print("\n\t#NewInfected = " + infectedStrain.size());
            //System.out.print(", Str#Super=" + superSpreaderStrain.size());
            //System.out.print(" - #Deaths = " + deathStrain.size() + "\n");
            propagateDisease();

            if (infectedStrain.isEmpty()) {
                epidemic = false;
            }

            if (bestSolutionStrain.getFitness() == 0.0) {
                bestSolutionStrain.setDiscoveringIteration(time);
            }

            time++;

        }

        System.out.println("\n\n" + strainID + " converged after " + time + " iterations.");
        System.out.println("Best individual = " + bestSolutionStrain);

        return bestSolutionStrain;
    }

    public int getLastIteration() {
        return stoppingIteration;
    }

    private void propagateDisease() {

        int infectedStrainSize = infectedStrain.size();

        // New infected people will be stored here (from infectedStrain)
        Set<Individual> newInfectedPopulation = new HashSet<Individual>();

        // Update super spreader and deaths strain sets
        int numberOfSuperSpreaders = 1;
        int numberOfDeaths = 0;

        if (infectedStrainSize != 1) {

            numberOfSuperSpreaders = (int) Math.ceil(SUPERSPREADER_PERC * infectedStrainSize);
            numberOfDeaths = (int) Math.ceil(DEATH_PERC * infectedStrainSize);

            for (Individual individual : infectedStrain) {

                if (insertIntoSetStrain(superSpreaderStrain, individual, numberOfSuperSpreaders, 's')) {
                    numberOfSuperSpreaders--;
                }
                if (updateRecoverdDeathStrain(deathStrain, individual, numberOfDeaths)) {
                    numberOfDeaths--;
                }
                if (bestSolution.compareTo(individual) == 1) {
                    bestSolution = individual;
                }
                if (bestSolutionStrain.compareTo(individual) == 1) {
                    bestSolutionStrain = individual;
                }
            }
            deaths.addAll(deathStrain);
            bestSolutionStrain.setDiscoveringIteration(time + 1);
        }
        recovered.removeAll(deaths);

        // Each indvidual infects new ones and add them to newInfectedPopulation 
        for (Individual x : infectedStrain) {
            //Calculation of number of new infected and whether they travel or not
            //1. Determine the number of new individuals depending of SuperSpreader or Common
            int ninfected = 0;
            if (superSpreaderStrain.contains(x)) {
                ninfected = MIN_SUPERSPREAD + rnd.nextInt(MAX_SUPERSPREAD - MIN_SUPERSPREAD + 1);
            } else {
                ninfected = rnd.nextInt(MAX_SPREAD + 1);
            }

            //2. Determine the travel distance, which is how far is the new infected individual (number of bits mutating)
            int travel_distance = 1;
            if (rnd.nextDouble() < P_TRAVEL) {
                travel_distance = rnd.nextInt(size + 1);
            }

            // Every individual infects as many times as indicated by ninfected
            for (int j = 0; j < ninfected; j++) {
                Individual z = infect(x, travel_distance);

                // Propagate with no social distancing measures
                if (time < SOCIAL_DISTANCING) {

                    // PUEDE SER UNA FUNCION updatePopulations
                    if (!deaths.contains(z) && !recovered.contains(z)) {
                        newInfectedPopulation.add(z); // Add new infected individual
                    } else if (recovered.contains(z)) {
                        if (rnd.nextDouble() < P_REINFECTION) { // Chance of reinfection
                            newInfectedPopulation.add(z);
                            recovered.remove(z);
                        }
                    }
                } // After SOCIAL_DISTANCING iterations, there is a P_ISOLATION of not being infected 
                else {
                    if (rnd.nextDouble() > P_ISOLATION) {

                        // PUEDE SER UNA FUNCION updatePopulations
                        if (!deaths.contains(z) && !recovered.contains(z)) {
                            newInfectedPopulation.add(z); // Add new infected individual
                        } else if (recovered.contains(z)) {
                            if (rnd.nextDouble() < P_REINFECTION) { // Chance of reinfection
                                newInfectedPopulation.add(z);
                                recovered.remove(z);
                            }
                        }
                    } else {
                        if (!deaths.contains(z) && !recovered.contains(z) && !isolated.contains(z)) {
                            isolated.add(z);// People not infected thanks to isolation measures
                        }
                    }
                }
            }
        }
// (Un)comment this line to hide/show intermediate information
/*    
        System.out.print("\n[" + strainID + "] - Iteration #" + time);
        System.out.print("\n\tBest global fitness = " + bestSolution);
        //System.out.print(", #Recovered = " + recovered.size());
        //System.out.print(", #Deaths=" + deaths.size());
        System.out.print("\n\tBest strain fitness = " + bestSolutionStrain);
        System.out.print("\n\t#NewInfected = " + newInfectedPopulation.size());
        //System.out.print(", Str#Super=" + superSpreaderStrain.size());
        //System.out.print(" - #Deaths = " + deathStrain.size() + "\n");
        System.out.print("\n\tR0 = " + DF.format((double) newInfectedPopulation.size() / infectedStrain.size()) + "\n");
*/
        // Just one println to ensure it is printed without interfering with other threads
        System.out.print("\n[" + strainID + "] - Iteration #" + time+"\n\tBest global fitness = " + bestSolution+"\n\tBest strain fitness = " + bestSolutionStrain+"\n\t#NewInfected = " + newInfectedPopulation.size()+"\n\tR0 = " + DF.format((double) newInfectedPopulation.size() / infectedStrain.size()) + "\n");
        
        infectedStrain.clear();
        infectedStrain.addAll(newInfectedPopulation);

    }

    // This method could be improved if a wiser selection of PZ is done
    // It could be selected orthogonal PZs or PZs with high Hamming distance
    // Another strategy can consist in creating PZs evenly spaced (and their complement to 1 values)
    private Individual infectPZ() {
        Individual pz = null;

        int[] data = new int[size];

        for (int i = 0; i < size; i++) {
            data[i] = rnd.nextInt(2);
        }

        pz = buildIndividual(data);

        return pz;
    }

    // Infect a new individual by mutating as many bits as indicated by travel_distance
    private Individual infect(Individual individual, int travel_distance) {
        List<Integer> mutated = new LinkedList<Integer>();
        int[] res = Arrays.copyOf(individual.getData(), size);
        int i = 0, pos;

        while (i < travel_distance) {
            pos = rnd.nextInt(size);
            if (!mutated.contains(pos)) {
                res[pos] = res[pos] == 0 ? 1 : 0;
                mutated.add(pos);
                i++;
            }
        }

        return buildIndividual(res);
    }

    // Supporting methods
    private Individual buildIndividual(int[] data) {

        Individual res = new Individual();
        res.setData(data);
        res.setFitness(fitnessFunction.fitness(res));
        return res;

    }

    private boolean updateRecoverdDeathStrain(Set<Individual> bag, Individual toInsert, int remaining) {

        boolean dead = false;

        dead = insertIntoSetStrain(bag, toInsert, remaining, 'd');

        if (!dead) {
            if (!deaths.contains(toInsert)) {
                recovered.add(toInsert);
            }
        }

        return dead;

    }

    private boolean insertIntoSetStrain(Set<Individual> bag, Individual toInsert, int remaining, char type) {

        boolean r = false;
        Object[] aux = getCompareToValueAndElement(type);
        int compareToValue = ((Integer) aux[0]).intValue();
        Individual border = ((Individual) aux[1]);

        if (remaining > 0) {
            r = bag.add(toInsert);

            if (toInsert.compareTo(border) == compareToValue) {
                updateBorder(toInsert, type);
            }
        } else {

            if (toInsert.compareTo(border) == compareToValue) {
                bag.remove(border);
                r = bag.add(toInsert);
                updateBorder(toInsert, type);
            }
        }

        return r;
    }

    private Object[] getCompareToValueAndElement(char type) {

        Object[] r = new Object[2];
        if (type == 's') {// Super Spreader bag
            r[0] = new Integer(1);
            r[1] = worstSuperSpreaderIndividualStrain;
        }
        if (type == 'd') {// Death Strain bag

            r[0] = new Integer(-1);
            r[1] = bestDeadIndividualStrain;
        }

        return r;
    }

    private void updateBorder(Individual toUpdate, char type) {

        if (type == 's') // Super-spreader Strain bag
        {
            worstSuperSpreaderIndividualStrain = toUpdate;
        } else if (type == 'd') // Deaths strain bag
        {
            bestDeadIndividualStrain = toUpdate;
        }
    }

}
