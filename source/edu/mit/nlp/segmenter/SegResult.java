package edu.mit.nlp.segmenter;

import java.io.PrintStream;

public class SegResult {
    public SegResult(int[] response, int[] key, double p_ll){
        ll = p_ll;
        // assert(response.length==key.length);
//         for (int i = 0; i < response.length; i++){
//             System.out.println(response[i] +" "+key[i]);
//         }
        pk = SegEval.pkEval(response,key);
        wd = SegEval.wdEval(response,key);
    }
    
    public SegResult(){}
    
    public static SegResult getAvgResult(SegResult[] results){
        return getAvgResult(results,-1);
    }

    //takes the average across all dudes except the test dude
    public static SegResult getAvgResult(SegResult[] results, int test_index){
        double total_pk = 0; double total_wd = 0; double total_ll = 0; int ctr = 0;
        for (int i = 0; i < results.length; i++){
            if (i != test_index){
                total_pk += results[i].pk; total_wd += results[i].wd; total_ll += results[i].ll;
                ctr++;
            }
        }
        SegResult result = new SegResult();
        result.ll = total_ll;
        result.pk = total_pk / ctr;
        result.wd = total_wd / ctr;
        return result;
    }

    public String toString(){ 
        return String.format("%.4f %.4f %.4f",pk,wd,ll);
    }

    double ll;
    double pk;
    double wd;
    public static void printResults(PrintStream out, SegResult[] results, double[] priors){
        //System.out.print("Results[");
//         for (int i = 0; i < priors.length; i++){
//             out.print(String.format("%.3f ",Math.exp(priors[i])));
//         }
//         out.print("\n");
        for (int i = 0; i < results.length; i++){
            out.println(results[i]);
        }
        out.println(getAvgResult(results));
    }
}
