package edu.mit.util.stats;

import java.util.HashMap;

/* computes the digamma function using the CERN code, but memoizes results */
public class FastDigamma {
    private HashMap<Double,Double> cache; 
    public long num_hits;
    public long num_misses;
    public FastDigamma(){ 
        num_hits = 0; num_misses = 0;
        cache = new HashMap<Double,Double>();
    }
    public FastDigamma(int init_size, float load_factor){ 
        num_hits = 0; num_misses = 0;
        cache = new HashMap<Double,Double>(init_size, load_factor);
    }
    public synchronized double digamma(final double in){
        Double out = cache.get(in);
        if (out == null){
            double d_out = com.aliasi.util.Math.digamma(in);
            cache.put(in, d_out);
            num_misses++;
            return d_out;
        } else {
            num_hits++;
            return out;
        }
    }
}
