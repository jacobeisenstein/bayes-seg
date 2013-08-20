package edu.mit.util.stats;

import cern.jet.stat.Gamma;
import java.util.HashMap;

/* computes the gamma function using the CERN code, but memoizes results */
public class FastDoubleGamma implements FastGamma {
    private HashMap<Double,Double> log_gamma_cache; 
    private HashMap<Double,Double> gamma_cache;
    public long num_hits;
    public long num_misses;
    public FastDoubleGamma(){ 
        num_hits = 0; num_misses = 0;
        log_gamma_cache = new HashMap<Double,Double>();
        gamma_cache = new HashMap<Double,Double>();
    }
    public FastDoubleGamma(int init_size, float load_factor){ 
        num_hits = 0; num_misses = 0;
        log_gamma_cache = new HashMap<Double,Double>(init_size, load_factor);
        gamma_cache = new HashMap<Double,Double>(init_size, load_factor);
    }
    public synchronized double logGamma(final double in){
        Double out = log_gamma_cache.get(in);
        if (out == null){
            double d_out = Gamma.logGamma(in);
            log_gamma_cache.put(in, d_out);
            num_misses++;
            return d_out;
        } else {
            num_hits++;
            return out;
        }
    }
    public synchronized double gamma(final double in){
        Double out = gamma_cache.get(in);
        if (out == null){
            double d_out = Gamma.gamma(in);
            gamma_cache.put(in, d_out);
            return d_out;
        } else {
            return out;
        }
    }
}
