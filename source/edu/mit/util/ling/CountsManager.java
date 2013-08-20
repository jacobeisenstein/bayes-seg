package edu.mit.util.ling;

public class CountsManager {
    public static double[] addToCounts(double[] counts, int[] obs, double weight){
        for (int i = 0; i < obs.length; i++) counts[obs[i]]+=weight; 
        return counts;
    }

    public static double[] addToCountsFirst(double[] counts, int[] obs, double weight){
        return addToCounts(counts,obs,weight,0,1);
    }

    public static double[] addToCountsRest(double[] counts, int[] obs, double weight){
        return addToCounts(counts,obs,weight,1,Integer.MAX_VALUE);
    }

    public static double[] addToCounts(double[] counts, int[] obs, double weight, int start, int end){
        for (int i = start; i < obs.length && i < end; i++){
            counts[obs[i]]+=weight;
        }
        return counts;
    }

    public static int[] addToCounts(int[] counts, int[] obs, int weight){
        for (int i = 0; i < obs.length; i++) counts[obs[i]]+=weight; 
        return counts;
    }

    public static int[] addToCountsFirst(int[] counts, int[] obs, int weight){
        return addToCounts(counts,obs,weight,0,1);
    }

    public static int[] addToCountsRest(int[] counts, int[] obs, int weight){
        return addToCounts(counts,obs,weight,1,Integer.MAX_VALUE);
    }

    public static int[] addToCounts(int[] counts, int[] obs, int weight, int start, int end){
        for (int i = start; i < obs.length && i < end; i++){
            counts[obs[i]]+=weight;
        }
        return counts;
    }


}
